package in.spendsmart.gstservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class GstCalculatorService {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public GstResult computeGst(
            BigDecimal totalAmount,
            BigDecimal gstRate,
            String supplierStateCode,
            String buyerStateCode
    ) {
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(gstRate, "gstRate must not be null");

        BigDecimal taxableAmount = totalAmount.divide(ONE.add(gstRate), 2, RoundingMode.HALF_UP);
        BigDecimal totalGstAmount = totalAmount.subtract(taxableAmount).setScale(2, RoundingMode.HALF_UP);

        boolean intraStateSupply = Objects.equals(supplierStateCode, buyerStateCode);
        if (intraStateSupply) {
            BigDecimal cgstAmount = totalGstAmount.divide(TWO, 2, RoundingMode.HALF_UP);
            BigDecimal sgstAmount = totalGstAmount.subtract(cgstAmount).setScale(2, RoundingMode.HALF_UP);
            return new GstResult(taxableAmount, totalGstAmount, cgstAmount, sgstAmount, ZERO_MONEY);
        }

        return new GstResult(taxableAmount, totalGstAmount, ZERO_MONEY, ZERO_MONEY, totalGstAmount);
    }

    public record GstResult(
            BigDecimal taxableAmount,
            BigDecimal totalGstAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount
    ) {
    }
}