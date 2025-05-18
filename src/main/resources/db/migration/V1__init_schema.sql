-- Users table
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chats table
CREATE TABLE chats (
                       id BIGSERIAL PRIMARY KEY,
                       user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       title VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       is_archived BOOLEAN NOT NULL DEFAULT FALSE
);

-- Messages table
CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                          content TEXT NOT NULL,
                          is_from_user BOOLEAN NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Database connections table
CREATE TABLE database_connections (
                                      id BIGSERIAL PRIMARY KEY,
                                      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                      chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                                      name VARCHAR(100) NOT NULL,
                                      db_type VARCHAR(50) NOT NULL,
                                      host VARCHAR(255) NOT NULL,
                                      port INT NOT NULL,
                                      database_name VARCHAR(100) NOT NULL,
                                      username VARCHAR(100) NOT NULL,
                                      password VARCHAR(255) NOT NULL,
                                      is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      last_connected_at TIMESTAMP,
                                      UNIQUE (chat_id, name)
);

-- SQL queries table
CREATE TABLE sql_queries (
                             id BIGSERIAL PRIMARY KEY,
                             message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                             original_query TEXT NOT NULL,
                             optimized_query TEXT,
                             database_connection_id BIGINT REFERENCES database_connections(id),
                             execution_time_ms BIGINT,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_chats_user_id ON chats(user_id);
CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_database_connections_user_id ON database_connections(user_id);
CREATE INDEX idx_database_connections_chat_id ON database_connections(chat_id);
CREATE INDEX idx_sql_queries_message_id ON sql_queries(message_id);
