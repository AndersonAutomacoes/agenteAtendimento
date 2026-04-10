package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.infrastructure.security.firebase.FirebaseAdminTokenVerifier;
import com.atendimento.cerebro.infrastructure.security.firebase.FirebaseTokenVerifier;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "cerebro.firebase", name = "enabled", havingValue = "true")
public class FirebaseAppConfiguration {

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        if (StringUtils.hasText(properties.getServiceAccountJsonPath())) {
            try (InputStream in = new FileInputStream(properties.getServiceAccountJsonPath())) {
                credentials = GoogleCredentials.fromStream(in);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }
        FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            builder.setProjectId(properties.getProjectId().trim());
        }
        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    public FirebaseTokenVerifier firebaseTokenVerifier() {
        return new FirebaseAdminTokenVerifier();
    }
}
