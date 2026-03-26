package in.spendsmart.upiservice.client;

import in.spendsmart.upiservice.worker.UpiTransactionMessage;
import java.util.UUID;

public interface ExpenseServiceClient {

    UUID createFromUpi(UpiTransactionMessage message, UUID userId, String resolvedMerchant, String category);
}
