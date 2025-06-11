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
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.StringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubJoin;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SqlOptimizationService {
    private static final Logger log = LoggerFactory.getLogger(SqlOptimizationService.class);

    private final SqlQueryRepository sqlQueryRepository;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final LLMService llmService;
    private final DatabaseConnectionService databaseConnectionService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ASTService astService;

    private static class LLMResponse {
        private String optimizedSQL;
        private String optimizationRationale;
        private String performanceImpact;
        private String potentialRisks;
    }

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

                    // Собираем метаданные таблиц, если есть подключение к БД
                    final Map<String, Map<String, Object>> tablesMetadata = new HashMap<>();
                    if (finalDbConnection != null) {
                        try {
                            Connection connection = databaseConnectionService.getConnection(finalDbConnection.getId());
                            List<String> tables = extractTablesFromQuery(request.getQuery());
                            if (!tables.isEmpty()) {
                                tablesMetadata.putAll(collectTableMetadata(connection, tables));
                                log.debug("Collected metadata for {} tables", tablesMetadata.size());
                            }
                        } catch (SQLException e) {
                            log.warn("Failed to collect table metadata: {}", e.getMessage());
                        }
                    }

                    return llmService
                            .optimizeSqlQuery(request.getQuery(), request.getLlm(), promptTemplate)
                            .doOnSuccess(optimizedQuery -> log.debug("LLM optimization successful: {}", optimizedQuery))
                            .doOnError(error -> log.error("LLM optimization failed: {}", error.getMessage(), error))
                            .flatMap(llmResponse -> {
                                log.debug("Parsing LLM response");
                                LLMResponse parsedResponse = parseLLMResponse(llmResponse);
                                String optimizedQuery = parsedResponse.optimizedSQL;

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
                                        .optimizationRationale(parsedResponse.optimizationRationale)
                                        .performanceImpact(parsedResponse.performanceImpact)
                                        .potentialRisks(parsedResponse.potentialRisks)
                                        .build();

                                // Measure execution time and get EXPLAIN ANALYZE output if connection exists
                                String explainOutput = null;
                                QueryPlanResult planResult = null;
                                if (finalDbConnection != null) {
                                    log.debug("Measuring query execution time and analyzing query plan");
                                    try {
                                        // Получаем план для исходного запроса
                                        ExecutionResult originalExecutionResult = measureQueryExecutionTime(finalDbConnection.getId(), request.getQuery());
                                        sqlQuery.setExecutionTimeMs(originalExecutionResult.getExecutionTime());
                                        if (originalExecutionResult.getExplainOutput() != null) {
                                            sqlQuery.setOriginalPlan(astService.analyzeQueryPlan(originalExecutionResult.getExplainOutput()));
                                        }

                                        // Получаем план для оптимизированного запроса
                                        ExecutionResult optimizedExecutionResult = measureQueryExecutionTime(finalDbConnection.getId(), optimizedQuery);
                                        if (optimizedExecutionResult.getExplainOutput() != null) {
                                            planResult = astService.analyzeQueryPlan(optimizedExecutionResult.getExplainOutput());
                                            sqlQuery.setOptimizedPlan(planResult);
                                        }
                                        log.debug("Execution time measured: {}ms, plan analyzed: {}", 
                                            optimizedExecutionResult.getExecutionTime(), planResult != null);
                                    } catch (SQLException e) {
                                        log.warn("Failed to measure execution time or analyze query plan: {}", e.getMessage());
                                    }
                                }

                                log.debug("Saving SqlQuery entity");
                                SqlQuery savedQuery = sqlQueryRepository.save(sqlQuery);
                                log.info("SQL query saved: id={}", savedQuery.getId());

                                // Форматируем ответ с учетом результатов AST-анализа
                                String formattedResponse = formatOptimizationResponse(
                                    optimizedQuery, 
                                    planResult, 
                                    finalDbConnection != null ? tablesMetadata : null,
                                    parsedResponse.optimizationRationale,
                                    parsedResponse.performanceImpact,
                                    parsedResponse.potentialRisks
                                );
                                message.setContent(formattedResponse);
                                messageRepository.save(message);

                                // Отправляем сообщение через WebSocket
                                String destination = "/topic/chat/" + request.getChatId();
                                MessageDto messageDto = MessageDto.builder()
                                    .id(message.getId())
                                    .content(formattedResponse)
                                    .fromUser(false)
                                    .createdAt(message.getCreatedAt())
                                    .chatId(request.getChatId())
                                    .build();
                                messagingTemplate.convertAndSend(destination, messageDto);
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
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            net.sf.jsqlparser.statement.Statement statement = parserManager.parse(new StringReader(query));
            
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                SelectBody selectBody = selectStatement.getSelectBody();
                
                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    
                    // Получаем таблицы из FROM
                    FromItem fromItem = plainSelect.getFromItem();
                    if (fromItem instanceof Table) {
                        // This is already handled in extractTablesFromQuery
                    } else if (fromItem instanceof SubJoin) {
                        // This is already handled in extractTablesFromQuery
                    }
                    
                    // Получаем таблицы из JOIN
                    if (plainSelect.getJoins() != null) {
                        for (Join join : plainSelect.getJoins()) {
                            if (join.getRightItem() instanceof Table) {
                                // This is already handled in extractTablesFromQuery
                            }
                        }
                    }
                } else if (selectBody instanceof SetOperationList) {
                    // This is already handled in extractTablesFromQuery
                }
            }
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

        try (java.sql.Statement stmt = connection.createStatement();
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
                .executionTimeMs(sqlQuery.getExecutionTimeMs())
                .originalPlan(sqlQuery.getOriginalPlan())
                .optimizedPlan(sqlQuery.getOptimizedPlan())
                .optimizationRationale(sqlQuery.getOptimizationRationale())
                .performanceImpact(sqlQuery.getPerformanceImpact())
                .potentialRisks(sqlQuery.getPotentialRisks())
                .build();
    }

    private MessageDto mapToMessageDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .content(message.getContent())
                .fromUser(message.isFromUser())
                .createdAt(message.getCreatedAt())
                .chatId(message.getChat().getId())
                .build();
    }

    private String getDefaultPromptTemplate(boolean isMPP, boolean hasConnection) {
        StringBuilder template = new StringBuilder();
        
        template.append("Ты — специалист по оптимизации SQL-запросов. Твоя задача — проанализировать предоставленный SQL-запрос и предложить его оптимизированную версию.\n\n");
        
        template.append("SQL-запрос:\n");
        template.append("```sql\n");
        template.append("{query}\n");
        template.append("```\n\n");

        if (hasConnection) {
            template.append("План выполнения:\n");
            template.append("```sql\n");
            template.append("{explain_output}\n");
            template.append("```\n\n");

            template.append("Метаданные таблиц:\n");
            template.append("{tables_meta}\n\n");
        } else {
            template.append("Примечание: Подключение к базе данных отсутствует. Оптимизация будет выполнена на основе общих принципов и лучших практик SQL.\n\n");
        }

        template.append("Требования к ответу:\n");
        template.append("1. Предложи оптимизированную версию запроса с объяснением внесенных изменений.\n");
        template.append("2. Объясни, почему эти изменения улучшат производительность.\n");
        template.append("3. Укажи потенциальные риски или побочные эффекты изменений, если таковые имеются.\n\n");

        template.append("Формат ответа:\n");
        template.append("## Оптимизированный SQL-запрос\n\n");
        template.append("```sql\n");
        template.append("[оптимизированный запрос]\n");
        template.append("```\n\n");
        
        template.append("## Обоснование оптимизации\n\n");
        template.append("[подробное объяснение внесенных изменений]\n\n");
        
        template.append("## Оценка улучшения\n\n");
        template.append("[оценка ожидаемого улучшения производительности]\n\n");
        
        template.append("## Потенциальные риски\n\n");
        template.append("[описание возможных рисков и побочных эффектов]\n\n");

        return template.toString();
    }

    private LLMResponse parseLLMResponse(String response) {
        LLMResponse result = new LLMResponse();
        
        // Разбиваем ответ на секции
        String[] sections = response.split("##");
        
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            
            if (section.contains("Оптимизированный SQL-запрос")) {
                result.optimizedSQL = extractSQLFromSection(section);
            } else if (section.contains("Обоснование оптимизации")) {
                result.optimizationRationale = extractContentFromSection(section);
            } else if (section.contains("Оценка улучшения")) {
                result.performanceImpact = extractContentFromSection(section);
            } else if (section.contains("Потенциальные риски")) {
                result.potentialRisks = extractContentFromSection(section);
            }
        }
        
        // Если не нашли оптимизированный SQL, используем исходный запрос
        if (result.optimizedSQL == null || result.optimizedSQL.trim().isEmpty()) {
            result.optimizedSQL = response;
        }
        
        return result;
    }

    private String extractSQLFromSection(String section) {
        int startIndex = section.indexOf("```sql");
        if (startIndex == -1) return null;
        
        startIndex += 6; // длина ```sql
        int endIndex = section.indexOf("```", startIndex);
        if (endIndex == -1) return null;
        
        return section.substring(startIndex, endIndex).trim();
    }

    private String extractContentFromSection(String section) {
        int startIndex = section.indexOf("\n");
        if (startIndex == -1) return null;
        
        return section.substring(startIndex).trim();
    }

    private String formatOptimizationResponse(
            String optimizedQuery, 
            QueryPlanResult planResult, 
            Map<String, Map<String, Object>> tablesMetadata,
            String optimizationRationale,
            String performanceImpact,
            String potentialRisks) {
        StringBuilder response = new StringBuilder();

        // Добавляем оптимизированный запрос
        response.append("## Оптимизированный SQL-запрос\n\n");
        response.append("```sql\n").append(optimizedQuery).append("\n```\n\n");

        // Добавляем обоснование оптимизации
        if (optimizationRationale != null && !optimizationRationale.isEmpty()) {
            response.append("## Обоснование оптимизации\n\n");
            response.append(optimizationRationale).append("\n\n");
        }

        // Добавляем оценку улучшения
        if (performanceImpact != null && !performanceImpact.isEmpty()) {
            response.append("## Оценка улучшения\n\n");
            response.append(performanceImpact).append("\n\n");
        }

        // Добавляем потенциальные риски
        if (potentialRisks != null && !potentialRisks.isEmpty()) {
            response.append("## Потенциальные риски\n\n");
            response.append(potentialRisks).append("\n\n");
        }

        // Добавляем анализ плана выполнения только если есть подключение и план
        if (planResult != null && !planResult.getOperations().isEmpty()) {
            response.append("## Метрики и анализ\n\n");
            for (com.example.sqlopt.ast.Operation operation : planResult.getOperations()) {
                response.append("### Операция: ").append(operation.getType()).append("\n");
                if (operation.getTableName() != null) {
                    response.append("- Таблица: ").append(operation.getTableName()).append("\n");
                }
                if (operation.getTableMetadata() != null) {
                    response.append("- Метаданные таблицы: ").append(operation.getTableMetadata()).append("\n");
                }
                if (operation.getStatistics() != null && !operation.getStatistics().isEmpty()) {
                    response.append("- Статистика:\n");
                    for (Map.Entry<String, String> stat : operation.getStatistics().entrySet()) {
                        response.append("  - ").append(stat.getKey()).append(": ").append(stat.getValue()).append("\n");
                    }
                }
                if (!operation.getKeys().isEmpty()) {
                    response.append("- Ключи: ").append(String.join(", ", operation.getKeys())).append("\n");
                }
                if (!operation.getConditions().isEmpty()) {
                    response.append("- Условия: ").append(String.join(", ", operation.getConditions())).append("\n");
                }
                if (!operation.getAdditionalInfo().isEmpty()) {
                    response.append("- Дополнительная информация:\n");
                    for (Map.Entry<String, Object> info : operation.getAdditionalInfo().entrySet()) {
                        response.append("  - ").append(info.getKey()).append(": ").append(info.getValue()).append("\n");
                    }
                }
                response.append("\n");
            }
        }
        
        // Добавляем метаданные таблиц только если есть подключение
        if (tablesMetadata != null && !tablesMetadata.isEmpty()) {
            response.append("## Метаданные таблиц\n\n");
            for (Map.Entry<String, Map<String, Object>> entry : tablesMetadata.entrySet()) {
                response.append("### Таблица: ").append(entry.getKey()).append("\n\n");
                Map<String, Object> metadata = entry.getValue();
                
                // Колонки
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) metadata.get("columns");
                if (columns != null && !columns.isEmpty()) {
                    response.append("#### Колонки\n\n");
                    for (Map<String, Object> column : columns) {
                        response.append("- ").append(column.get("name"))
                              .append(" (").append(column.get("type")).append(")");
                        if (column.get("nullable") != null) {
                            response.append(column.get("nullable").equals(true) ? " NULL" : " NOT NULL");
                        }
                        if (column.get("default") != null) {
                            response.append(" DEFAULT ").append(column.get("default"));
                        }
                        response.append("\n");
                    }
                    response.append("\n");
                }
                
                // Индексы
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> indexes = (List<Map<String, Object>>) metadata.get("indexes");
                if (indexes != null && !indexes.isEmpty()) {
                    response.append("#### Индексы\n\n");
                    for (Map<String, Object> index : indexes) {
                        response.append("- ").append(index.get("name"))
                              .append(" (").append(index.get("columns")).append(")");
                        if (index.get("unique") != null) {
                            response.append(index.get("unique").equals(true) ? " UNIQUE" : "");
                        }
                        response.append("\n");
                    }
                    response.append("\n");
                }
                
                // Статистика
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) metadata.get("statistics");
                if (stats != null && !stats.isEmpty()) {
                    response.append("#### Статистика\n\n");
                    for (Map.Entry<String, Object> stat : stats.entrySet()) {
                        if (stat.getValue() instanceof Map) {
                            response.append("- ").append(stat.getKey()).append(":\n");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> subStats = (Map<String, Object>) stat.getValue();
                            for (Map.Entry<String, Object> subStat : subStats.entrySet()) {
                                response.append("  - ").append(subStat.getKey())
                                      .append(": ").append(subStat.getValue()).append("\n");
                            }
                        } else {
                            response.append("- ").append(stat.getKey())
                                  .append(": ").append(stat.getValue()).append("\n");
                        }
                    }
                    response.append("\n");
                }
            }
        }

        return response.toString();
    }

    private List<String> extractTablesFromQuery(String query) {
        List<String> tables = new ArrayList<>();
        try {
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            net.sf.jsqlparser.statement.Statement statement = parserManager.parse(new StringReader(query));
            
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                SelectBody selectBody = selectStatement.getSelectBody();
                
                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    
                    // Получаем таблицы из FROM
                    FromItem fromItem = plainSelect.getFromItem();
                    if (fromItem instanceof Table) {
                        tables.add(((Table) fromItem).getName());
                    } else if (fromItem instanceof SubJoin) {
                        // Обрабатываем подзапросы с JOIN
                        SubJoin subJoin = (SubJoin) fromItem;
                        if (subJoin.getLeft() instanceof Table) {
                            tables.add(((Table) subJoin.getLeft()).getName());
                        }
                        for (Join join : subJoin.getJoinList()) {
                            if (join.getRightItem() instanceof Table) {
                                tables.add(((Table) join.getRightItem()).getName());
                            }
                        }
                    }
                    
                    // Получаем таблицы из JOIN
                    if (plainSelect.getJoins() != null) {
                        for (Join join : plainSelect.getJoins()) {
                            if (join.getRightItem() instanceof Table) {
                                tables.add(((Table) join.getRightItem()).getName());
                            }
                        }
                    }
                } else if (selectBody instanceof SetOperationList) {
                    // Обрабатываем UNION, INTERSECT, EXCEPT
                    SetOperationList setOpList = (SetOperationList) selectBody;
                    for (SelectBody selectBody2 : setOpList.getSelects()) {
                        if (selectBody2 instanceof PlainSelect) {
                            PlainSelect plainSelect = (PlainSelect) selectBody2;
                            if (plainSelect.getFromItem() instanceof Table) {
                                tables.add(((Table) plainSelect.getFromItem()).getName());
                            }
                        }
                    }
                }
            }
        } catch (JSQLParserException e) {
            log.warn("Ошибка при разборе SQL-запроса: {}", e.getMessage());
        }
        
        return tables;
    }

    private Map<String, Map<String, Object>> collectTableMetadata(Connection conn, List<String> tableNames) throws SQLException {
        Map<String, Map<String, Object>> tablesMetadata = new HashMap<>();
        
        for (String tableName : tableNames) {
            Map<String, Object> metadata = new HashMap<>();
            
            // Получаем информацию о колонках
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("size", rs.getInt("COLUMN_SIZE"));
                    column.put("nullable", rs.getBoolean("IS_NULLABLE"));
                    column.put("default", rs.getString("COLUMN_DEF"));
                    columns.add(column);
                }
            }
            metadata.put("columns", columns);
            
            // Получаем информацию об индексах
            List<Map<String, Object>> indexes = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
                while (rs.next()) {
                    Map<String, Object> index = new HashMap<>();
                    index.put("name", rs.getString("INDEX_NAME"));
                    index.put("columns", rs.getString("COLUMN_NAME"));
                    index.put("unique", rs.getBoolean("NON_UNIQUE"));
                    index.put("type", rs.getShort("TYPE"));
                    indexes.add(index);
                }
            }
            metadata.put("indexes", indexes);
            
            // Получаем статистику таблицы
            Map<String, Object> statistics = new HashMap<>();
            try (java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT reltuples, relpages FROM pg_class WHERE relname = '" + tableName + "'")) {
                if (rs.next()) {
                    statistics.put("estimated_rows", rs.getDouble("reltuples"));
                    statistics.put("pages", rs.getInt("relpages"));
                }
            } catch (SQLException e) {
                log.warn("Не удалось получить статистику для таблицы {}: {}", tableName, e.getMessage());
            }
            
            // Получаем размер таблицы
            try (java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT pg_size_pretty(pg_total_relation_size('" + tableName + "')) as size")) {
                if (rs.next()) {
                    statistics.put("total_size", rs.getString("size"));
                }
            } catch (SQLException e) {
                log.warn("Не удалось получить размер таблицы {}: {}", tableName, e.getMessage());
            }
            
            // Получаем статистику по колонкам
            try (java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT attname, n_distinct, null_frac FROM pg_stats WHERE tablename = '" + tableName + "'")) {
                Map<String, Map<String, Object>> columnStats = new HashMap<>();
                while (rs.next()) {
                    Map<String, Object> colStats = new HashMap<>();
                    colStats.put("n_distinct", rs.getDouble("n_distinct"));
                    colStats.put("null_frac", rs.getDouble("null_frac"));
                    columnStats.put(rs.getString("attname"), colStats);
                }
                statistics.put("column_stats", columnStats);
            } catch (SQLException e) {
                log.warn("Не удалось получить статистику колонок для таблицы {}: {}", tableName, e.getMessage());
            }
            
            metadata.put("statistics", statistics);
            tablesMetadata.put(tableName, metadata);
        }
        
        return tablesMetadata;
    }
}
