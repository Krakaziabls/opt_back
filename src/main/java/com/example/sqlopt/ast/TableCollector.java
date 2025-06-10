package com.example.sqlopt.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TableCollector {
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static List<String> collectTables(String query) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String table = matcher.group(1).trim();
            // Удаляем схему, если она есть
            if (table.contains(".")) {
                table = table.substring(table.indexOf(".") + 1);
            }
            if (!tables.contains(table)) {
                tables.add(table);
            }
        }
        
        return tables;
    }
} 