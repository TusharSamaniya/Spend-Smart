package in.spendsmart.ocrservice.worker;

import java.util.UUID;

public record OcrJobMessage(
        UUID receiptId,
        String s3Key,
        UUID orgId
) {
}
