package com.example.backend.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import com.example.sqlopt.ast.QueryPlanResult;
import com.example.sqlopt.service.ASTService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlOptimizationService {

    private final SqlQueryRepository sqlQueryRepository;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final LLMService llmService;
    private final DatabaseConnectionService databaseConnectionService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ASTService astService;

    @Transactional
    public Mono<SqlQueryResponse> optimizeQuery(Long userId, SqlQueryRequest request) {
        log.info("Starting query optimization for userId={}, chatId={}, query={}, llm={}, isMPP={}",
                userId, request.getChatId(), request.getQuery(), request.getLlm(), request.isMPP());

        // Создаем сообщение для исходного запроса пользователя
        AtomicReference<Message> userMessageRef = new AtomicReference<>(new Message());
        Message initialMessage = userMessageRef.get();
        initialMessage.setContent(request.getQuery());
        initialMessage.setFromUser(true);
        initialMessage.setCreatedAt(LocalDateTime.now());

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

                    // Устанавливаем чат для сообщения и сохраняем его
                    initialMessage.setChat(chat);
                    Message savedMessage = messageRepository.save(initialMessage);
                    userMessageRef.set(savedMessage);
                    log.debug("Saved user message: id={}", savedMessage.getId());

                    return chat;
                })
                .flatMap(chat -> {
                    // Проверяем наличие подключения к БД
                    DatabaseConnection dbConnection = null;
                    if (request.getDatabaseConnectionId() != null) {
                        dbConnection = databaseConnectionRepository.findById(request.getDatabaseConnectionId())
                                .orElseThrow(() -> new ResourceNotFoundException("Database connection not found"));
                    }
                    final DatabaseConnection finalDbConnection = dbConnection;

                    // Получаем шаблон промпта
                    String promptTemplate = request.getPromptTemplate();
                    if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
                        promptTemplate = getDefaultPromptTemplate(request.isMPP(), finalDbConnection != null);
                    }

                    return llmService
                            .optimizeSqlQuery(request.getQuery(), request.getLlm(), promptTemplate)
                            .doOnSuccess(optimizedQuery -> log.debug("LLM optimization successful: {}", optimizedQuery))
                            .doOnError(error -> log.error("LLM optimization failed: {}", error.getMessage(), error))
                            .flatMap(optimizedQuery -> {
                                log.debug("Saving optimized query");

                                // Создаем сообщение для оптимизированного запроса
                                Message message = new Message();
                                message.setChat(chat);
                                message.setContent(optimizedQuery);
                                message.setFromUser(false);
                                message.setCreatedAt(LocalDateTime.now());
                                message = messageRepository.save(message);
                                log.debug("Saved message for optimized query: id={}", message.getId());

                                // Build and save SqlQuery entity
                                SqlQuery sqlQuery = SqlQuery.builder()
                                        .message(userMessageRef.get())
                                        .originalQuery(request.getQuery())
                                        .optimizedQuery(optimizedQuery)
                                        .databaseConnection(finalDbConnection)
                                        .createdAt(LocalDateTime.now())
                                        .build();

                                // Measure execution time and get EXPLAIN ANALYZE output if connection exists
                                String explainOutput = null;
                                QueryPlanResult planResult = null;
                                if (finalDbConnection != null) {
                                    log.debug("Measuring query execution time and analyzing query plan");
                                    try {
                                        ExecutionResult executionResult = measureQueryExecutionTime(finalDbConnection.getId(), optimizedQuery);
                                        sqlQuery.setExecutionTimeMs(executionResult.getExecutionTime());
                                        explainOutput = executionResult.getExplainOutput();
                                        if (explainOutput != null) {
                                            planResult = astService.analyzeQueryPlan(explainOutput);
                                        }
                                        log.debug("Execution time measured: {}ms, plan analyzed: {}", executionResult.getExecutionTime(), planResult != null);
                                    } catch (SQLException e) {
                                        log.warn("Failed to measure execution time or analyze query plan: {}", e.getMessage());
                                    }
                                }

                                log.debug("Saving SqlQuery entity");
                                SqlQuery savedQuery = sqlQueryRepository.save(sqlQuery);
                                log.info("SQL query saved: id={}", savedQuery.getId());

                                // Форматируем ответ с учетом результатов AST-анализа
                                String formattedResponse = formatOptimizationResponse(optimizedQuery, planResult);
                                message.setContent(formattedResponse);
                                messageRepository.save(message);

                                // Отправляем сообщение через WebSocket
                                String destination = "/topic/chat/" + request.getChatId();
                                messagingTemplate.convertAndSend(destination, mapToMessageDto(message));
                                log.info("Successfully sent message to {}: id={}", destination, message.getId());

                                return Mono.just(mapToResponse(savedQuery));
                            });
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

    private ExecutionResult measureQueryExecutionTime(Long connectionId, String query) throws SQLException {
        if (!query.trim().toUpperCase().startsWith("SELECT")) {
            log.info("Skipping execution time measurement for non-SELECT query: {}", query);
            return new ExecutionResult(-1, null);
        }

        Connection connection = databaseConnectionService.getConnection(connectionId);
        String explainQuery = "EXPLAIN ANALYZE " + query;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(explainQuery)) {
            StringBuilder output = new StringBuilder();
            while (rs.next()) {
                output.append(rs.getString(1)).append("\n");
            }
            String explainOutput = output.toString();
            Pattern pattern = Pattern.compile("Execution Time: (\\d+\\.\\d+) ms");
            Matcher matcher = pattern.matcher(explainOutput);
            if (matcher.find()) {
                long executionTime = (long) Double.parseDouble(matcher.group(1));
                return new ExecutionResult(executionTime, explainOutput);
            } else {
                log.warn("Execution time not found in EXPLAIN ANALYZE output");
                return new ExecutionResult(-1, explainOutput);
            }
        } catch (SQLException e) {
            log.error("SQLException during execution time measurement: {}", e.getMessage());
            throw e;
        }
    }

    private static class ExecutionResult {
        private final long executionTime;
        private final String explainOutput;

        public ExecutionResult(long executionTime, String explainOutput) {
            this.executionTime = executionTime;
            this.explainOutput = explainOutput;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public String getExplainOutput() {
            return explainOutput;
        }
    }

    private SqlQueryResponse mapToResponse(SqlQuery sqlQuery) {
        return SqlQueryResponse.builder()
                .id(sqlQuery.getId().toString())
                .originalQuery(sqlQuery.getOriginalQuery())
                .optimizedQuery(sqlQuery.getOptimizedQuery())
                .createdAt(sqlQuery.getCreatedAt().toString())
                .message(mapToMessageDto(sqlQuery.getMessage()))
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

    private String getDefaultPromptTemplate(boolean isMPP, boolean hasConnection) {
        if (isMPP && hasConnection) {
            return """
                                        Ты — специалист по оптимизации SQL-запросов в MPP-системах, включая Greenplum. Твоя цель — переписать SQL-запрос так, чтобы он выполнялся быстрее и использовал меньше ресурсов, без изменения логики и без вмешательства в СУБД.

                    ociative

                                        Входные данные SQL-запрос:
                                        {query_text}
                                        План выполнения (EXPLAIN): {query_plan}
                                        Метаданные таблиц: {tables_meta}

                                        Выходные данные
                                        Оптимизированный SQL-запрос:
                                        {optimized_query}
                                        Обоснование изменений:
                                        Кратко опиши, какие узкие места были найдены в плане запроса, и какие методы оптимизации применены.
                                        Оценка улучшения:
                                        Примерное снижение времени выполнения или факторы, которые повлияют на производительность.
                                        Потенциальные риски:
                                        Возможные побочные эффекты изменений, если таковые имеются.""";
        } else if (isMPP && !hasConnection) {
            return """
                    Ты — специалист по оптимизации SQL-запросов в MPP-системах, включая Greenplum. Твоя цель — переписать SQL-запрос так, чтобы он выполнялся быстрее и использовал меньше ресурсов, без изменения логики и без вмешательства в СУБД.

                    Входные данные SQL-запрос:
                    {query_text}

                    Выходные данные
                    Оптимизированный SQL-запрос:
                    {optimized_query}
                    Обоснование изменений:
                    Кратко опиши, какие методы оптимизации применены и почему.
                    Оценка улучшения:
                    Примерное снижение времени выполнения или факторы, которые повлияют на производительность.
                    Потенциальные риски:
                    Возможные побочные эффекты изменений, если таковые имеются.""";
        } else if (!isMPP && hasConnection) {
            return """
                    Ты — специалист по оптимизации SQL-запросов в PostgreSQL. Твоя цель — переписать SQL-запрос так, чтобы он вовремя быстрее и использовал меньше ресурсов, без изменения логики и без вмешательства в СУБД.

                    Входные данные SQL-запрос:
                    {query_text}
                    План выполнения (EXPLAIN): {query_plan}
                    Метаданные таблиц: {tables_meta}

                    Выходные данные
                    Оптимизированный SQL-запрос:
                    {optimized_query}
                    Обоснование изменений:
                    Кратко опиши, какие узкие места были найдены в плане запроса, и какие методы оптимизации применены.
                    Оценка улучшения:
                    Примерное снижение времени выполнения или факторы, которые повлияют на производительность.
                    Потенциальные риски:
                    Возможные побочные эффекты изменений, если таковые имеются.""";
        } else {
            return """
                    Ты — специалист по оптимизации SQL-запросов в PostgreSQL. Твоя цель — переписать SQL-запрос так, чтобы он выполнялся быстрее и использовал меньше ресурсов, без изменения логики и без вмешательства в СУБД.

                    Входные данные SQL-запрос:
                    {query_text}

                    Выходные данные
                    Оптимизированный SQL-запрос:
                    {optimized_query}
                    Обоснование изменений:
                    Кратко опиши, какие методы оптимизации применены и почему.
                    Оценка улучшения:
                    Примерное снижение времени выполнения или факторы, которые повлияют на производительность.
                    Потенциальные риски:
                    Возможные побочные эффекты изменений, если таковые имеются.""";
        }
    }

    private String formatOptimizationResponse(String optimizedQuery, QueryPlanResult planResult) {
        StringBuilder response = new StringBuilder();

        // Добавляем оптимизированный запрос
        response.append("## Оптимизированный SQL-запрос\n\n");
        response.append("```sql\n").append(optimizedQuery).append("\n```\n\n");

        if (planResult != null && !planResult.getOperations().isEmpty()) {
            response.append("## Анализ плана запроса\n\n");
            for (com.example.sqlopt.ast.Operation operation : planResult.getOperations()) {
                response.append("- **").append(operation.getType()).append("**");
                if (operation.getTableName() != null) {
                    response.append(" для таблицы `").append(operation.getTableName()).append("`");
                }
                response.append("\n");

                if (operation.getTableMetadata() != null) {
                    response.append("  - Метаданные таблицы: ").append(operation.getTableMetadata()).append("\n");
                }

                if (operation.getStatistics() != null && !operation.getStatistics().isEmpty()) {
                    response.append("  - Статистика: ").append(operation.getStatistics()).append("\n");
                }

                if (operation.getKeys() != null && !operation.getKeys().isEmpty()) {
                    response.append("  - Ключи: ").append(String.join(", ", operation.getKeys())).append("\n");
                }

                if (operation.getConditions() != null && !operation.getConditions().isEmpty()) {
                    response.append("  - Условия: ").append(String.join(", ", operation.getConditions())).append("\n");
                }

                if (operation.getAdditionalInfo() != null && !operation.getAdditionalInfo().isEmpty()) {
                    response.append("  - Дополнительная информация: ").append(operation.getAdditionalInfo()).append("\n");
                }
            }
        }

        // Добавляем секции для обоснования изменений, оценки улучшения и рисков
        response.append("\n## Обоснование изменений\n\n");
        response.append("Оптимизация запроса выполнена с учетом анализа плана выполнения и лучших практик SQL.\n\n");

        response.append("## Оценка улучшения\n\n");
        response.append("Ожидается улучшение производительности за счет оптимизации структуры запроса и использования более эффективных операций.\n\n");

        response.append("## Потенциальные риски\n\n");
        response.append("Изменения не должны повлиять на логику работы запроса, так как оптимизация направлена только на улучшение производительности.");

        return response.toString();
    }
}
