package in.spendsmart.upiservice.worker;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpiTransactionMessage(
        String upiRefId,
        String vpaSender,
        String vpaReceiver,
        BigDecimal amount,
        OffsetDateTime txnTimestamp,
        UUID orgId
) {
}
