package com.example.sqlopt.ast;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryPlanAnalyzer {
    private static final Map<String, OperationType> OPERATION_PATTERNS = new HashMap<>();
    private static final Map<String, String> STATISTIC_PATTERNS = new HashMap<>();

    static {
        // Инициализация паттернов операций
        OPERATION_PATTERNS.put("Seq Scan", OperationType.SEQUENTIAL_SCAN);
        OPERATION_PATTERNS.put("Index Scan", OperationType.INDEX_SCAN);
        OPERATION_PATTERNS.put("Bitmap Heap Scan", OperationType.BITMAP_HEAP_SCAN);
        OPERATION_PATTERNS.put("Bitmap Index Scan", OperationType.BITMAP_INDEX_SCAN);
        OPERATION_PATTERNS.put("Sort", OperationType.SORT);
        OPERATION_PATTERNS.put("Hash", OperationType.HASH);
        OPERATION_PATTERNS.put("Hash Join", OperationType.HASH_JOIN);
        OPERATION_PATTERNS.put("Nested Loop", OperationType.NESTED_LOOP);
        OPERATION_PATTERNS.put("Merge Join", OperationType.MERGE_JOIN);
        OPERATION_PATTERNS.put("Aggregate", OperationType.AGGREGATE);
        OPERATION_PATTERNS.put("Gather Motion", OperationType.GATHER_MOTION);
        OPERATION_PATTERNS.put("Redistribute Motion", OperationType.REDISTRIBUTE_MOTION);
        OPERATION_PATTERNS.put("Broadcast Motion", OperationType.BROADCAST_MOTION);

        // Инициализация паттернов статистики
        STATISTIC_PATTERNS.put("rows", "rows=(\\d+)");
        STATISTIC_PATTERNS.put("cost", "cost=(\\d+\\.\\d+)");
        STATISTIC_PATTERNS.put("width", "width=(\\d+)");
        STATISTIC_PATTERNS.put("actual time", "actual time=(\\d+\\.\\d+)");
        STATISTIC_PATTERNS.put("planning time", "planning time=(\\d+\\.\\d+)");
        STATISTIC_PATTERNS.put("execution time", "execution time=(\\d+\\.\\d+)");
    }

    public static QueryPlanResult analyzeQueryPlan(String plan) {
        if (plan == null || plan.trim().isEmpty()) {
            return new QueryPlanResult();
        }

        QueryPlanResult result = new QueryPlanResult();
        List<String> lines = Arrays.asList(plan.split("\n"));
        Set<String> allHashKeys = new HashSet<>();

        // Анализ каждой строки плана
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Поиск операции
            for (Map.Entry<String, OperationType> entry : OPERATION_PATTERNS.entrySet()) {
                if (line.contains(entry.getKey())) {
                    Operation op = new Operation(entry.getValue());

                    // Извлечение имени таблицы
                    Pattern tablePattern = Pattern.compile("on\\s+(\\S+)");
                    Matcher tableMatcher = tablePattern.matcher(line);
                    if (tableMatcher.find()) {
                        op.setTableName(tableMatcher.group(1));
                    }

                    // Извлечение статистики
                    for (Map.Entry<String, String> statEntry : STATISTIC_PATTERNS.entrySet()) {
                        Pattern statPattern = Pattern.compile(statEntry.getValue());
                        Matcher statMatcher = statPattern.matcher(line);
                        if (statMatcher.find()) {
                            op.getStatistics().put(statEntry.getKey(), statMatcher.group(1));
                        }
                    }

                    // Сбор хеш-ключей для redistribute motion
                    if (entry.getValue() == OperationType.HASH) {
                        Pattern keyPattern = Pattern.compile("Hash Key: (\\S+)");
                        Matcher keyMatcher = keyPattern.matcher(line);
                        if (keyMatcher.find()) {
                            allHashKeys.add(keyMatcher.group(1));
                        }
                    }

                    result.addOperation(op);
                    break;
                }
            }
        }

        // Добавление redistribute motion если есть хеш-ключи
        if (!allHashKeys.isEmpty()) {
            boolean hasRedistributeMotion = result.getOperations().stream()
                    .anyMatch(op -> op.getType() == OperationType.REDISTRIBUTE_MOTION);

            if (!hasRedistributeMotion) {
                Operation redistributeOp = new Operation(OperationType.REDISTRIBUTE_MOTION);
                redistributeOp.getAdditionalInfo().put("hashKeys", new ArrayList<>(allHashKeys));
                result.addOperation(redistributeOp);
            }
        }

        return result;
    }
}
