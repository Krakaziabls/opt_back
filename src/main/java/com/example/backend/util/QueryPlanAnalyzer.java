package com.example.backend.util;

import com.example.sqlopt.ast.Operation;
import com.example.sqlopt.ast.OperationType;
import com.example.sqlopt.ast.QueryPlanResult;
import com.example.backend.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryPlanAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(QueryPlanAnalyzer.class);

    public static class PlanMetrics {
        private final double executionTime;
        private final double planningTime;
        private final double totalCost;
        private final long rows;
        private final int width;
        private final String planText;
        private final List<OperationType> operations;

        public PlanMetrics(double executionTime, double planningTime, double totalCost,
                         long rows, int width, String planText, List<OperationType> operations) {
            this.executionTime = executionTime;
            this.planningTime = planningTime;
            this.totalCost = totalCost;
            this.rows = rows;
            this.width = width;
            this.planText = planText;
            this.operations = operations;
        }

        public double getExecutionTime() { return executionTime; }
        public double getPlanningTime() { return planningTime; }
        public double getTotalCost() { return totalCost; }
        public long getRows() { return rows; }
        public int getWidth() { return width; }
        public String getPlanText() { return planText; }
        public List<OperationType> getOperations() { return operations; }
    }

    public static PlanMetrics analyzePlan(Connection conn, String query, boolean isMPP) {
        if (conn == null) {
            logger.info("No database connection provided, skipping plan analysis");
            return null;
        }

        try {
            String explainQuery = isMPP ?
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query :
                "EXPLAIN (ANALYZE, BUFFERS) " + query;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(explainQuery)) {

                StringBuilder planText = new StringBuilder();
                while (rs.next()) {
                    planText.append(rs.getString(1)).append("\n");
                }

                String plan = planText.toString();
                return parsePlanMetrics(plan, isMPP);
            }
        } catch (Exception e) {
            logger.warn("Failed to analyze query plan: {}", e.getMessage());
            return null;
        }
    }

    private static PlanMetrics parsePlanMetrics(String plan, boolean isMPP) {
        try {
            double executionTime = extractExecutionTime(plan);
            double planningTime = extractPlanningTime(plan);
            double totalCost = extractTotalCost(plan);
            long rows = extractRows(plan);
            int width = extractWidth(plan);
            List<OperationType> operations = extractOperations(plan);

            return new PlanMetrics(executionTime, planningTime, totalCost, rows, width, plan, operations);
        } catch (Exception e) {
            logger.warn("Failed to parse plan metrics: {}", e.getMessage());
            return null;
        }
    }

    private static double extractExecutionTime(String plan) {
        Pattern pattern = Pattern.compile("Execution Time: (\\d+\\.\\d+) ms");
        Matcher matcher = pattern.matcher(plan);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static double extractPlanningTime(String plan) {
        Pattern pattern = Pattern.compile("Planning Time: (\\d+\\.\\d+) ms");
        Matcher matcher = pattern.matcher(plan);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static double extractTotalCost(String plan) {
        Pattern pattern = Pattern.compile("cost=(\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(plan);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static long extractRows(String plan) {
        Pattern pattern = Pattern.compile("rows=(\\d+)");
        Matcher matcher = pattern.matcher(plan);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
    }

    private static int extractWidth(String plan) {
        Pattern pattern = Pattern.compile("width=(\\d+)");
        Matcher matcher = pattern.matcher(plan);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static List<OperationType> extractOperations(String plan) {
        List<OperationType> operations = new ArrayList<>();
        String[] lines = plan.split("\n");

        for (String line : lines) {
            if (line.contains("->")) {
                String operation = line.split("->")[1].trim().split(" ")[0].toUpperCase();
                try {
                    operations.add(OperationType.valueOf(operation));
                } catch (IllegalArgumentException e) {
                    operations.add(OperationType.UNKNOWN);
                }
            }
        }

        return operations;
    }

    public static String comparePlans(PlanMetrics original, PlanMetrics optimized) {
        if (original == null || optimized == null) {
            return "Невозможно сравнить планы: отсутствуют метрики";
        }

        StringBuilder comparison = new StringBuilder();
        comparison.append("Сравнение планов выполнения:\n\n");

        // Сравнение времени выполнения
        double executionTimeDiff = ((optimized.getExecutionTime() - original.getExecutionTime()) / original.getExecutionTime()) * 100;
        comparison.append(String.format("Время выполнения: %.2f мс -> %.2f мс (изменение: %.1f%%)\n",
                original.getExecutionTime(), optimized.getExecutionTime(), executionTimeDiff));

        // Сравнение времени планирования
        double planningTimeDiff = ((optimized.getPlanningTime() - original.getPlanningTime()) / original.getPlanningTime()) * 100;
        comparison.append(String.format("Время планирования: %.2f мс -> %.2f мс (изменение: %.1f%%)\n",
                original.getPlanningTime(), optimized.getPlanningTime(), planningTimeDiff));

        // Сравнение общей стоимости
        double costDiff = ((optimized.getTotalCost() - original.getTotalCost()) / original.getTotalCost()) * 100;
        comparison.append(String.format("Общая стоимость: %.2f -> %.2f (изменение: %.1f%%)\n",
                original.getTotalCost(), optimized.getTotalCost(), costDiff));

        // Сравнение количества строк
        double rowsDiff = ((optimized.getRows() - original.getRows()) / (double) original.getRows()) * 100;
        comparison.append(String.format("Количество строк: %d -> %d (изменение: %.1f%%)\n",
                original.getRows(), optimized.getRows(), rowsDiff));

        // Сравнение ширины
        double widthDiff = ((optimized.getWidth() - original.getWidth()) / (double) original.getWidth()) * 100;
        comparison.append(String.format("Ширина: %d -> %d (изменение: %.1f%%)\n",
                original.getWidth(), optimized.getWidth(), widthDiff));

        // Сравнение операций
        comparison.append("\nИспользуемые операции:\n");
        comparison.append("Оригинальный запрос: " + String.join(", ", original.getOperations().stream()
                .map(Enum::name).toList()) + "\n");
        comparison.append("Оптимизированный запрос: " + String.join(", ", optimized.getOperations().stream()
                .map(Enum::name).toList()) + "\n");

        return comparison.toString();
    }

    public static QueryPlanResult toQueryPlanResult(PlanMetrics metrics) {
        if (metrics == null) {
            QueryPlanResult result = new QueryPlanResult();
            result.setExecutionTime(0.0);
            result.setPlanningTime(0.0);
            result.setTotalCost(0.0);
            result.setRows(0L);
            result.setWidth(0);
            result.setPlanText("");
            result.setOperations(new ArrayList<>());
            return result;
        }
        
        QueryPlanResult result = new QueryPlanResult();
        result.setExecutionTime(metrics.getExecutionTime());
        result.setPlanningTime(metrics.getPlanningTime());
        result.setTotalCost(metrics.getTotalCost());
        result.setRows(metrics.getRows());
        result.setWidth(metrics.getWidth());
        result.setPlanText(metrics.getPlanText());
        
        List<Operation> operations = new ArrayList<>();
        for (OperationType type : metrics.getOperations()) {
            operations.add(new Operation(type));
        }
        result.setOperations(operations);
        
        return result;
    }

    public static ExecutionResult toExecutionResult(PlanMetrics metrics) {
        if (metrics == null) {
            return new ExecutionResult(0, "Нет данных о плане выполнения");
        }
        return new ExecutionResult(
            (long) metrics.getExecutionTime(),
            metrics.getPlanText()
        );
    }
}
