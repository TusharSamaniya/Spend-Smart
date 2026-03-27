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
public class SpendSummary {

    private BigDecimal totalSpend;
    private Long txnCount;
    private List<CategorySpend> byCategory;
    private List<TopMerchantSpend> topMerchants;
    private BigDecimal avgDailySpend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySpend {
        private String categoryName;
        private BigDecimal total;
        private Long count;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopMerchantSpend {
        private String merchantName;
        private BigDecimal total;
        private Long count;
    }
}
