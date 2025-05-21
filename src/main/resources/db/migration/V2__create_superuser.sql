-- Создание суперпользователя с зашифрованным паролем (пароль: postgres)
INSERT INTO users (username, email, password, created_at, updated_at)
VALUES (
    'postgres',
    'postgres@example.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- BCrypt хеш для 'postgres'
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (username) DO NOTHING; 