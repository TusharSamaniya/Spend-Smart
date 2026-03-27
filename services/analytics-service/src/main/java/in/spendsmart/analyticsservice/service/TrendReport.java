package in.spendsmart.analyticsservice.service;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendReport {

    private List<MonthlyTotal> monthlyTotals;
    private List<CategoryTrend> categoryTrends;
    private BigDecimal yoyChange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTotal {
        private String yearMonth;
        private BigDecimal total;
        private BigDecimal pctChangeFromPreviousMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryTrend {
        private String categoryName;
        private List<MonthlyPoint> trajectory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPoint {
        private String yearMonth;
        private BigDecimal total;
    }
}
