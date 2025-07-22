# Currency Wallet Service

A comprehensive async currency wallet micro-service built with Spring Boot 3, Java 21, Kafka, Redis, and PostgreSQL. This service manages user balances in USD and TRY currencies with asynchronous transaction processing, live FX rates, and rate limiting.

## Features

- **JWT Authentication**: Secure token-based authentication with Spring Security
- **Multi-Currency Support**: USD and TRY wallet management
- **Async Processing**: Kafka-based transaction processing pipeline
- **FX Rate Caching**: Redis-cached live exchange rates with 60s TTL
- **Rate Limiting**: IP-based rate limiting (20 write operations/minute)
- **Idempotency**: X-Idempotency-Key header support for duplicate prevention
- **Monitoring**: Spring Actuator with health checks and metrics
- **API Documentation**: Swagger/OpenAPI 3 integration
- **Containerized**: Full Docker Compose stack

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Security | Spring Security + JWT |
| Build Tool | Gradle 8.5 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Messaging | Apache Kafka |
| Documentation | Springdoc OpenAPI |
| Containerization | Docker & Docker Compose |

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Gradle 8.x (for local development)

### One-Command Startup

```bash
# Clone and start the entire stack
git clone <repository-url>
cd currency-wallet-service
docker compose up
```

The service will be available at:
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **Database**: PostgreSQL on localhost:5432
- **Redis**: localhost:6379
- **Kafka**: localhost:9092

## Authentication

The service uses JWT (JSON Web Token) authentication. All transaction endpoints require authentication except for user registration and login.

### Test Users

Two test users are available for immediate testing:
- **Email**: `john.doe@example.com`, **Password**: `password123`
- **Email**: `jane.smith@example.com`, **Password**: `password123`

### Authentication Flow

1. **Register a new user** (optional - test users already exist)
2. **Login** to get a JWT token
3. **Include the token** in Authorization header for protected endpoints

### Example Authentication

```bash
# 1. Login to get JWT token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "password123"
  }'

# Response:
# {
#   "token": "eyJhbGciOiJIUzUxMiJ9...",
#   "type": "Bearer",
#   "userId": 1,
#   "email": "john.doe@example.com",
#   "name": "John Doe"
# }

# 2. Use token in subsequent requests
export JWT_TOKEN="eyJhbGciOiJIUzUxMiJ9..."
curl -H "Authorization: Bearer $JWT_TOKEN" \
     http://localhost:8080/transactions/balance/1
```

## API Endpoints

### Transaction Operations (🔒 Authentication Required)

#### Create Deposit
```bash
curl -X POST http://localhost:8080/transactions/deposit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-Idempotency-Key: unique-key-123" \
  -d '{
    "userId": 1,
    "currency": "USD",
    "amount": 100.50,
    "description": "Salary deposit"
  }'
```

#### Create Withdrawal
```bash
curl -X POST http://localhost:8080/transactions/withdraw \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-Idempotency-Key: unique-key-456" \
  -d '{
    "userId": 1,
    "currency": "USD",
    "amount": 50.00,
    "description": "ATM withdrawal"
  }'
```

#### Currency Exchange
```bash
curl -X POST http://localhost:8080/transactions/exchange \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-Idempotency-Key: unique-key-789" \
  -d '{
    "userId": 1,
    "fromCurrency": "USD",
    "toCurrency": "TRY",
    "amount": 100.00,
    "description": "Travel exchange"
  }'
```

### Query Operations (🔒 Authentication Required)

#### Get User Balances
```bash
curl -H "Authorization: Bearer $JWT_TOKEN" \
     http://localhost:8080/transactions/balance/1
```

#### Get Transaction Status
```bash
curl -H "Authorization: Bearer $JWT_TOKEN" \
     http://localhost:8080/transactions/status/1
```

#### Get Transaction History
```bash
curl -H "Authorization: Bearer $JWT_TOKEN" \
     http://localhost:8080/transactions/history/1
```

### Authentication & User Management

#### User Login (Public)
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "password123"
  }'
```

#### Create User (Public - Registration)
```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@example.com",
    "password": "securePassword123"
  }'
```

#### Get User by ID (🔒 Authentication Required)
```bash
curl -H "Authorization: Bearer $JWT_TOKEN" \
     http://localhost:8080/users/1
```

#### Get User by Email (🔒 Authentication Required)
```bash
curl -H "Authorization: Bearer $JWT_TOKEN" \
     http://localhost:8080/users/email/john.doe@example.com
```

## Architecture Overview

### Async Transaction Flow

1. **API Request** → Transaction created with PENDING status
2. **Kafka Producer** → Message sent to `wallet.txn` topic
3. **Kafka Consumer** → Processes transaction asynchronously
4. **Database Update** → Account balances updated with optimistic locking
5. **Status Update** → Transaction marked as COMPLETED/FAILED

### Rate Limiting

- **Scope**: Write operations (POST, PUT, DELETE)
- **Limit**: 20 requests per minute per IP address
- **Implementation**: Bucket4j with in-memory token buckets
- **Headers**: `X-Rate-Limit-Remaining`, `X-Rate-Limit-Retry-After-Seconds`

### FX Rate Caching

- **Provider**: External API (configurable)
- **Cache**: Redis with 60-second TTL
- **Fallback**: Mock rates for USD↔TRY (1 USD = 33.25 TRY)
- **Error Handling**: Returns 503 if FX service unavailable and no cached rate

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS users (
                                    id BIGSERIAL PRIMARY KEY,
                                    name VARCHAR(100) NOT NULL,
   email VARCHAR(100) UNIQUE NOT NULL,
   password VARCHAR(255) NOT NULL,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

CREATE TABLE IF NOT EXISTS accounts (
                                       id BIGSERIAL PRIMARY KEY,
                                       user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   currency VARCHAR(3) NOT NULL,
   balance DECIMAL(18,6) NOT NULL DEFAULT 0,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   UNIQUE(user_id, currency)
   );

CREATE TABLE IF NOT EXISTS transactions (
                                           id BIGSERIAL PRIMARY KEY,
                                           user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   type VARCHAR(20) NOT NULL,
   currency VARCHAR(3) NOT NULL,
   amount DECIMAL(18,6) NOT NULL,
   status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
   idempotency_key VARCHAR(100) UNIQUE,
   external_reference VARCHAR(100),
   description TEXT,
   error_message TEXT,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   processed_at TIMESTAMP
   );
```

## Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
wallet:
  fx:
    api:
      url: https://api.exchangerate-api.com/v4/latest/USD
      cache-ttl: 60s
  kafka:
    topics:
      transactions: wallet.txn
  rate-limiting:
    enabled: true
    capacity: 20
    refill-rate: 20
    refill-period: 1m
  jwt:
    secret: mySecretKey1234567890abcdefghijklmnopqrstuvwxyz
    expiration-hours: 24
```

### Environment Variables

For Docker deployment:

```bash
SPRING_PROFILES_ACTIVE=docker
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/walletdb
SPRING_DATASOURCE_USERNAME=wallet_user
SPRING_DATASOURCE_PASSWORD=wallet_pass
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PASSWORD=redispass
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

## Development

### Local Development Setup

1. **Start Dependencies**:
   ```bash
   # Start only the infrastructure services
   docker compose up db redis kafka zookeeper -d
   ```

2. **Run Application**:
   ```bash
   ./gradlew bootRun
   ```

3. **Run Tests**:
   ```bash
   ./gradlew test
   ```

4. **Generate Test Coverage**:
   ```bash
   ./gradlew jacocoTestReport
   # Report available at build/jacocoHtml/index.html
   ```

### Building

```bash
# Build JAR
./gradlew bootJar

# Build Docker image
docker build -t currency-wallet-service .

# Run complete test suite with integration tests
./gradlew test integrationTest
```

## Monitoring & Observability

### Health Checks

- **Application**: http://localhost:8080/actuator/health
- **Detailed**: http://localhost:8080/actuator/health?showDetails=true

### Metrics

- **Prometheus**: http://localhost:8080/actuator/prometheus
- **General Metrics**: http://localhost:8080/actuator/metrics

### Logging

Structured logging with different levels:
- **DEBUG**: Transaction processing details
- **INFO**: API requests and business operations
- **WARN**: Rate limiting violations, FX cache misses
- **ERROR**: Transaction failures, system errors

## Security Considerations

- **JWT Authentication**: Stateless token-based authentication with Spring Security
- **Password Security**: BCrypt encryption for password hashing
- **Input Validation**: Bean Validation with custom constraints
- **SQL Injection**: JPA/Hibernate with parameterized queries
- **Rate Limiting**: Prevents API abuse
- **Container Security**: Non-root user in Docker
- **Secrets Management**: Environment variables (externalize in production)
- **Token Expiration**: JWT tokens expire after 24 hours (configurable)

## Design Decisions

### Async Processing
- **Why**: Improves API response times and system resilience
- **Trade-off**: Eventual consistency vs immediate feedback
- **Mitigation**: Status endpoint for transaction tracking

### Optimistic Locking
- **Why**: Better performance than pessimistic locking
- **Trade-off**: Retry logic needed for concurrent updates
- **Implementation**: JPA @Version annotation

### Redis Caching
- **Why**: Reduces external API calls and improves performance
- **TTL**: 60 seconds balances freshness vs performance
- **Fallback**: Mock rates ensure service availability

### Decimal Precision
- **Why**: Financial applications require exact decimal arithmetic
- **Implementation**: BigDecimal with 6 decimal places
- **Storage**: DECIMAL(18,6) in database

## Production Considerations

### Scaling
- **Horizontal**: Multiple application instances behind load balancer
- **Database**: Read replicas, connection pooling
- **Kafka**: Multiple partitions for parallel processing
- **Redis**: Redis Cluster for high availability

### Monitoring
- **APM**: Integration with tools like New Relic, DataDog
- **Alerting**: Transaction failure rates, FX service health
- **Dashboards**: Business metrics (transaction volumes, balances)

### Security Enhancements
- **HTTPS**: TLS termination at load balancer
- **Secrets**: HashiCorp Vault or AWS Secrets Manager
- **Rate Limiting**: Redis-based distributed rate limiting
- **Role-based Access**: Extend JWT with user roles and permissions

## Troubleshooting

### Common Issues

1. **Kafka Connection Errors**:
   ```bash
   # Check Kafka health
   docker compose logs kafka
   ```

2. **Database Connection Issues**:
   ```bash
   # Verify PostgreSQL
   docker compose exec db pg_isready -U wallet_user
   ```

3. **Redis Connection Problems**:
   ```bash
   # Test Redis connectivity
   docker compose exec redis redis-cli -a redispass ping
   ```

4. **Rate Limiting Too Aggressive**:
   ```yaml
   # Adjust in application.yml
   wallet.rate-limiting.capacity: 50
   ```
