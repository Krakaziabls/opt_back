package com.example.backend.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.exception.ApiException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.model.dto.SqlQueryRequest;
import com.example.backend.model.dto.SqlQueryResponse;
import com.example.backend.model.entity.Chat;
import com.example.backend.model.entity.DatabaseConnection;
import com.example.backend.model.entity.Message;
import com.example.backend.model.entity.SqlQuery;
import com.example.backend.repository.ChatRepository;
import com.example.backend.repository.DatabaseConnectionRepository;
import com.example.backend.repository.MessageRepository;
import com.example.backend.repository.SqlQueryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqlOptimizationService {

    private final SqlQueryRepository sqlQueryRepository;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final LLMService llmService;
    private final DatabaseConnectionService databaseConnectionService;

    @Transactional
    public SqlQueryResponse optimizeQuery(Long userId, SqlQueryRequest request) {
        // Validate chat belongs to user
        Chat chat = chatRepository.findByIdAndUserId(request.getChatId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        // Validate SQL query
        validateSqlQuery(request.getQuery());

        // Save user message
        Message userMessage = Message.builder()
                .chat(chat)
                .content(request.getQuery())
                .fromUser(true)
                .build();

        userMessage = messageRepository.save(userMessage);
        chat.getMessages().add(userMessage);

        // Get database connection if provided
        DatabaseConnection dbConnection = null;
        if (request.getDatabaseConnectionId() != null) {
            dbConnection = databaseConnectionRepository.findById(Long.parseLong(request.getDatabaseConnectionId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Database connection not found"));
        }

        // Optimize query using LLM
        String optimizedQuery = llmService.optimizeSqlQuery(request.getQuery());

        // Save LLM response message
        Message llmMessage = Message.builder()
                .chat(chat)
                .content(optimizedQuery)
                .fromUser(false)
                .build();

        llmMessage = messageRepository.save(llmMessage);
        chat.getMessages().add(llmMessage);

        // Create SQL query record
        SqlQuery sqlQuery = SqlQuery.builder()
                .message(userMessage)
                .originalQuery(request.getQuery())
                .optimizedQuery(optimizedQuery)
                .databaseConnection(dbConnection)
                .build();

        // If database connection is provided, execute the query to measure performance
        if (dbConnection != null) {
            try {
                long executionTime = measureQueryExecutionTime(dbConnection.getId(), optimizedQuery);
                sqlQuery.setExecutionTimeMs(executionTime);
            } catch (Exception e) {
                log.error("Error executing optimized query: {}", e.getMessage());
                // Continue without execution time
            }
        }

        SqlQuery savedQuery = sqlQueryRepository.save(sqlQuery);

        // Update chat's updatedAt timestamp
        chat.setUpdatedAt(LocalDateTime.now());
        chatRepository.save(chat);

        return SqlQueryResponse.builder()
                .id(savedQuery.getId())
                .originalQuery(savedQuery.getOriginalQuery())
                .optimizedQuery(savedQuery.getOptimizedQuery())
                .executionTimeMs(savedQuery.getExecutionTimeMs())
                .createdAt(savedQuery.getCreatedAt())
                .message(mapToMessageDto(llmMessage))
                .build();
    }

    public List<SqlQueryResponse> getQueryHistory(Long chatId, Long userId) {
        // Validate chat belongs to user
        chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        List<SqlQuery> queries = sqlQueryRepository.findByMessageChatIdOrderByCreatedAtDesc(chatId);

        return queries.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateSqlQuery(String query) {
        try {
            // Parse the SQL query to validate syntax
            CCJSqlParserUtil.parse(query);
        } catch (JSQLParserException e) {
            throw new ApiException("Invalid SQL query: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private long measureQueryExecutionTime(Long connectionId, String query) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnectionService.getConnection(connectionId);

            // Add EXPLAIN ANALYZE to get execution plan and timing
            String explainQuery = "EXPLAIN ANALYZE " + query;

            long startTime = System.currentTimeMillis();

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(explainQuery);
            }

            long endTime = System.currentTimeMillis();
            return endTime - startTime;
        } finally {
            // Don't close the connection here, as it's managed by the DatabaseConnectionService
        }
    }

    private SqlQueryResponse mapToResponse(SqlQuery sqlQuery) {
        return SqlQueryResponse.builder()
                .id(sqlQuery.getId())
                .originalQuery(sqlQuery.getOriginalQuery())
                .optimizedQuery(sqlQuery.getOptimizedQuery())
                .executionTimeMs(sqlQuery.getExecutionTimeMs())
                .createdAt(sqlQuery.getCreatedAt())
                .build();
    }

    private MessageDto mapToMessageDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .content(message.getContent())
                .fromUser(message.isFromUser())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
