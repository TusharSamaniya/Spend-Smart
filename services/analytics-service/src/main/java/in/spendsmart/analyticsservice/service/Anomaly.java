package in.spendsmart.analyticsservice.service;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anomaly {

    private String category;
    private BigDecimal actual;
    private BigDecimal baseline;
    private BigDecimal pctAbove;
}
