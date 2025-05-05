// SQLOptimizerService.java
package com.example.backend.service;

import com.example.backend.entity.OptimizedQuery;
import com.example.backend.repository.OptimizedQueryRepository;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.deparser.StatementDeParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SQLOptimizerService {
    private final OptimizedQueryRepository repo;

    public SQLOptimizerService(OptimizedQueryRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public OptimizedQuery optimize(String sql) throws JSQLParserException {
        Statement stmt = CCJSqlParserUtil.parse(sql);
        String optimized = sql;
        if (stmt instanceof Select) {
            optimizeSelect((Select) stmt);
            StringBuilder buf = new StringBuilder();
            stmt.accept(new StatementDeParser(buf));
            optimized = buf.toString();
        }
        OptimizedQuery entity = OptimizedQuery.builder()
                .originalSql(sql)
                .optimizedSql(optimized)
                // убрали .createdAt(...)
                .build();
        return repo.save(entity);
    }

    private void optimizeSelect(Select select) {
        SelectBody body = select.getSelectBody();
        if (body instanceof PlainSelect) {
            PlainSelect ps = (PlainSelect) body;
            if (ps.getWhere() != null) {
                ps.setWhere(optimizeExpression(ps.getWhere()));
            }
            // можно добавить оптимизацию JOIN-ов и другие методы
        }
    }

    private net.sf.jsqlparser.expression.Expression optimizeExpression(
            net.sf.jsqlparser.expression.Expression expr) {
        // ваш код из PDF: удаление 1=1, упрощение AND и т.д.
        return expr;
    }
}
