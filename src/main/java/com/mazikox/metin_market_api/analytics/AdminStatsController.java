package com.mazikox.metin_market_api.analytics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
public class AdminStatsController {
    private final AnalyticsService analytics;
    public AdminStatsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/api/v1/admin/stats")
    public Map<String, Object> stats(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(defaultValue="14") int days, @RequestParam(defaultValue="20") int limit) {
        response.setHeader("Cache-Control", "no-store");
        if (!analytics.trusted(request) || request.getHeader("X-Analytics-Admin") == null
                || request.getHeader("X-Analytics-Admin").isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (days < 1 || days > 366 || (limit != 10 && limit != 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return analytics.report(days, limit);
    }
}
