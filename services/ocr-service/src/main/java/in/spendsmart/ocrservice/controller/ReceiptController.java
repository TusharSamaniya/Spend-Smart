package in.spendsmart.ocrservice.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.spendsmart.ocrservice.entity.Receipt;
import in.spendsmart.ocrservice.repository.ReceiptRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@RestController
@RequestMapping("/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final S3Presigner s3Presigner;
    private final ReceiptRepository receiptRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> createUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        String s3Key = String.format("receipts/%s/%s.jpg", request.orgId(), UUID.randomUUID());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("image/jpeg")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        UploadUrlResponse response = new UploadUrlResponse(presignedRequest.url().toString(), s3Key);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceiptDetailsResponse> getReceipt(@PathVariable UUID id, @RequestParam UUID orgId) {
        Receipt receipt = receiptRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found"));

        return ResponseEntity.ok(ReceiptDetailsResponse.from(receipt));
    }

    public record UploadUrlRequest(@NotNull UUID orgId) {
    }

    public record UploadUrlResponse(
            @JsonProperty("upload_url") String uploadUrl,
            @JsonProperty("receipt_key") String receiptKey
    ) {
    }

    public record ReceiptDetailsResponse(
            UUID id,
            Receipt.ReceiptStatus status,
            @JsonProperty("merchant_name") String merchantName,
            BigDecimal amount,
            String currency,
            @JsonProperty("receipt_date") LocalDate receiptDate,
            @JsonProperty("gst_amount") BigDecimal gstAmount,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            @JsonProperty("hsn_sac_code") String hsnSacCode,
            @JsonProperty("line_items") String lineItems,
            @JsonProperty("confidence_score") BigDecimal confidenceScore,
            @JsonProperty("duplicate_of") UUID duplicateOf,
            @JsonProperty("raw_ocr") String rawOcr,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {
        static ReceiptDetailsResponse from(Receipt receipt) {
            return new ReceiptDetailsResponse(
                    receipt.getId(),
                    receipt.getStatus(),
                    receipt.getMerchantName(),
                    receipt.getAmount(),
                    receipt.getCurrency(),
                    receipt.getReceiptDate(),
                    receipt.getGstAmount(),
                    receipt.getCgst(),
                    receipt.getSgst(),
                    receipt.getIgst(),
                    receipt.getHsnSacCode(),
                    receipt.getLineItems(),
                    receipt.getConfidenceScore(),
                    receipt.getDuplicateOf(),
                    receipt.getRawOcr(),
                    receipt.getCreatedAt()
            );
        }
    }
}
