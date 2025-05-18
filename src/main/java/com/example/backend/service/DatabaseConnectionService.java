package com.example.backend.service;

import com.example.backend.exception.DatabaseConnectionException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.DatabaseConnectionDto;
import com.example.backend.model.entity.Chat;
import com.example.backend.model.entity.DatabaseConnection;
import com.example.backend.model.entity.User;
import com.example.backend.repository.ChatRepository;
import com.example.backend.repository.DatabaseConnectionRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseConnectionService {

    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;

    // Cache for active connections
    private final Map<Long, Connection> activeConnections = new HashMap<>();

    public List<DatabaseConnectionDto> getConnectionsForChat(Long chatId) {
        List<DatabaseConnection> connections = databaseConnectionRepository.findByChatIdAndActiveTrue(chatId);
        return connections.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public DatabaseConnectionDto createConnection(Long userId, DatabaseConnectionDto connectionDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Chat chat = chatRepository.findById(connectionDto.getChatId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        // Test connection before saving
        testConnection(connectionDto);

        DatabaseConnection connection = DatabaseConnection.builder()
                .user(user)
                .chat(chat)
                .name(connectionDto.getName())
                .dbType(connectionDto.getDbType())
                .host(connectionDto.getHost())
                .port(connectionDto.getPort())
                .databaseName(connectionDto.getDatabaseName())
                .username(connectionDto.getUsername())
                .password(connectionDto.getPassword()) // In a real app, this should be encrypted
                .active(true)
                .build();

        DatabaseConnection savedConnection = databaseConnectionRepository.save(connection);
        return mapToDto(savedConnection);
    }

    public boolean testConnection(DatabaseConnectionDto connectionDto) {
        String url = buildJdbcUrl(connectionDto);

        try (Connection connection = DriverManager.getConnection(
                url, connectionDto.getUsername(), connectionDto.getPassword())) {
            return connection.isValid(5);
        } catch (SQLException e) {
            log.error("Failed to connect to database: {}", e.getMessage());
            throw new DatabaseConnectionException("Failed to connect to database: " + e.getMessage());
        }
    }

    public Connection getConnection(Long connectionId) {
        // Check if connection is already in cache
        if (activeConnections.containsKey(connectionId)) {
            try {
                Connection connection = activeConnections.get(connectionId);
                if (connection != null && !connection.isClosed() && connection.isValid(1)) {
                    return connection;
                }
            } catch (SQLException e) {
                // Connection is invalid, remove from cache
                closeConnection(connectionId);
            }
        }

        // Get connection details from database
        DatabaseConnection dbConnection = databaseConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Database connection not found"));

        if (!dbConnection.isActive()) {
            throw new DatabaseConnectionException("Connection is not active");
        }

        // Create new connection
        String url = buildJdbcUrl(dbConnection);

        try {
            Connection connection = DriverManager.getConnection(
                    url, dbConnection.getUsername(), dbConnection.getPassword());

            // Update last connected timestamp
            dbConnection.setLastConnectedAt(LocalDateTime.now());
            databaseConnectionRepository.save(dbConnection);

            // Cache the connection
            activeConnections.put(connectionId, connection);

            return connection;
        } catch (SQLException e) {
            log.error("Failed to connect to database: {}", e.getMessage());
            throw new DatabaseConnectionException("Failed to connect to database: " + e.getMessage());
        }
    }

    public void closeConnection(Long connectionId) {
        Connection connection = activeConnections.remove(connectionId);
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("Error closing database connection: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public void deactivateConnectionsForChat(Long chatId) {
        List<DatabaseConnection> connections = databaseConnectionRepository.findByChatIdAndActiveTrue(chatId);

        for (DatabaseConnection connection : connections) {
            connection.setActive(false);
            closeConnection(connection.getId());
        }

        databaseConnectionRepository.saveAll(connections);
    }

    private String buildJdbcUrl(DatabaseConnectionDto connection) {
        if ("postgresql".equalsIgnoreCase(connection.getDbType()) ||
                "greenplum".equalsIgnoreCase(connection.getDbType())) {
            return String.format("jdbc:postgresql://%s:%d/%s",
                    connection.getHost(), connection.getPort(), connection.getDatabaseName());
        }

        throw new DatabaseConnectionException("Unsupported database type: " + connection.getDbType());
    }

    private String buildJdbcUrl(DatabaseConnection connection) {
        if ("postgresql".equalsIgnoreCase(connection.getDbType()) ||
                "greenplum".equalsIgnoreCase(connection.getDbType())) {
            return String.format("jdbc:postgresql://%s:%d/%s",
                    connection.getHost(), connection.getPort(), connection.getDatabaseName());
        }

        throw new DatabaseConnectionException("Unsupported database type: " + connection.getDbType());
    }

    private DatabaseConnectionDto mapToDto(DatabaseConnection connection) {
        return DatabaseConnectionDto.builder()
                .id(connection.getId())
                .chatId(connection.getChat().getId())
                .name(connection.getName())
                .dbType(connection.getDbType())
                .host(connection.getHost())
                .port(connection.getPort())
                .databaseName(connection.getDatabaseName())
                .username(connection.getUsername())
                .password("********") // Don't expose password in DTO
                .active(connection.isActive())
                .createdAt(connection.getCreatedAt())
                .lastConnectedAt(connection.getLastConnectedAt())
                .build();
    }
}
