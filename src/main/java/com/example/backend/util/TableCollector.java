package com.example.backend.util;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubJoin;

public class TableCollector {
    public static List<String> collectTables(String sql) {
        try {
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            Statement statement = parserManager.parse(new StringReader(sql));
            
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                return collectTablesFromSelectBody(selectStatement.getSelectBody());
            }
            return new ArrayList<>();
        } catch (JSQLParserException e) {
            throw new RuntimeException("Failed to parse SQL query", e);
        }
    }

    private static List<String> collectTablesFromSelectBody(SelectBody selectBody) {
        Set<String> tables = new HashSet<>();
        
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            collectTablesFromFromItem(plainSelect.getFromItem(), tables);
            
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    collectTablesFromFromItem(join.getRightItem(), tables);
                }
            }
        } else if (selectBody instanceof SetOperationList) {
            SetOperationList setOpList = (SetOperationList) selectBody;
            for (SelectBody select : setOpList.getSelects()) {
                tables.addAll(collectTablesFromSelectBody(select));
            }
        }
        return new ArrayList<>(tables);
    }

    private static void collectTablesFromFromItem(FromItem fromItem, Set<String> tables) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            tables.add(table.getName());
        } else if (fromItem instanceof SubJoin) {
            SubJoin subJoin = (SubJoin) fromItem;
            collectTablesFromFromItem(subJoin.getLeft(), tables);
            for (Join join : subJoin.getJoinList()) {
                collectTablesFromFromItem(join.getRightItem(), tables);
            }
        }
    }
} 