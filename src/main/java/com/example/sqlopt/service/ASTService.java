package com.example.sqlopt.service;

import com.example.sqlopt.ast.Operation;
import com.example.sqlopt.ast.QueryPlanAnalyzer;
import com.example.sqlopt.ast.QueryPlanResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ASTService {

    private final Map<String, Map<String, Object>> tableMetadata = new HashMap<>();

    public QueryPlanResult analyzeQueryPlan(String plan) {
        // Анализируем план запроса
        return QueryPlanAnalyzer.analyzeQueryPlan(plan);
    }

    public void updateTableMetadata(String tableName, Map<String, Object> metadata) {
        tableMetadata.put(tableName, metadata);
    }

    private void enrichWithMetadata(QueryPlanResult result, List<String> tables) {
        for (String table : tables) {
            Map<String, Object> metadata = tableMetadata.get(table);
            if (metadata != null) {
                // Добавляем метаинформацию к операциям, связанным с таблицей
                for (Operation operation : result.getOperations()) {
                    if (operation.getTableName() != null &&
                        operation.getTableName().equals(table)) {
                        operation.setTableMetadata(metadata);
                    }
                }
            }
        }
    }

    public Map<String, Object> getTableMetadata(String tableName) {
        return tableMetadata.get(tableName);
    }

    public void clearTableMetadata() {
        tableMetadata.clear();
    }
}
