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
import com.google.gson.Gson;

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
    private final SimpMessagingTemplate messagingTemplate;
    private final ASTService astService;

    private static class LLMResponse {
        private String optimizedSql;
        private String optimizationRationale;
        private String performanceImpact;
        private String potentialRisks;

        public String getOptimizedSql() {
            return optimizedSql;
        }

        public void setOptimizedSql(String optimizedSql) {
            this.optimizedSql = optimizedSql;
        }

        public String getOptimizationRationale() {
            return optimizationRationale;
        }

        public void setOptimizationRationale(String optimizationRationale) {
            this.optimizationRationale = optimizationRationale;
        }

        public String getPerformanceImpact() {
            return performanceImpact;
        }

        public void setPerformanceImpact(String performanceImpact) {
            this.performanceImpact = performanceImpact;
        }

        public String getPotentialRisks() {
            return potentialRisks;
        }

        public void setPotentialRisks(String potentialRisks) {
            this.potentialRisks = potentialRisks;
        }
    }

    private String formatPrompt(String promptTemplate, String query, QueryPlanResult planResult, Map<String, Map<String, Object>> tablesMetadata) {
        StringBuilder prompt = new StringBuilder(promptTemplate);
        
        // Добавляем SQL-запрос
        prompt.append("\n\nSQL-запрос для оптимизации:\n```sql\n").append(query).append("\n```\n");
        
        // Если есть план выполнения, добавляем его
        if (planResult != null && !planResult.getOperations().isEmpty()) {
            prompt.append("\nПлан выполнения запроса:\n");
            for (com.example.sqlopt.ast.Operation operation : planResult.getOperations()) {
                prompt.append("- ").append(operation.getType());
                if (operation.getTableName() != null) {
                    prompt.append(" по таблице ").append(operation.getTableName());
                }
                prompt.append("\n");
            }
        }
        
        // Если есть метаданные таблиц, добавляем их
        if (tablesMetadata != null && !tablesMetadata.isEmpty()) {
            prompt.append("\nМетаданные таблиц:\n");
            for (Map.Entry<String, Map<String, Object>> entry : tablesMetadata.entrySet()) {
                prompt.append("\nТаблица: ").append(entry.getKey()).append("\n");
                Map<String, Object> metadata = entry.getValue();
                
                // Колонки
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) metadata.get("columns");
                if (columns != null && !columns.isEmpty()) {
                    prompt.append("Колонки:\n");
                    for (Map<String, Object> column : columns) {
                        prompt.append("- ").append(column.get("name"))
                              .append(" (").append(column.get("type")).append(")");
                        if (column.get("nullable") != null) {
                            prompt.append(column.get("nullable").equals(true) ? " NULL" : " NOT NULL");
                        }
                        prompt.append("\n");
                    }
                }
                
                // Индексы
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> indexes = (List<Map<String, Object>>) metadata.get("indexes");
                if (indexes != null && !indexes.isEmpty()) {
                    prompt.append("Индексы:\n");
                    for (Map<String, Object> index : indexes) {
                        prompt.append("- ").append(index.get("name"))
                              .append(" (").append(index.get("columns")).append(")");
                        if (index.get("unique") != null) {
                            prompt.append(index.get("unique").equals(true) ? " UNIQUE" : "");
                        }
                        prompt.append("\n");
                    }
                }
            }
        }
        
        return prompt.toString();
    }

    private com.example.sqlopt.ast.QueryPlanResult convertToAstQueryPlanResult(QueryPlanResult result) {
        com.example.sqlopt.ast.QueryPlanResult astResult = new com.example.sqlopt.ast.QueryPlanResult();
        astResult.setOperations(result.getOperations());
        astResult.setCost(result.getCost());
        astResult.setPlanningTimeMs(result.getPlanningTimeMs());
        astResult.setExecutionTimeMs(result.getExecutionTimeMs());
        return astResult;
    }

    private QueryPlanResult convertFromAstQueryPlanResult(com.example.sqlopt.ast.QueryPlanResult astResult) {
        QueryPlanResult result = new QueryPlanResult();
        result.setOperations(astResult.getOperations());
        result.setCost(astResult.getCost());
        result.setPlanningTimeMs(astResult.getPlanningTimeMs());
        result.setExecutionTimeMs(astResult.getExecutionTimeMs());
        return result;
    }

    private ExecutionResult executeExplainAnalyze(Connection connection, String query) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN ANALYZE " + query)) {
            StringBuilder output = new StringBuilder();
            while (rs.next()) {
                output.append(rs.getString(1)).append("\n");
            }
            String explainOutput = output.toString();
            
            // Извлекаем время выполнения
            Pattern pattern = Pattern.compile("Execution Time: (\\d+\\.\\d+) ms");
            Matcher matcher = pattern.matcher(explainOutput);
            if (matcher.find()) {
                long executionTime = (long) Double.parseDouble(matcher.group(1));
                return new ExecutionResult(executionTime, explainOutput);
            }
            return new ExecutionResult(-1, explainOutput);
        }
    }

    private String formatOptimizationResponse(
            String optimizedQuery, 
            com.example.sqlopt.ast.QueryPlanResult planResult, 
            Map<String, Map<String, Object>> tablesMetadata,
            String optimizationRationale,
            String performanceImpact,
            String potentialRisks,
            ExecutionResult originalExecution,
            ExecutionResult optimizedExecution,
            String originalQuery) {
        
        StringBuilder response = new StringBuilder();
        
        // Краткая шапка с основным выводом
        response.append("# Оптимизация SQL-запроса\n\n");
        if (optimizedExecution != null && originalExecution != null) {
            double timeImprovement = ((double) (originalExecution.getExecutionTime() - optimizedExecution.getExecutionTime()) / originalExecution.getExecutionTime()) * 100;
            response.append(String.format("**Улучшение производительности: %.2f%%**\n\n", timeImprovement));
        }
        
        // Информация о запросе
        response.append("## Информация о запросе\n\n");
        response.append("<details>\n<summary>Детали запроса</summary>\n\n");
        
        // Сравнение планов выполнения
        if (originalExecution != null && originalExecution.getExplainOutput() != null) {
            response.append("### Сравнение планов выполнения\n\n");
            response.append("<div class='plan-comparison'>\n");
            response.append("<table class='plan-table'>\n");
            response.append("<tr><th>Метрика</th><th>Исходный запрос</th><th>Оптимизированный запрос</th><th>Изменение</th></tr>\n");
            
            // Время выполнения
            response.append("<tr>\n");
            response.append("<td>Время выполнения</td>\n");
            response.append("<td>").append(originalExecution.getExecutionTime()).append(" мс</td>\n");
            if (optimizedExecution != null) {
                response.append("<td>").append(optimizedExecution.getExecutionTime()).append(" мс</td>\n");
                double timeImprovement = ((double) (originalExecution.getExecutionTime() - optimizedExecution.getExecutionTime()) / originalExecution.getExecutionTime()) * 100;
                response.append("<td class='").append(timeImprovement > 0 ? "improvement" : "degradation").append("'>")
                       .append(String.format("%.2f", timeImprovement)).append("%</td>\n");
            } else {
                response.append("<td>Нет данных</td>\n");
                response.append("<td>Нет данных</td>\n");
            }
            response.append("</tr>\n");
            response.append("</table>\n");
            response.append("</div>\n\n");
            
            // График сравнения
            response.append("<div class='chart-container'>\n");
            response.append("<canvas id='performanceChart'></canvas>\n");
            response.append("</div>\n\n");
        }
        
        // Детальные планы выполнения
        response.append("### Детальные планы выполнения\n\n");
        response.append("<details>\n<summary>Исходный план</summary>\n\n");
        response.append("```sql\n");
        response.append(originalExecution != null ? originalExecution.getExplainOutput() : "Нет данных");
        response.append("\n```\n\n");
        response.append("</details>\n\n");
        
        response.append("<details>\n<summary>Оптимизированный план</summary>\n\n");
        response.append("```sql\n");
        response.append(optimizedExecution != null ? optimizedExecution.getExplainOutput() : "Нет данных");
        response.append("\n```\n\n");
        response.append("</details>\n\n");
        
        // Сравнение SQL-запросов
        response.append("### Сравнение SQL-запросов\n\n");
        response.append("<details>\n<summary>Показать запросы</summary>\n\n");
        response.append("#### Исходный запрос\n");
        response.append("```sql\n");
        response.append(originalQuery);
        response.append("\n```\n\n");
        
        response.append("#### Оптимизированный запрос\n");
        response.append("```sql\n");
        response.append(optimizedQuery);
        response.append("\n```\n");
        response.append("</details>\n\n");
        
        // Метаданные таблиц
        if (tablesMetadata != null && !tablesMetadata.isEmpty()) {
            response.append("### Метаданные таблиц\n\n");
            response.append("<details>\n<summary>Показать метаданные</summary>\n\n");
            for (Map.Entry<String, Map<String, Object>> entry : tablesMetadata.entrySet()) {
                response.append("#### Таблица: ").append(entry.getKey()).append("\n\n");
                Map<String, Object> metadata = entry.getValue();
                
                // Колонки
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) metadata.get("columns");
                if (columns != null && !columns.isEmpty()) {
                    response.append("**Колонки:**\n\n");
                    response.append("| Имя | Тип | Nullable | Default |\n");
                    response.append("|-----|-----|----------|--------|\n");
                    for (Map<String, Object> column : columns) {
                        response.append("| ").append(column.get("name"))
                              .append(" | ").append(column.get("type"))
                              .append(" | ").append(column.get("nullable"))
                              .append(" | ").append(column.get("default"))
                              .append(" |\n");
                    }
                    response.append("\n");
                }
                
                // Индексы
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> indexes = (List<Map<String, Object>>) metadata.get("indexes");
                if (indexes != null && !indexes.isEmpty()) {
                    response.append("**Индексы:**\n\n");
                    response.append("| Имя | Колонки | Уникальный |\n");
                    response.append("|-----|---------|------------|\n");
                    for (Map<String, Object> index : indexes) {
                        response.append("| ").append(index.get("name"))
                              .append(" | ").append(index.get("columns"))
                              .append(" | ").append(index.get("unique"))
                              .append(" |\n");
                    }
                    response.append("\n");
                }
                
                // Статистика
                @SuppressWarnings("unchecked")
                Map<String, Object> statistics = (Map<String, Object>) metadata.get("statistics");
                if (statistics != null && !statistics.isEmpty()) {
                    response.append("**Статистика:**\n\n");
                    response.append("- Оценочное количество строк: ").append(statistics.get("estimated_rows")).append("\n");
                    response.append("- Размер таблицы: ").append(statistics.get("total_size")).append("\n");
                    response.append("- Количество страниц: ").append(statistics.get("pages")).append("\n\n");
                }
            }
            response.append("</details>\n\n");
        }
        
        response.append("</details>\n\n");
        
        // Обоснование оптимизации
        response.append("## Обоснование оптимизации\n\n");
        response.append("<details>\n<summary>Показать обоснование</summary>\n\n");
        response.append(optimizationRationale);
        response.append("\n\n");
        response.append("</details>\n\n");
        
        // Оценка улучшения
        response.append("## Оценка улучшения\n\n");
        response.append("<details>\n<summary>Показать оценку</summary>\n\n");
        response.append(performanceImpact);
        response.append("\n\n");
        response.append("</details>\n\n");
        
        // Потенциальные риски
        response.append("## Потенциальные риски\n\n");
        response.append("<details>\n<summary>Показать риски</summary>\n\n");
        response.append(potentialRisks);
        response.append("\n\n");
        response.append("</details>\n");
        
        return response.toString();
    }

    @Transactional
    public Mono<SqlQueryResponse> optimizeQuery(Long userId, SqlQueryRequest request) {
        log.info("Starting query optimization for userId={}, chatId={}, query={}, llm={}, isMPP={}",
                userId, request.getChatId(), request.getQuery(), request.getLlm(), request.isMPP());

        AtomicReference<Map<String, Map<String, Object>>> tablesMetadataRef = new AtomicReference<>(null);
        AtomicReference<com.example.sqlopt.ast.QueryPlanResult> originalPlanResultRef = new AtomicReference<>(null);
        AtomicReference<ExecutionResult> originalExecutionRef = new AtomicReference<>(null);
        AtomicReference<ExecutionResult> optimizedExecutionRef = new AtomicReference<>(null);

        return Mono.just(request)
                .flatMap(req -> {
                    // Проверяем существование чата
                    Chat chat = chatRepository.findById(req.getChatId())
                            .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

                    // Создаем сообщение от пользователя
                    Message userMessage = Message.builder()
                            .chat(chat)
                            .content(req.getQuery())
                            .fromUser(true)
                            .createdAt(LocalDateTime.now())
                            .build();

                    // Создаем запись SQL-запроса
                    SqlQuery sqlQuery = SqlQuery.builder()
                            .message(userMessage)
                            .originalQuery(req.getQuery())
                            .createdAt(LocalDateTime.now())
                            .build();

                    // Если есть подключение к БД, сохраняем его
                    if (req.getDatabaseConnectionId() != null) {
                        DatabaseConnection dbConnection = databaseConnectionRepository
                                .findById(req.getDatabaseConnectionId())
                                .orElseThrow(() -> new ResourceNotFoundException("Database connection not found"));
                        sqlQuery.setDatabaseConnection(dbConnection);
                    }

                    // Сохраняем сообщение пользователя и SQL-запрос в одной транзакции
                    userMessage = messageRepository.save(userMessage);
                    sqlQuery.setMessage(userMessage);
                    sqlQuery = sqlQueryRepository.save(sqlQuery);

                    return Mono.just(sqlQuery);
                })
                .flatMap(sqlQuery -> {
                    String promptTemplate = getDefaultPromptTemplate(request.isMPP(), 
                            request.getDatabaseConnectionId() != null);

                    String prompt;

                    if (request.getDatabaseConnectionId() != null) {
                        try {
                            DatabaseConnection dbConnection = sqlQuery.getDatabaseConnection();
                            
                            // Анализируем план исходного запроса
                            originalPlanResultRef.set(astService.analyzeQueryPlan(request.getQuery()));
                            log.info("Original query plan: {}", originalPlanResultRef.get());
                            
                            // Получаем EXPLAIN ANALYZE для исходного запроса
                            Connection connection = databaseConnectionService.getConnection(dbConnection.getId());
                            originalExecutionRef.set(executeExplainAnalyze(connection, request.getQuery()));
                            log.info("Original query execution: {}", originalExecutionRef.get().getExplainOutput());
                            
                            // Собираем метаданные таблиц
                            List<String> tables = extractTablesFromQuery(request.getQuery());
                            if (!tables.isEmpty()) {
                                tablesMetadataRef.set(collectTableMetadata(connection, tables));
                                log.info("Collected metadata for tables: {}", tablesMetadataRef.get());
                            }
                            
                            String dbInfo = getDatabaseInfo(dbConnection);
                            log.info("Database connection info: {}", dbInfo);
                            
                            prompt = formatPrompt(promptTemplate, request.getQuery(), originalPlanResultRef.get(), tablesMetadataRef.get());
                        } catch (Exception e) {
                            log.warn("Failed to collect metadata or analyze plan: {}", e.getMessage());
                            prompt = formatPrompt(promptTemplate, request.getQuery(), null, null);
                        }
                    } else {
                        prompt = formatPrompt(promptTemplate, request.getQuery(), null, null);
                    }

                    return llmService.optimizeSqlQuery(prompt, request.getLlm(), promptTemplate)
                            .map(llmResponse -> {
                                try {
                                    LLMResponse parsedResponse = parseLLMResponse(llmResponse);
                                    
                                    sqlQuery.setOptimizedQuery(parsedResponse.getOptimizedSql());
                                    sqlQuery.setOptimizationRationale(parsedResponse.getOptimizationRationale());
                                    sqlQuery.setPerformanceImpact(parsedResponse.getPerformanceImpact());
                                    sqlQuery.setPotentialRisks(parsedResponse.getPotentialRisks());

                                    if (request.getDatabaseConnectionId() != null) {
                                        try {
                                            DatabaseConnection dbConnection = sqlQuery.getDatabaseConnection();
                                            Connection connection = databaseConnectionService.getConnection(dbConnection.getId());
                                            
                                            // Получаем EXPLAIN ANALYZE для оптимизированного запроса
                                            try {
                                                optimizedExecutionRef.set(executeExplainAnalyze(connection, parsedResponse.getOptimizedSql()));
                                                log.info("Optimized query execution: {}", optimizedExecutionRef.get().getExplainOutput());
                                            } catch (SQLException e) {
                                                log.warn("Failed to execute optimized query: {}", e.getMessage());
                                                // Продолжаем выполнение, даже если оптимизированный запрос не удалось выполнить
                                            }
                                            
                                            // Анализируем план оптимизированного запроса
                                            try {
                                                com.example.sqlopt.ast.QueryPlanResult optimizedPlanResult = 
                                                    astService.analyzeQueryPlan(parsedResponse.getOptimizedSql());
                                                
                                                sqlQuery.setOriginalPlan(originalPlanResultRef.get());
                                                sqlQuery.setOptimizedPlan(optimizedPlanResult);
                                            } catch (Exception e) {
                                                log.warn("Failed to analyze optimized query plan: {}", e.getMessage());
                                                // Продолжаем выполнение, даже если не удалось проанализировать план
                                            }
                                            
                                            if (tablesMetadataRef.get() != null) {
                                                sqlQuery.setTablesMetadata(tablesMetadataRef.get());
                                            }
                                            
                                            log.debug("Successfully analyzed and saved query plans and metadata");
                                        } catch (Exception e) {
                                            log.warn("Failed to analyze optimized query plan: {}", e.getMessage());
                                        }
                                    }

                                    return sqlQuery;
                                } catch (Exception e) {
                                    log.error("Error parsing LLM response: {}", e.getMessage());
                                    sqlQuery.setOptimizedQuery(request.getQuery());
                                    sqlQuery.setOptimizationRationale("Не удалось получить оптимизированную версию запроса. Пожалуйста, попробуйте позже.");
                                    sqlQuery.setPerformanceImpact("Нет данных");
                                    sqlQuery.setPotentialRisks("Нет данных");
                                    return sqlQuery;
                                }
                            })
                            .onErrorResume(e -> {
                                log.error("Error during LLM optimization: {}", e.getMessage());
                                sqlQuery.setOptimizedQuery(request.getQuery());
                                sqlQuery.setOptimizationRationale("Сервис оптимизации временно недоступен. Пожалуйста, попробуйте позже.");
                                sqlQuery.setPerformanceImpact("Нет данных");
                                sqlQuery.setPotentialRisks("Нет данных");
                                return Mono.just(sqlQuery);
                            });
                })
                .flatMap(sqlQuery -> {
                    try {
                        SqlQuery savedQuery = sqlQueryRepository.save(sqlQuery);

                        String formattedResponse = formatOptimizationResponse(
                            sqlQuery.getOptimizedQuery(),
                            sqlQuery.getOptimizedPlan(),
                            tablesMetadataRef.get(),
                            sqlQuery.getOptimizationRationale(),
                            sqlQuery.getPerformanceImpact(),
                            sqlQuery.getPotentialRisks(),
                            originalExecutionRef.get(),
                            optimizedExecutionRef.get(),
                            sqlQuery.getOriginalQuery()
                        );

                        Message llmMessage = Message.builder()
                            .chat(sqlQuery.getMessage().getChat())
                            .content(formattedResponse)
                            .fromUser(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                        llmMessage = messageRepository.save(llmMessage);

                        String destination = "/topic/chat/" + request.getChatId();
                        MessageDto messageDto = MessageDto.builder()
                            .id(llmMessage.getId())
                            .content(formattedResponse)
                            .fromUser(false)
                            .createdAt(llmMessage.getCreatedAt())
                            .chatId(request.getChatId())
                            .build();
                        messagingTemplate.convertAndSend(destination, messageDto);
                        log.info("Successfully sent message to {}: id={}", destination, llmMessage.getId());

                        return Mono.just(mapToResponse(savedQuery));
                    } catch (Exception e) {
                        log.error("Error saving optimization results: {}", e.getMessage());
                        throw new RuntimeException("Failed to save optimization results", e);
                    }
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
                .tablesMetadata(sqlQuery.getTablesMetadata())
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
        
        if (hasConnection) {
            template.append("У тебя есть доступ к базе данных и возможность анализировать план выполнения запроса.\n\n");
        } else {
            template.append("У тебя нет доступа к базе данных. Оптимизация будет выполнена на основе общих принципов и лучших практик SQL.\n\n");
        }
        
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
        if (hasConnection) {
            template.append("[оценка ожидаемого улучшения производительности на основе анализа плана выполнения]\n\n");
        } else {
            template.append("[оценка ожидаемого улучшения производительности на основе общих принципов SQL]\n\n");
        }
        
        template.append("## Потенциальные риски\n\n");
        if (hasConnection) {
            template.append("[описание возможных рисков и побочных эффектов на основе анализа плана выполнения]\n\n");
        } else {
            template.append("[описание возможных рисков и побочных эффектов на основе общих принципов SQL]\n\n");
        }

        return template.toString();
    }

    private LLMResponse parseLLMResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new ApiException("Пустой ответ от LLM", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        LLMResponse result = new LLMResponse();
        
        // Разбиваем ответ на секции
        String[] sections = response.split("##");
        
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            
            if (section.contains("Оптимизированный SQL-запрос")) {
                String sql = extractSQLFromSection(section);
                if (sql != null && !sql.trim().isEmpty()) {
                    try {
                        // Валидируем SQL-запрос
                        validateSqlQuery(sql);
                        result.setOptimizedSql(sql);
                    } catch (Exception e) {
                        log.warn("Невалидный SQL в ответе LLM: {}", e.getMessage());
                    }
                }
            } else if (section.contains("Обоснование оптимизации") || section.contains("Обоснование изменений")) {
                String rationale = extractContentFromSection(section);
                if (rationale != null && !rationale.trim().isEmpty()) {
                    result.setOptimizationRationale(rationale);
                }
            } else if (section.contains("Оценка улучшения")) {
                String impact = extractContentFromSection(section);
                if (impact != null && !impact.trim().isEmpty()) {
                    result.setPerformanceImpact(impact);
                }
            } else if (section.contains("Потенциальные риски")) {
                String risks = extractContentFromSection(section);
                if (risks != null && !risks.trim().isEmpty()) {
                    result.setPotentialRisks(risks);
                }
            }
        }
        
        // Если не нашли оптимизированный SQL, ищем его в тексте
        if (result.getOptimizedSql() == null || result.getOptimizedSql().trim().isEmpty()) {
            String sql = extractSQLFromText(response);
            if (sql != null && !sql.trim().isEmpty()) {
                try {
                    validateSqlQuery(sql);
                    result.setOptimizedSql(sql);
                } catch (Exception e) {
                    log.warn("Невалидный SQL в тексте ответа: {}", e.getMessage());
                    throw new ApiException("Не удалось извлечь валидный SQL-запрос из ответа LLM", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                throw new ApiException("Не удалось найти SQL-запрос в ответе LLM", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        
        // Устанавливаем стандартные значения для отсутствующих секций
        if (result.getOptimizationRationale() == null || result.getOptimizationRationale().trim().isEmpty()) {
            result.setOptimizationRationale("Оптимизация выполнена на основе общих принципов SQL без анализа плана выполнения.");
        }
        
        if (result.getPerformanceImpact() == null || result.getPerformanceImpact().trim().isEmpty()) {
            result.setPerformanceImpact("Без анализа плана выполнения невозможно точно оценить улучшение производительности.");
        }
        
        if (result.getPotentialRisks() == null || result.getPotentialRisks().trim().isEmpty()) {
            result.setPotentialRisks("Без анализа плана выполнения невозможно точно оценить потенциальные риски.");
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

    private String extractSQLFromText(String text) {
        // Ищем SQL в тексте между ```sql и ```
        int startIndex = text.indexOf("```sql");
        if (startIndex == -1) {
            // Если не нашли ```sql, ищем просто SQL-запрос
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.trim().toUpperCase().startsWith("SELECT")) {
                    return line.trim();
                }
            }
            return null;
        }
        
        startIndex += 6; // длина ```sql
        int endIndex = text.indexOf("```", startIndex);
        if (endIndex == -1) return null;
        
        return text.substring(startIndex, endIndex).trim();
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

    private String getDatabaseInfo(DatabaseConnection dbConnection) {
        return String.format("Database: %s, Host: %s, Port: %d",
            dbConnection.getDatabaseName(),
            dbConnection.getHost(),
            dbConnection.getPort());
    }
}
