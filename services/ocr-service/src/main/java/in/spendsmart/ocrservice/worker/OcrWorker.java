package in.spendsmart.ocrservice.worker;

import in.spendsmart.ocrservice.service.OcrService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrWorker {

    private final OcrService ocrService;

    @SqsListener("${aws.sqs.queue-url}")
    public void processOcrJob(OcrJobMessage message) {
        log.info("Received OCR job for receiptId={}", message.receiptId());
        try {
            ocrService.markProcessing(message.receiptId());
            ocrService.processReceipt(message.receiptId(), message.s3Key());
        } catch (Exception exception) {
            log.error("OCR job failed for receiptId={}", message.receiptId(), exception);
            ocrService.markFailed(message.receiptId());
        }
    }
}
