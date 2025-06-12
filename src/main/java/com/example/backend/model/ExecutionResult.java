package com.example.backend.model;

public class ExecutionResult {
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