package in.spendsmart.upiservice.client;

import in.spendsmart.upiservice.worker.UpiTransactionMessage;

public interface CategorizationServiceClient {

    String categorize(UpiTransactionMessage message, String resolvedMerchant);
}
