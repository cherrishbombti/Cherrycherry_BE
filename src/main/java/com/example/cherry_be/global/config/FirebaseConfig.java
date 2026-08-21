package com.example.cherry_be.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Firebase Admin SDK 초기화.
 *
 * 서비스 계정 키는 secrets/ 아래에 두고 커밋하지 않는다.
 * 키가 없거나 초기화에 실패해도 서버는 정상 기동해야 한다.
 * 푸시는 부가 기능이므로, 이것 때문에 낙상 감지·이력 저장이 멈추면 안 된다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-path}")
    private String serviceAccountPath;

    @Value("${firebase.enabled}")
    private boolean enabled;

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (!enabled) {
            log.warn("Firebase 비활성화 상태입니다. 푸시는 발송되지 않습니다. (firebase.enabled=false)");
            return null;
        }

        try (InputStream keyStream = openKeyStream()) {
            if (keyStream == null) {
                log.error("Firebase 키 파일을 찾을 수 없습니다. 푸시 발송이 비활성화됩니다."
                                + " 설정값={}, 실행 위치={}",
                        serviceAccountPath, new File("").getAbsolutePath());
                return null;
            }

            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(keyStream))
                            .build())
                    : FirebaseApp.getInstance();

            log.info("Firebase 초기화 완료");
            return FirebaseMessaging.getInstance(app);

        } catch (Exception e) {
            log.error("Firebase 초기화 실패. 푸시 발송이 비활성화됩니다. 사유={}", e.getMessage());
            return null;
        }
    }

    /**
     * 키 파일을 찾는다.
     * 실행 위치에 따라 상대 경로가 어긋날 수 있어 파일시스템 → 클래스패스 순으로 시도한다.
     * 배포 환경에서는 절대 경로를 환경변수로 지정하는 것을 권장한다.
     */
    private InputStream openKeyStream() throws Exception {
        File file = new File(serviceAccountPath);
        if (file.exists()) {
            log.info("Firebase 키 파일 사용: {}", file.getAbsolutePath());
            return new FileInputStream(file);
        }

        ClassPathResource resource = new ClassPathResource(serviceAccountPath);
        if (resource.exists()) {
            log.info("Firebase 키 파일 사용(classpath): {}", serviceAccountPath);
            return resource.getInputStream();
        }

        return null;
    }
}
