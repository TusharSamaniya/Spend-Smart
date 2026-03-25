package in.spendsmart.ocrservice.provider;

import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import in.spendsmart.ocrservice.exception.OcrProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleDocumentAiProvider {

    private final DocumentProcessorServiceClient documentProcessorServiceClient;

    @Value("${google.document-ai.project-id}")
    private String projectId;

    @Value("${google.document-ai.location}")
    private String location;

    @Value("${google.document-ai.processor-id}")
    private String processorId;

    public String processImage(byte[] imageBytes) {
        try {
            RawDocument rawDocument = RawDocument.newBuilder()
                    .setContent(ByteString.copyFrom(imageBytes))
                    .setMimeType("image/jpeg")
                    .build();

            String processorName = String.format(
                    "projects/%s/locations/%s/processors/%s",
                    projectId,
                    location,
                    processorId
            );

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setName(processorName)
                    .setRawDocument(rawDocument)
                    .build();

            ProcessResponse response = documentProcessorServiceClient.processDocument(request);
            return JsonFormat.printer().includingDefaultValueFields().print(response);
        } catch (Exception exception) {
            throw new OcrProviderException("Failed to process image with Google Document AI", exception);
        }
    }
}
