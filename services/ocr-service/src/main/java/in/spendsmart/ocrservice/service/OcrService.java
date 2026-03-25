package in.spendsmart.ocrservice.service;

import java.util.UUID;

public interface OcrService {

    void markProcessing(UUID receiptId);

    void processReceipt(UUID receiptId, String s3Key);

    void markFailed(UUID receiptId);
}
