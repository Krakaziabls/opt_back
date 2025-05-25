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
    private final LLMService llmService;
    private final DatabaseConnectionService databaseConnectionService;
    private final ChatService chatService;

    @Transactional
    public Mono<SqlQueryResponse> optimizeQuery(Long userId, SqlQueryRequest request) {
        log.debug("Optimizing query for chatId={}, userId={}, query={}",
                request.getChatId(), userId, request.getQuery());

        return Mono.just(request)
                .flatMap(req -> {
                    // Проверка существования чата
                    Chat chat = chatRepository.findByIdAndUserId(req.getChatId(), userId)
                            .orElseThrow(() -> {
                                log.error("Chat not found: chatId={}, userId={}", req.getChatId(), userId);
                                return new ResourceNotFoundException("Chat not found");
                            });

                    // Валидация SQL-запроса
                    validateSqlQuery(req.getQuery());

                    // Сохранение сообщения пользователя через ChatService
                    MessageDto userMessageDto = MessageDto.builder()
                            .content(req.getQuery())
                            .fromUser(true)
                            .build();
                    return Mono.just(chatService.sendMessage(req.getChatId(), userId, userMessageDto));
                })
                .flatMap(savedUserMessageDto -> {
                    // Проверка подключения к базе данных, если указано
                    DatabaseConnection dbConnection;
                    if (request.getDatabaseConnectionId() != null) {
                        dbConnection = databaseConnectionRepository.findById(Long.parseLong(request.getDatabaseConnectionId()))
                                .orElseThrow(() -> {
                                    log.error("Database connection not found: id={}", request.getDatabaseConnectionId());
                                    return new ResourceNotFoundException("Database connection not found");
                                });
                    } else {
                        dbConnection = null;
                    }

                    // Оптимизация запроса через LLMService
                    return llmService.optimizeSqlQuery(request.getQuery())
                            .flatMap(optimizedQuery -> {
                                // Сохранение ответа системы через ChatService
                                MessageDto llmMessageDto = MessageDto.builder()
                                        .content(optimizedQuery)
                                        .fromUser(false)
                                        .build();
                                return Mono.just(chatService.sendMessage(request.getChatId(), userId, llmMessageDto))
                                        .map(savedLlmMessageDto -> {
                                            // Создание сущности SQL-запроса
                                            SqlQuery sqlQuery = SqlQuery.builder()
                                                    .message(messageRepository.findById(savedUserMessageDto.getId())
                                                            .orElseThrow(() -> {
                                                                log.error("User message not found: id={}", savedUserMessageDto.getId());
                                                                return new ResourceNotFoundException("User message not found");
                                                            }))
                                                    .originalQuery(request.getQuery())
                                                    .optimizedQuery(optimizedQuery)
                                                    .databaseConnection(dbConnection)
                                                    .build();

                                            // Измерение времени выполнения, если есть подключение к базе данных
                                            if (dbConnection != null) {
                                                try {
                                                    long executionTime = measureQueryExecutionTime(dbConnection.getId(), optimizedQuery);
                                                    sqlQuery.setExecutionTimeMs(executionTime);
                                                } catch (Exception e) {
                                                    log.error("Error executing optimized query: {}", e.getMessage());
                                                }
                                            }

                                            // Сохранение запроса в базе данных
                                            SqlQuery savedQuery = sqlQueryRepository.save(sqlQuery);

                                            // Обновление времени чата
                                            Chat chat = chatRepository.findById(request.getChatId())
                                                    .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));
                                            chat.setUpdatedAt(LocalDateTime.now());
                                            chatRepository.save(chat);

                                            // Формирование ответа
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
                    log.error("Error optimizing query: {}", e.getMessage(), e);
                    return new ApiException("Error optimizing query: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    public List<SqlQueryResponse> getQueryHistory(Long chatId, Long userId) {
        log.debug("Fetching query history for chatId={}, userId={}", chatId, userId);

        // Проверка существования чата
        chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> {
                    log.error("Chat not found: chatId={}, userId={}", chatId, userId);
                    return new ResourceNotFoundException("Chat not found");
                });

        // Получение истории запросов
        List<SqlQuery> queries = sqlQueryRepository.findByMessageChatIdOrderByCreatedAtDesc(chatId);

        return queries.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateSqlQuery(String query) {
        try {
            CCJSqlParserUtil.parse(query);
        } catch (JSQLParserException e) {
            log.error("Invalid SQL query: {}", e.getMessage());
            throw new ApiException("Invalid SQL query: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private long measureQueryExecutionTime(Long connectionId, String query) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnectionService.getConnection(connectionId);

            String explainQuery = "EXPLAIN ANALYZE " + query;

            long startTime = System.currentTimeMillis();

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(explainQuery);
            }

            long endTime = System.currentTimeMillis();
            return endTime - startTime;
        } finally {
            // Подключение управляется DatabaseConnectionService
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
}
