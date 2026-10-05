package com.mazikox.metin_market_api.scans;

import com.mazikox.metin_market_api.analytics.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/servers/{server}/scans")
public class AdminScansController {
    private final ScanSelectionService scans;
    private final AnalyticsService analytics;
    public AdminScansController(ScanSelectionService scans, AnalyticsService analytics) {
        this.scans = scans; this.analytics = analytics;
    }
    public record Selection(@NotBlank @Size(max=128) String mapId, @NotNull Boolean enabled,
                            @Positive Long selectedScanId) {}
    public record Update(@NotNull @Size(min=1, max=100) List<@NotNull @Valid Selection> maps) {}

    private void authorize(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (!analytics.trusted(request) || request.getHeader("X-Analytics-Admin") == null
                || request.getHeader("X-Analytics-Admin").isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    @GetMapping
    public ScanSelectionService.Report get(HttpServletRequest request, HttpServletResponse response) {
        authorize(request, response);
        return scans.report();
    }
    @PutMapping(consumes="application/json")
    public ScanSelectionService.Report update(HttpServletRequest request, HttpServletResponse response,
                                              @Valid @RequestBody Update update) {
        authorize(request, response);
        String site = request.getHeader("Sec-Fetch-Site");
        if (!"scan-selection".equals(request.getHeader("X-Admin-Action"))
                || (site != null && !"same-origin".equals(site) && !"none".equals(site)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return scans.update(update.maps());
    }
}
