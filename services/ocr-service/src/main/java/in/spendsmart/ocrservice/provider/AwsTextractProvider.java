package in.spendsmart.ocrservice.provider;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;

@Component
@RequiredArgsConstructor
public class AwsTextractProvider {

    private final TextractClient textractClient;

    public String processImage(byte[] imageBytes) {
        Document document = Document.builder()
                .bytes(SdkBytes.fromByteArray(imageBytes))
                .build();

        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                .document(document)
                .build();

        DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

        return response.blocks().stream()
                .filter(block -> BlockType.LINE.equals(block.blockType()))
                .map(block -> block.text() == null ? "" : block.text())
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
