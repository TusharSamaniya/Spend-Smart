package in.spendsmart.notificationservice.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FirebaseConfig.class);

    private final String credentialsPath;

    public FirebaseConfig(@Value("${firebase.credentials-path}") String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        if (credentialsPath == null || credentialsPath.isBlank()) {
            LOG.warn("Firebase initialization skipped: firebase.credentials-path is not configured");
            return;
        }

        Path credentialFilePath = Path.of(credentialsPath);
        if (!Files.exists(credentialFilePath)) {
            LOG.warn("Firebase initialization skipped: credentials file not found at {}", credentialFilePath);
            return;
        }

        try (FileInputStream serviceAccount = new FileInputStream(credentialFilePath.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            LOG.info("Firebase app initialized successfully");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize Firebase from path: " + credentialsPath, ex);
        }
    }
}
