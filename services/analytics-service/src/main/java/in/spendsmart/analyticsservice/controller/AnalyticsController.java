package in.spendsmart.analyticsservice.controller;

import in.spendsmart.analyticsservice.service.Anomaly;
import in.spendsmart.analyticsservice.service.AnomalyDetector;
import in.spendsmart.analyticsservice.service.SpendSummary;
import in.spendsmart.analyticsservice.service.SpendSummaryService;
import in.spendsmart.analyticsservice.service.TrendAnalysisService;
import in.spendsmart.analyticsservice.service.TrendReport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SpendSummaryService spendSummaryService;
    private final TrendAnalysisService trendAnalysisService;
    private final AnomalyDetector anomalyDetector;

    @GetMapping("/summary")
    public ResponseEntity<SpendSummary> getSummary(
            @RequestHeader("X-Org-Id") UUID orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "category") String groupBy
    ) {
        return ResponseEntity.ok(spendSummaryService.getSummary(orgId, from, to, groupBy));
    }

    @GetMapping("/trends")
    public ResponseEntity<TrendReport> getTrends(
            @RequestHeader("X-Org-Id") UUID orgId,
            @RequestParam(defaultValue = "6") int months
    ) {
        return ResponseEntity.ok(trendAnalysisService.getMonthlyTrends(orgId, months));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<Anomaly>> getAnomalies(
            @RequestHeader("X-Org-Id") UUID orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(anomalyDetector.detectAnomalies(orgId, from, to));
    }
}
