package com.example.backend.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import com.example.backend.model.ExecutionResult;
import com.example.backend.util.QueryPlanAnalyzer;
import com.example.sqlopt.ast.TableCollector;
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
            com.example.backend.model.ExecutionResult originalExecution,
            com.example.backend.model.ExecutionResult optimizedExecution,
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
        }

        // Детальные планы выполнения
        response.append("### Детальные планы выполнения\n\n");
        
        // Исходный план
        response.append("#### Исходный план\n\n");
        response.append("```sql\n");
        if (originalExecution != null && originalExecution.getExplainOutput() != null) {
            response.append(originalExecution.getExplainOutput());
        } else {
            response.append("Нет данных о плане выполнения");
        }
        response.append("\n```\n\n");

        // Оптимизированный план
        response.append("#### Оптимизированный план\n\n");
        response.append("```sql\n");
        if (optimizedExecution != null && optimizedExecution.getExplainOutput() != null) {
            response.append(optimizedExecution.getExplainOutput());
        } else {
            response.append("Нет данных о плане выполнения");
        }
        response.append("\n```\n\n");

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
                String tableName = entry.getKey();
                Map<String, Object> metadata = entry.getValue();

                response.append("#### Таблица: ").append(tableName).append("\n\n");

                // Колонки
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) metadata.get("columns");
                if (columns != null && !columns.isEmpty()) {
                    response.append("**Колонки:**\n\n");
                    response.append("| Имя | Тип | Nullable | Default | Комментарий |\n");
                    response.append("|-----|-----|----------|---------|-------------|\n");
                    for (Map<String, Object> column : columns) {
                        response.append("| ").append(column.get("name"))
                              .append(" | ").append(column.get("type"))
                              .append(" | ").append(column.get("nullable"))
                              .append(" | ").append(column.get("default"))
                              .append(" | ").append(column.get("comment"))
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

    private Map<String, Map<String, Object>> extractTablesMetadata(Connection connection, String query) throws SQLException {
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        try {
            // Получаем список таблиц из запроса
            Set<String> tables = extractTablesFromQuery(query);
            
            for (String table : tables) {
                Map<String, Object> tableInfo = new HashMap<>();
                DatabaseMetaData dbMetaData = connection.getMetaData();
                
                // Получаем информацию о таблице
                try (ResultSet rs = dbMetaData.getTables(null, null, table, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        tableInfo.put("type", rs.getString("TABLE_TYPE"));
                        tableInfo.put("remarks", rs.getString("REMARKS"));
                    }
                }
                
                // Получаем информацию о колонках
                List<Map<String, Object>> columns = new ArrayList<>();
                try (ResultSet rs = dbMetaData.getColumns(null, null, table, null)) {
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
                tableInfo.put("columns", columns);
                
                // Получаем информацию об индексах
                List<Map<String, Object>> indexes = new ArrayList<>();
                try (ResultSet rs = dbMetaData.getIndexInfo(null, null, table, false, false)) {
                    while (rs.next()) {
                        Map<String, Object> index = new HashMap<>();
                        index.put("name", rs.getString("INDEX_NAME"));
                        index.put("column", rs.getString("COLUMN_NAME"));
                        index.put("unique", !rs.getBoolean("NON_UNIQUE"));
                        indexes.add(index);
                    }
                }
                tableInfo.put("indexes", indexes);
                
                metadata.put(table, tableInfo);
            }
        } catch (SQLException e) {
            log.error("Error extracting table metadata: {}", e.getMessage());
            throw e;
        }
        return metadata;
    }

    private Set<String> extractTablesFromQuery(String query) {
        Set<String> tables = new HashSet<>();
        // Простой парсинг для извлечения имен таблиц
        // Это базовая реализация, которая может быть улучшена
        String[] words = query.split("\\s+");
        boolean isFrom = false;
        for (String word : words) {
            if (word.equalsIgnoreCase("FROM") || word.equalsIgnoreCase("JOIN")) {
                isFrom = true;
                continue;
            }
            if (isFrom && !word.equalsIgnoreCase("WHERE") && !word.equalsIgnoreCase("GROUP") 
                && !word.equalsIgnoreCase("ORDER") && !word.equalsIgnoreCase("HAVING")) {
                tables.add(word.replaceAll("[^a-zA-Z0-9_]", ""));
                isFrom = false;
            }
        }
        return tables;
    }

    @Transactional
    public Mono<SqlQueryResponse> optimizeQuery(Long userId, SqlQueryRequest request) {
        log.info("Starting query optimization for userId={}, chatId={}, query={}, llm={}, isMPP={}",
                userId, request.getChatId(), request.getQuery(), request.getLlm(), request.isMPP());

        AtomicReference<Map<String, Map<String, Object>>> tablesMetadataRef = new AtomicReference<>(null);
        AtomicReference<QueryPlanAnalyzer.PlanMetrics> originalPlanMetricsRef = new AtomicReference<>(null);
        AtomicReference<QueryPlanAnalyzer.PlanMetrics> optimizedPlanMetricsRef = new AtomicReference<>(null);

        return Mono.just(request)
                .flatMap(req -> {
                    // Получаем последнее сообщение из чата
                    Optional<Message> messageOpt = messageRepository.findFirstByChatIdOrderByCreatedAtDesc(req.getChatId());
                    if (messageOpt.isEmpty()) {
                        return Mono.error(new ResourceNotFoundException("Chat not found with ID: " + req.getChatId()));
                    }
                    Message message = messageOpt.get();
                    SqlQuery sqlQuery = new SqlQuery();
                    sqlQuery.setOriginalQuery(req.getQuery());
                    sqlQuery.setMessage(message);
                    return Mono.just(sqlQuery);
                })
                .flatMap(sqlQuery -> {
                    // Анализируем исходный план выполнения
                    if (request.getDatabaseConnectionId() != null) {
                        try {
                            Connection connection = databaseConnectionService.getConnection(request.getDatabaseConnectionId());
                            // Получаем план выполнения исходного запроса
                            ExecutionResult originalExecution = executeExplainAnalyze(connection, request.getQuery());
                            originalPlanMetricsRef.set(QueryPlanAnalyzer.analyzePlan(connection, request.getQuery(), request.isMPP()));
                            sqlQuery.setOriginalPlan(QueryPlanAnalyzer.toQueryPlanResult(originalPlanMetricsRef.get()));

                            // Получаем метаданные таблиц
                            tablesMetadataRef.set(extractTablesMetadata(connection, request.getQuery()));

                            return Mono.just(sqlQuery);
                        } catch (SQLException e) {
                            log.error("Error executing explain analyze: {}", e.getMessage());
                            return Mono.error(new ApiException("Failed to analyze query plan", HttpStatus.INTERNAL_SERVER_ERROR));
                        }
                    }
                    return Mono.just(sqlQuery);
                })
                .flatMap(sqlQuery -> {
                    // Получаем шаблон промпта
                    String promptTemplate = getDefaultPromptTemplate(request.isMPP(), request.getDatabaseConnectionId() != null);

                    // Форматируем промпт
                    String formattedPrompt = formatPrompt(
                            promptTemplate,
                            request.getQuery(),
                            sqlQuery.getOriginalPlan(),
                            tablesMetadataRef.get()
                    );

                    // Отправляем запрос к LLM
                    return llmService.optimizeSqlQuery(formattedPrompt, request.getLlm(), promptTemplate)
                            .flatMap(llmResponse -> {
                                try {
                                    LLMResponse parsedResponse = parseLLMResponse(llmResponse);
                                    sqlQuery.setOptimizedQuery(parsedResponse.getOptimizedSql());
                                    sqlQuery.setOptimizationRationale(parsedResponse.getOptimizationRationale());
                                    sqlQuery.setPerformanceImpact(parsedResponse.getPerformanceImpact());
                                    sqlQuery.setPotentialRisks(parsedResponse.getPotentialRisks());

                                    // Анализируем оптимизированный план выполнения
                                    if (request.getDatabaseConnectionId() != null) {
                                        try {
                                            Connection connection = databaseConnectionService.getConnection(request.getDatabaseConnectionId());
                                            // Получаем план выполнения оптимизированного запроса
                                            ExecutionResult optimizedExecution = executeExplainAnalyze(connection, parsedResponse.getOptimizedSql());
                                            optimizedPlanMetricsRef.set(QueryPlanAnalyzer.analyzePlan(connection, parsedResponse.getOptimizedSql(), request.isMPP()));
                                            sqlQuery.setOptimizedPlan(QueryPlanAnalyzer.toQueryPlanResult(optimizedPlanMetricsRef.get()));

                                            // Сравниваем планы выполнения
                                            String comparison = QueryPlanAnalyzer.comparePlans(
                                                    originalPlanMetricsRef.get(),
                                                    optimizedPlanMetricsRef.get()
                                            );

                                            // Обновляем обоснование оптимизации
                                            String updatedRationale = sqlQuery.getOptimizationRationale() + "\n\n" + comparison;
                                            sqlQuery.setOptimizationRationale(updatedRationale);

                                            if (tablesMetadataRef.get() != null) {
                                                sqlQuery.setTablesMetadata(tablesMetadataRef.get());
                                            }

                                            log.debug("Successfully analyzed and compared query plans");
                                            return Mono.just(sqlQuery);
                                        } catch (SQLException e) {
                                            log.error("Error executing explain analyze: {}", e.getMessage());
                                            return Mono.error(new ApiException("Failed to analyze optimized query plan", HttpStatus.INTERNAL_SERVER_ERROR));
                                        }
                                    }

                                    return Mono.just(sqlQuery);
                                } catch (Exception e) {
                                    log.error("Error parsing LLM response: {}", e.getMessage());
                                    sqlQuery.setOptimizedQuery(request.getQuery());
                                    sqlQuery.setOptimizationRationale("Не удалось получить оптимизированную версию запроса. Пожалуйста, попробуйте позже.");
                                    sqlQuery.setPerformanceImpact("Нет данных");
                                    sqlQuery.setPotentialRisks("Нет данных");
                                    return Mono.just(sqlQuery);
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
                        SqlQuery savedQuery = sqlQueryRepository.save((SqlQuery) sqlQuery);

                        String formattedResponse = formatOptimizationResponse(
                            savedQuery.getOptimizedQuery(),
                            savedQuery.getOptimizedPlan(),
                            tablesMetadataRef.get(),
                            savedQuery.getOptimizationRationale(),
                            savedQuery.getPerformanceImpact(),
                            savedQuery.getPotentialRisks(),
                            QueryPlanAnalyzer.toExecutionResult(originalPlanMetricsRef.get()),
                            QueryPlanAnalyzer.toExecutionResult(optimizedPlanMetricsRef.get()),
                            savedQuery.getOriginalQuery()
                        );

                        Message llmMessage = Message.builder()
                            .chat(savedQuery.getMessage().getChat())
                            .content(formattedResponse)
                            .fromUser(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                        llmMessage = messageRepository.save(llmMessage);

                        savedQuery.setMessage(llmMessage);
                        savedQuery = sqlQueryRepository.save(savedQuery);

                        return Mono.just(mapToResponse(savedQuery));
                    } catch (Exception e) {
                        log.error("Error saving query: {}", e.getMessage());
                        return Mono.error(new ApiException("Failed to save query", HttpStatus.INTERNAL_SERVER_ERROR));
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

    private SqlQueryResponse mapToResponse(SqlQuery sqlQuery) {
        SqlQueryResponse response = new SqlQueryResponse();
        response.setId(sqlQuery.getId().toString());
        response.setMessage(MessageDto.fromEntity(sqlQuery.getMessage()));
        response.setOriginalQuery(sqlQuery.getOriginalQuery());
        response.setOptimizedQuery(sqlQuery.getOptimizedQuery());
        response.setOptimizationRationale(sqlQuery.getOptimizationRationale());
        response.setPerformanceImpact(sqlQuery.getPerformanceImpact());
        response.setPotentialRisks(sqlQuery.getPotentialRisks());
        response.setCreatedAt(sqlQuery.getCreatedAt().toString());
        response.setExecutionTimeMs(sqlQuery.getExecutionTimeMs());
        response.setOriginalPlan(sqlQuery.getOriginalPlan());
        response.setOptimizedPlan(sqlQuery.getOptimizedPlan());
        return response;
    }

    private String getDefaultPromptTemplate(boolean isMPP, boolean hasConnection) {
        StringBuilder template = new StringBuilder();
        template.append("## Информация о запросе\n\n");
        template.append("Исходный запрос:\n");
        template.append("```sql\n");
        template.append("[исходный запрос]\n");
        template.append("```\n\n");

        if (hasConnection) {
            template.append("## План выполнения\n\n");
            template.append("Исходный план:\n");
            template.append("```sql\n");
            template.append("[план выполнения исходного запроса]\n");
            template.append("```\n\n");

            template.append("## Метаданные таблиц\n\n");
            template.append("```json\n");
            template.append("[метаданные таблиц в формате JSON]\n");
            template.append("```\n\n");
        }

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
        LLMResponse result = new LLMResponse();
        
        try {
            // Разбиваем ответ на секции
            String[] sections = response.split("##");
            
            for (String section : sections) {
                if (section.contains("Оптимизированный SQL-запрос")) {
                    // Извлекаем оптимизированный SQL
                    int start = section.indexOf("```sql");
                    if (start != -1) {
                        start += 6;
                        int end = section.indexOf("```", start);
                        if (end != -1) {
                            result.setOptimizedSql(section.substring(start, end).trim());
                        }
                    }
                } else if (section.contains("Обоснование оптимизации") || section.contains("Обоснование изменений")) {
                    // Извлекаем обоснование
                    int start = section.indexOf("###");
                    if (start != -1) {
                        start = section.indexOf("\n", start);
                        if (start != -1) {
                            result.setOptimizationRationale(section.substring(start).trim());
                        }
                    }
                } else if (section.contains("Оценка улучшения")) {
                    // Извлекаем оценку улучшения
                    int start = section.indexOf("###");
                    if (start != -1) {
                        start = section.indexOf("\n", start);
                        if (start != -1) {
                            result.setPerformanceImpact(section.substring(start).trim());
                        }
                    }
                } else if (section.contains("Потенциальные риски")) {
                    // Извлекаем потенциальные риски
                    int start = section.indexOf("###");
                    if (start != -1) {
                        start = section.indexOf("\n", start);
                        if (start != -1) {
                            result.setPotentialRisks(section.substring(start).trim());
                        }
                    }
                }
            }
            
            // Если какие-то поля остались пустыми, устанавливаем значения по умолчанию
            if (result.getOptimizedSql() == null || result.getOptimizedSql().isEmpty()) {
                result.setOptimizedSql("Не удалось получить оптимизированную версию запроса");
            }
            if (result.getOptimizationRationale() == null || result.getOptimizationRationale().isEmpty()) {
                result.setOptimizationRationale("Нет данных об обосновании оптимизации");
            }
            if (result.getPerformanceImpact() == null || result.getPerformanceImpact().isEmpty()) {
                result.setPerformanceImpact("Нет данных об оценке улучшения");
            }
            if (result.getPotentialRisks() == null || result.getPotentialRisks().isEmpty()) {
                result.setPotentialRisks("Нет данных о потенциальных рисках");
            }
            
        } catch (Exception e) {
            log.error("Error parsing LLM response: {}", e.getMessage());
            result.setOptimizedSql("Ошибка при разборе ответа от LLM");
            result.setOptimizationRationale("Не удалось разобрать ответ от LLM");
            result.setPerformanceImpact("Нет данных");
            result.setPotentialRisks("Нет данных");
        }
        
        return result;
    }
}