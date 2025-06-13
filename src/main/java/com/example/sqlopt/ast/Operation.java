package com.example.sqlopt.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Operation {
    private OperationType type;
    private String tableName;
    private Map<String, Object> tableMetadata;
    private Map<String, Object> additionalInfo;
    private Map<String, String> statistics;
    private List<String> keys;
    private List<String> conditions;

    public Operation() {
        this.additionalInfo = new HashMap<>();
        this.tableMetadata = new HashMap<>();
        this.statistics = new HashMap<>();
        this.keys = new ArrayList<>();
        this.conditions = new ArrayList<>();
    }

    public Operation(OperationType type) {
        this();
        this.type = type;
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Map<String, Object> getTableMetadata() {
        return tableMetadata;
    }

    public void setTableMetadata(Map<String, Object> tableMetadata) {
        this.tableMetadata = tableMetadata;
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

    public Map<String, String> getStatistics() {
        return statistics;
    }

    public void setStatistics(Map<String, String> statistics) {
        this.statistics = statistics;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions;
    }

    @Override
    public String toString() {
        return "Operation{" +
                "type=" + type +
                ", tableName='" + tableName + '\'' +
                ", tableMetadata=" + tableMetadata +
                ", additionalInfo=" + additionalInfo +
                ", statistics=" + statistics +
                ", keys=" + keys +
                ", conditions=" + conditions +
                '}';
    }
} 