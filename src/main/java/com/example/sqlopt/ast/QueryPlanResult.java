package com.example.sqlopt.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryPlanResult {
    private Double executionTimeMs;
    private Double planningTimeMs;
    private Double cost;
    private Long rows;
    private Integer width;
    private String planText;
    private List<Operation> operations;
    private Map<String, Object> additionalInfo;

    public QueryPlanResult() {
        this.operations = new ArrayList<>();
        this.additionalInfo = new HashMap<>();
    }

    public QueryPlanResult(Double executionTime, Double planningTime, Double totalCost, 
                         Long rows, Integer width, String planText, List<OperationType> operations) {
        this.executionTimeMs = executionTime;
        this.planningTimeMs = planningTime;
        this.cost = totalCost;
        this.rows = rows;
        this.width = width;
        this.planText = planText;
        this.operations = new ArrayList<>();
        for (OperationType type : operations) {
            this.operations.add(new Operation(type));
        }
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

    public Double getExecutionTime() {
        return executionTimeMs;
    }

    public void setExecutionTime(Double executionTime) {
        this.executionTimeMs = executionTime;
    }

    public Double getPlanningTime() {
        return planningTimeMs;
    }

    public void setPlanningTime(Double planningTime) {
        this.planningTimeMs = planningTime;
    }

    public Double getTotalCost() {
        return cost;
    }

    public void setTotalCost(Double totalCost) {
        this.cost = totalCost;
    }

    public Long getRows() {
        return rows;
    }

    public void setRows(Long rows) {
        this.rows = rows;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public String getPlanText() {
        return planText;
    }

    public void setPlanText(String planText) {
        this.planText = planText;
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