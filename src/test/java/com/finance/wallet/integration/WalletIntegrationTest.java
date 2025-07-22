package com.finance.wallet.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.wallet.WalletServiceApplication;
import com.finance.wallet.config.TestContainersConfiguration;
import com.finance.wallet.config.TestKafkaConfig;
import com.finance.wallet.dto.CreateUserRequest;
import com.finance.wallet.dto.DepositRequest;
import com.finance.wallet.dto.WithdrawalRequest;
import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.Transaction;
import com.finance.wallet.entity.User;
import com.finance.wallet.repository.AccountRepository;
import com.finance.wallet.repository.TransactionRepository;
import com.finance.wallet.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "wallet.kafka.topics.transactions=test.wallet.txn",
                "wallet.fx.cache-ttl=1s",
                "wallet.rate-limiting.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.kafka.consumer.auto-startup=false",
                "spring.jackson.serialization.write-dates-as-timestamps=false",
                "spring.jackson.deserialization.fail-on-unknown-properties=false"
        },
        classes = {WalletServiceApplication.class, TestKafkaConfig.class})
@Testcontainers
@Import({TestContainersConfiguration.class, com.finance.wallet.config.TestJacksonConfig.class, com.finance.wallet.config.TestSecurityConfig.class})
class WalletIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("walletdb_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database properties
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // JPA properties
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Redis properties
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Fix Kafka consumer deserialization issues for tests
        registry.add("spring.kafka.consumer.properties.spring.json.use.type.headers", () -> "false");
        registry.add("spring.kafka.consumer.properties.spring.json.value.default.type", () -> "com.finance.wallet.dto.TransactionResponse");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "latest");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @Transactional
    void createUserFlow_EndToEnd_Success() throws Exception {
        CreateUserRequest createUserRequest = new CreateUserRequest("John Doe", "john.doe.create@example.com", "password123@");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john.doe.create@example.com"))
                .andExpect(jsonPath("$.id").exists());

        Optional<User> savedUser = userRepository.findByEmail("john.doe.create@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getName()).isEqualTo("John Doe");
    }

    @Test
    @Transactional
    void depositFlow_EndToEnd_Success() throws Exception {
        User user = User.builder()
                .name("John Doe")
                .email("john.doe.deposit@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
        User savedUser = userRepository.save(user);

        DepositRequest depositRequest = new DepositRequest(
                savedUser.getId(),
                Account.Currency.USD,
                new BigDecimal("100.00"),
                "Test deposit"
        );

        mockMvc.perform(post("/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "test-deposit-123")
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("PENDING"));

        List<Transaction> transactions = transactionRepository.findByUserId(savedUser.getId());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(Transaction.TransactionType.DEPOSIT);
    }

    @Test
    @Transactional
    void withdrawalFlow_EndToEnd_Success() throws Exception {
        User user = User.builder()
                .name("John Doe")
                .email("john.doe.withdrawal@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
        User savedUser = userRepository.save(user);

        Account account = Account.builder()
                .user(savedUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("500.00"))
                .build();
        accountRepository.save(account);

        WithdrawalRequest withdrawalRequest = new WithdrawalRequest(
                savedUser.getId(),
                Account.Currency.USD,
                new BigDecimal("100.00"),
                "Test withdrawal"
        );

        mockMvc.perform(post("/transactions/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "test-withdrawal-123")
                        .content(objectMapper.writeValueAsString(withdrawalRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(100.00));

        List<Transaction> transactions = transactionRepository.findByUserId(savedUser.getId());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getType()).isEqualTo(Transaction.TransactionType.WITHDRAWAL);
    }

    @Test
    @Transactional
    void withdrawalFlow_InsufficientBalance_Failed() throws Exception {
        User user = User.builder()
                .name("John Doe")
                .email("john.doe.insufficient@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
        User savedUser = userRepository.save(user);

        Account account = Account.builder()
                .user(savedUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("50.00"))
                .build();
        accountRepository.save(account);

        WithdrawalRequest withdrawalRequest = new WithdrawalRequest(
                savedUser.getId(),
                Account.Currency.USD,
                new BigDecimal("100.00"),
                "Test withdrawal"
        );

        mockMvc.perform(post("/transactions/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawalRequest)))
                .andExpect(status().isAccepted());

        List<Transaction> transactions = transactionRepository.findByUserId(savedUser.getId());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getType()).isEqualTo(Transaction.TransactionType.WITHDRAWAL);
    }

    @Test
    void idempotencyTest_DuplicateRequests_ReturnsSameResponse() throws Exception {
        User user = User.builder()
                .name("John Doe")
                .email("john.doe.idempotency@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
        User savedUser = userRepository.save(user);

        DepositRequest depositRequest = new DepositRequest(
                savedUser.getId(),
                Account.Currency.USD,
                new BigDecimal("100.00"),
                "Test deposit"
        );

        String idempotencyKey = "idempotency-test-123";

        String response1 = mockMvc.perform(post("/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String response2 = mockMvc.perform(post("/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        JsonNode json1 = objectMapper.readTree(response1);
        JsonNode json2 = objectMapper.readTree(response2);
        assertThat(json1).isEqualTo(json2);

        List<Transaction> transactions = transactionRepository.findByUserId(savedUser.getId());
        assertThat(transactions).hasSize(1);
    }

    @Test
    void redisIntegration_CachesFxRates() {
        String cacheKey = "fx_rate:USD_TRY";
        redisTemplate.opsForValue().set(cacheKey, "33.25", Duration.ofMinutes(1));
        String cachedRate = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedRate).isEqualTo("33.25");
        Boolean hasKey = redisTemplate.hasKey(cacheKey);
        assertThat(hasKey).isTrue();
    }

    @Test
    void transactionHistory_ReturnsAllUserTransactions() throws Exception {
        User user = User.builder()
                .name("John Doe")
                .email("john.doe.history@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
        User savedUser = userRepository.save(user);
        Transaction tx1 = Transaction.builder()
                .user(savedUser)
                .type(Transaction.TransactionType.DEPOSIT)
                .currency(Account.Currency.USD)
                .amount(new BigDecimal("100.00"))
                .status(Transaction.TransactionStatus.COMPLETED)
                .description("First deposit")
                .build();

        Transaction tx2 = Transaction.builder()
                .user(savedUser)
                .type(Transaction.TransactionType.WITHDRAWAL)
                .currency(Account.Currency.USD)
                .amount(new BigDecimal("50.00"))
                .status(Transaction.TransactionStatus.COMPLETED)
                .description("First withdrawal")
                .build();

        transactionRepository.saveAll(List.of(tx1, tx2));

        mockMvc.perform(get("/transactions/history/" + savedUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].type").value(containsInAnyOrder("DEPOSIT", "WITHDRAWAL")));
    }
} 