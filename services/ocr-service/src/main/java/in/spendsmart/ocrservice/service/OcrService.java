package in.spendsmart.ocrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.ocrservice.duplicate.DuplicateDetector;
import in.spendsmart.ocrservice.entity.Receipt;
import in.spendsmart.ocrservice.event.ReceiptEventPublisher;
import in.spendsmart.ocrservice.exception.OcrProviderException;
import in.spendsmart.ocrservice.parser.IndianReceiptParser;
import in.spendsmart.ocrservice.provider.AwsTextractProvider;
import in.spendsmart.ocrservice.provider.GoogleDocumentAiProvider;
import in.spendsmart.ocrservice.repository.ReceiptRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private static final BigDecimal GOOGLE_MIN_CONFIDENCE = new BigDecimal("0.70");

    private final ReceiptRepository receiptRepository;
    private final S3Client s3Client;
    private final DuplicateDetector duplicateDetector;
    private final GoogleDocumentAiProvider googleDocumentAiProvider;
    private final AwsTextractProvider awsTextractProvider;
    private final IndianReceiptParser indianReceiptParser;
    private final ReceiptEventPublisher receiptEventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Transactional
    public void markProcessing(UUID receiptId) {
        Receipt receipt = findReceipt(receiptId);
        receipt.setStatus(Receipt.ReceiptStatus.PROCESSING);
        receiptRepository.save(receipt);
    }

    @Transactional
    public void processReceipt(UUID receiptId, String s3Key) {
        Receipt receipt = findReceipt(receiptId);
        byte[] imageBytes = downloadFromS3(s3Key);

        Optional<UUID> duplicateOf = duplicateDetector.isDuplicate(receipt.getOrgId(), imageBytes);
        duplicateOf.ifPresent(receipt::setDuplicateOf);

        ProviderOutput providerOutput = runProvidersWithFallback(imageBytes);
        IndianReceiptParser.OcrResult parsedResult = indianReceiptParser.extract(providerOutput.textForParsing());

        receipt.setS3Key(s3Key);
        receipt.setAmount(parsedResult.amount());
        receipt.setMerchantName(parsedResult.merchantName());
        receipt.setReceiptDate(parsedResult.receiptDate());
        receipt.setGstAmount(parsedResult.gstAmount());
        receipt.setCgst(parsedResult.cgst());
        receipt.setSgst(parsedResult.sgst());
        receipt.setHsnSacCode(parsedResult.hsnCode());
        receipt.setRawOcr(providerOutput.rawResponse());
        receipt.setConfidenceScore(providerOutput.confidenceScore());
        receipt.setStatus(Receipt.ReceiptStatus.DONE);

        Receipt savedReceipt = receiptRepository.save(receipt);
        receiptEventPublisher.publishOcrCompleted(savedReceipt);
    }

    @Transactional
    public void markFailed(UUID receiptId) {
        Receipt receipt = findReceipt(receiptId);
        receipt.setStatus(Receipt.ReceiptStatus.FAILED);
        receiptRepository.save(receipt);
    }

    private Receipt findReceipt(UUID receiptId) {
        return receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalStateException("Receipt not found: " + receiptId));
    }

    private byte[] downloadFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);
        return responseBytes.asByteArray();
    }

    private ProviderOutput runProvidersWithFallback(byte[] imageBytes) {
        try {
            String googleRaw = googleDocumentAiProvider.processImage(imageBytes);
            String googleText = extractGoogleTextForParsing(googleRaw);
            BigDecimal confidence = extractGoogleConfidence(googleRaw).orElse(null);

            if (confidence != null && confidence.compareTo(GOOGLE_MIN_CONFIDENCE) >= 0) {
                return new ProviderOutput(googleRaw, googleText, confidence);
            }

            log.warn("Google Document AI confidence low or missing. Falling back to Textract.");
        } catch (OcrProviderException exception) {
            log.warn("Google Document AI failed. Falling back to Textract.", exception);
        }

        String textractText = awsTextractProvider.processImage(imageBytes);
        return new ProviderOutput(textractText, textractText, null);
    }

    private String extractGoogleTextForParsing(String googleRawJson) {
        try {
            JsonNode root = objectMapper.readTree(googleRawJson);
            String text = root.path("document").path("text").asText("");
            return text.isBlank() ? googleRawJson : text;
        } catch (Exception exception) {
            return googleRawJson;
        }
    }

    private Optional<BigDecimal> extractGoogleConfidence(String googleRawJson) {
        try {
            JsonNode root = objectMapper.readTree(googleRawJson);
            return findConfidence(root)
                    .map(confidence -> confidence.setScale(4, RoundingMode.HALF_UP));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> findConfidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.isObject()) {
            JsonNode confidenceNode = node.get("confidence");
            if (confidenceNode != null && confidenceNode.isNumber()) {
                return Optional.of(confidenceNode.decimalValue());
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                Optional<BigDecimal> nested = findConfidence(field.getValue());
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<BigDecimal> nested = findConfidence(child);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private record ProviderOutput(String rawResponse, String textForParsing, BigDecimal confidenceScore) {
    }
}
