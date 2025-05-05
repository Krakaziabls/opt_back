package com.example.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.Chat;
import com.example.backend.entity.SQLQuery;
import com.example.backend.repository.SQLQueryRepository;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

@Service
public class SQLQueryService {

    private final SQLQueryRepository sqlQueryRepository;

    public SQLQueryService(SQLQueryRepository sqlQueryRepository) {
        this.sqlQueryRepository = sqlQueryRepository;
    }

    @Transactional
    public SQLQuery analyzeQuery(Chat chat, String queryText) {
        SQLQuery sqlQuery = new SQLQuery();
        sqlQuery.setChat(chat);
        sqlQuery.setQueryText(queryText);

        try {
            // Парсинг SQL-запроса
            Statement statement = CCJSqlParserUtil.parse(queryText);
            sqlQuery.setAstTree(statement.toString());

            // TODO: Добавить реальный анализ запроса и оптимизацию
            sqlQuery.setAnalysisResult("Query analysis result");
            sqlQuery.setOptimizedQuery(queryText);
        } catch (JSQLParserException e) {
            sqlQuery.setErrorMessage(e.getMessage());
        }

        return sqlQueryRepository.save(sqlQuery);
    }

    @Transactional(readOnly = true)
    public List<SQLQuery> getChatQueries(Chat chat) {
        return sqlQueryRepository.findByChatOrderByCreatedAtDesc(chat);
    }

    @Transactional(readOnly = true)
    public SQLQuery getQuery(Long queryId, Chat chat) {
        return sqlQueryRepository.findByIdAndChat(queryId, chat)
                .orElseThrow(() -> new RuntimeException("Query not found"));
    }
} 