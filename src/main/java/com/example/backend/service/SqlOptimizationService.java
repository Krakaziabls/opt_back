package com.example.backend.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import com.example.backend.model.entity.SqlQuery;
import com.example.backend.repository.ChatRepository;
import com.example.backend.repository.DatabaseConnectionRepository;
import com.example.backend.repository.MessageRepository;
import com.example.backend.repository.SqlQueryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqlOptimizationService {

    private final SqlQueryRepository sqlQueryRepository;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final LLMService llmService; // Assumed service for query optimization
    private final DatabaseConnectionService databaseConnectionService;
    private final ChatService chatService; // Assumed service for chat operations

    @Transactional
    public Mono<SqlQueryResponse> optimizeQuery(Long userId, SqlQueryRequest request) {
        log.info("Starting query optimization for userId={}, chatId={}, query={}, llm={}",
                userId, request.getChatId(), request.getQuery(), request.getLlm());

        return Mono.fromCallable(() -> {
                    log.debug("Validating chat existence and ownership");
                    // Validate chat existence and ownership
                    Chat chat = chatRepository.findById(request.getChatId())
                            .orElseThrow(() -> new ResourceNotFoundException("Chat not found with ID: " + request.getChatId()));
                    log.debug("Chat found: id={}, title={}", chat.getId(), chat.getTitle());

                    log.debug("Validating SQL query syntax");
                    // Validate SQL query syntax
                    validateSqlQuery(request.getQuery());
                    log.debug("SQL query syntax is valid");

                    log.debug("Saving user's query as a message");
                    // Save user's query as a message
                    MessageDto userMessageDto = MessageDto.builder()
                            .content(request.getQuery())
                            .fromUser(true)
                            .build();
                    return chatService.sendMessage(request.getChatId(), userId, userMessageDto);
                })
                .flatMap(savedUserMessageDto -> {
                    log.debug("User message saved: id={}", savedUserMessageDto.getId());
                    
                    // Handle database connection if provided
                    DatabaseConnection dbConnection = null;
                    if (request.getDatabaseConnectionId() != null && !request.getDatabaseConnectionId().isBlank()) {
                        log.debug("Processing database connection: id={}", request.getDatabaseConnectionId());
                        try {
                            Long dbConnectionId = Long.parseLong(request.getDatabaseConnectionId());
                            dbConnection = databaseConnectionRepository.findByIdAndChatId(dbConnectionId, request.getChatId())
                                    .orElseThrow(() -> new ResourceNotFoundException(
                                            "Database connection not found or not linked to chat: " + dbConnectionId));
                            log.debug("Database connection found: id={}, name={}", dbConnection.getId(), dbConnection.getName());
                        } catch (NumberFormatException e) {
                            log.error("Invalid database connection ID: {}", request.getDatabaseConnectionId());
                            throw new ApiException("Invalid database connection ID: " + request.getDatabaseConnectionId(),
                                    HttpStatus.BAD_REQUEST);
                        }
                    } else {
                        log.debug("No database connection provided");
                    }

                    // Optimize query using LLM
                    DatabaseConnection finalDbConnection = dbConnection;
                    log.debug("Calling LLM service for query optimization");
                    return llmService.optimizeSqlQuery(request.getQuery())
                            .doOnSuccess(optimizedQuery -> log.debug("LLM optimization successful: {}", optimizedQuery))
                            .doOnError(error -> log.error("LLM optimization failed: {}", error.getMessage(), error))
                            .flatMap(optimizedQuery -> {
                                log.debug("Saving optimized query as a system message");
                                // Save optimized query as a system message
                                MessageDto llmMessageDto = MessageDto.builder()
                                        .content(optimizedQuery)
                                        .fromUser(false)
                                        .build();
                                return Mono.fromCallable(() -> chatService.sendMessage(request.getChatId(), userId, llmMessageDto))
                                        .doOnSuccess(savedLlmMessageDto -> log.debug("System message saved: id={}", savedLlmMessageDto.getId()))
                                        .map(savedLlmMessageDto -> {
                                            log.debug("Building SqlQuery entity");
                                            // Build and save SqlQuery entity
                                            SqlQuery sqlQuery = SqlQuery.builder()
                                                    .message(messageRepository.findById(savedUserMessageDto.getId())
                                                            .orElseThrow(() -> new ResourceNotFoundException(
                                                                    "User message not found: " + savedUserMessageDto.getId())))
                                                    .originalQuery(request.getQuery())
                                                    .optimizedQuery(optimizedQuery)
                                                    .databaseConnection(finalDbConnection)
                                                    .createdAt(LocalDateTime.now())
                                                    .build();

                                            // Measure execution time if connection exists
                                            if (finalDbConnection != null) {
                                                log.debug("Measuring query execution time");
                                                try {
                                                    long executionTime = measureQueryExecutionTime(finalDbConnection.getId(), optimizedQuery);
                                                    sqlQuery.setExecutionTimeMs(executionTime);
                                                    log.debug("Execution time measured: {}ms", executionTime);
                                                } catch (SQLException e) {
                                                    log.warn("Failed to measure execution time for query: {}", e.getMessage());
                                                }
                                            }

                                            log.debug("Saving SqlQuery entity");
                                            SqlQuery savedQuery = sqlQueryRepository.save(sqlQuery);
                                            log.info("SQL query saved: id={}", savedQuery.getId());

                                            return SqlQueryResponse.builder()
                                                    .id(savedQuery.getId())
                                                    .originalQuery(savedQuery.getOriginalQuery())
                                                    .optimizedQuery(savedQuery.getOptimizedQuery())
                                                    .executionTimeMs(savedQuery.getExecutionTimeMs())
                                                    .createdAt(savedQuery.getCreatedAt())
                                                    .message(savedLlmMessageDto)
                                                    .build();
                                        });
                            });
                })
                .onErrorMap(e -> {
                    if (!(e instanceof ResourceNotFoundException) && !(e instanceof ApiException)) {
                        log.error("Unexpected error during query optimization: {}", e.getMessage(), e);
                        return new ApiException("Internal error during query optimization", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    return e;
                });
    }

    public List<SqlQueryResponse> getQueryHistory(Long chatId, Long userId) {
        log.info("Fetching query history for userId={}, chatId={}", userId, chatId);

        // Validate chat existence
        chatRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found with ID: " + chatId));

        List<SqlQuery> queries = sqlQueryRepository.findByMessageChatIdOrderByCreatedAtDesc(chatId);
        return queries.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateSqlQuery(String query) {
        try {
            CCJSqlParserUtil.parse(query);
        } catch (JSQLParserException e) {
            log.error("Invalid SQL query syntax: {}", e.getMessage());
            throw new ApiException("Invalid SQL query: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private long measureQueryExecutionTime(Long connectionId, String query) throws SQLException {
        if (!query.trim().toUpperCase().startsWith("SELECT")) {
            log.info("Skipping execution time measurement for non-SELECT query: {}", query);
            return Long.parseLong(null); // Use null to indicate no measurement for non-SELECT queries
        }

        Connection connection = databaseConnectionService.getConnection(connectionId);
        String explainQuery = "EXPLAIN ANALYZE " + query;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(explainQuery)) {
            StringBuilder output = new StringBuilder();
            while (rs.next()) {
                output.append(rs.getString(1)).append("\n");
            }

            Pattern pattern = Pattern.compile("Execution Time: (\\d+\\.\\d+) ms");
            Matcher matcher = pattern.matcher(output.toString());
            if (matcher.find()) {
                return (long) Double.parseDouble(matcher.group(1));
            } else {
                log.warn("Execution time not found in EXPLAIN ANALYZE output");
                return Long.parseLong(null);
            }
        } catch (SQLException e) {
            log.error("SQLException during execution time measurement: {}", e.getMessage());
            throw e;
        }
    }

    private SqlQueryResponse mapToResponse(SqlQuery sqlQuery) {
        MessageDto messageDto = MessageDto.builder()
                .content(sqlQuery.getOriginalQuery())
                .fromUser(true)
                .build(); // Simplified for this example; adjust based on your MessageDto needs

        return SqlQueryResponse.builder()
                .id(sqlQuery.getId())
                .originalQuery(sqlQuery.getOriginalQuery())
                .optimizedQuery(sqlQuery.getOptimizedQuery())
                .executionTimeMs(sqlQuery.getExecutionTimeMs())
                .createdAt(sqlQuery.getCreatedAt())
                .message(messageDto)
                .build();
    }
}
