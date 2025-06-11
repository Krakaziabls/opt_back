package com.example.sqlopt.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryPlanResult {
    private List<Operation> operations;
    private Map<String, Object> additionalInfo;
    private Double cost;
    private Double planningTimeMs;
    private Double executionTimeMs;

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

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public Double getPlanningTimeMs() {
        return planningTimeMs;
    }

    public void setPlanningTimeMs(Double planningTimeMs) {
        this.planningTimeMs = planningTimeMs;
    }

    public Double getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Double executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    @Override
    public String toString() {
        return "QueryPlanResult{" +
                "operations=" + operations +
                ", additionalInfo=" + additionalInfo +
                ", cost=" + cost +
                ", planningTimeMs=" + planningTimeMs +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
} 