// OptimizeController.java
package com.example.backend.controller;

import com.example.backend.dto.OptimizeRequest;
import com.example.backend.dto.OptimizeResponse;
import com.example.backend.entity.OptimizedQuery;
import com.example.backend.service.SQLOptimizerService;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/optimize")
public class OptimizeController {
    private final SQLOptimizerService optimizer;

    public OptimizeController(SQLOptimizerService optimizer) {
        this.optimizer = optimizer;
    }

    @PostMapping
    public ResponseEntity<OptimizeResponse> optimize(@RequestBody OptimizeRequest req) throws JSQLParserException {
        OptimizedQuery result = optimizer.optimize(req.getSql());
        OptimizeResponse resp = new OptimizeResponse();
        resp.setOriginalSql(result.getOriginalSql());
        resp.setOptimizedSql(result.getOptimizedSql());
        return ResponseEntity.ok(resp);
    }
}
