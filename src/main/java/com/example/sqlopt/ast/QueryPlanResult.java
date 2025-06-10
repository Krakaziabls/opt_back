package com.example.sqlopt.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryPlanResult {
    private List<Operation> operations;
    private Map<String, Object> additionalInfo;

    public QueryPlanResult() {
        this.operations = new ArrayList<>();
        this.additionalInfo = new HashMap<>();
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    public void addOperation(Operation operation) {
        this.operations.add(operation);
    }

    public Map<String, Object> getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(Map<String, Object> additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public void addAdditionalInfo(String key, Object value) {
        this.additionalInfo.put(key, value);
    }

    @Override
    public String toString() {
        return "QueryPlanResult{" +
                "operations=" + operations +
                ", additionalInfo=" + additionalInfo +
                '}';
    }
} 