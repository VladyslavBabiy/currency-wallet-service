-- Create tables if they don't exist
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

-- Create sample users for testing (password is "password123" for both users)
INSERT INTO users (name, email, password, created_at, updated_at) VALUES 
('John Doe', 'john.doe@example.com', '$2a$10$ZhGS.zcWt1g6eFHaOVVUOuQi6G6R5XJFfUzKzFzJZFJ5H9Q5h1XhS', NOW(), NOW()),
('Jane Smith', 'jane.smith@example.com', '$2a$10$ZhGS.zcWt1g6eFHaOVVUOuQi6G6R5XJFfUzKzFzJZFJ5H9Q5h1XhS', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Create sample accounts
INSERT INTO accounts (user_id, currency, balance, created_at, updated_at) VALUES 
((SELECT id FROM users WHERE email = 'john.doe@example.com'), 'USD', 1000.000000, NOW(), NOW()),
((SELECT id FROM users WHERE email = 'john.doe@example.com'), 'TRY', 5000.000000, NOW(), NOW()),
((SELECT id FROM users WHERE email = 'jane.smith@example.com'), 'USD', 2000.000000, NOW(), NOW()),
((SELECT id FROM users WHERE email = 'jane.smith@example.com'), 'TRY', 10000.000000, NOW(), NOW())
ON CONFLICT (user_id, currency) DO NOTHING; 