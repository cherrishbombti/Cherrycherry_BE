package com.example.cherry_be.global.crypto;

import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 문자열 컬럼을 AES-GCM 으로 암호화해 저장한다.
 *
 * 건강정보(기저질환·복용약·병력)처럼 DB 에 평문으로 두면 안 되는 값에 @Convert 로 적용한다.
 * DB 파일이나 덤프가 단독으로 유출되는 상황을 막는 것이 목적이며,
 * 키가 서버 환경변수에 있으므로 서버 자체가 뚫리는 경우까지 막지는 못한다.
 *
 * CBC 가 아니라 GCM 을 쓰는 이유
 *  - GCM 은 인증 태그를 함께 저장해 값이 변조되면 복호화 단계에서 걸린다.
 *  - CBC 는 변조를 감지하지 못해 조작된 값이 그대로 화면에 노출될 수 있다.
 *
 * 저장 형식: Base64(IV 12바이트 + 암호문 + 인증태그 16바이트)
 * IV 를 매번 새로 만들어 앞에 붙이므로, 같은 평문이라도 저장값이 매번 달라진다.
 * 따라서 암호화된 컬럼으로는 검색·정렬·동등비교를 할 수 없다.
 */
@Slf4j
@Component
@Converter
public class StringEncryptConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;        // GCM 권장 IV 길이
    private static final int TAG_BIT_LENGTH = 128;  // 인증 태그 길이

    // JPA 가 컨버터 인스턴스를 직접 만드는 경로가 있어 인스턴스 필드 주입은 신뢰할 수 없다.
    // 스프링이 만든 빈에서 키를 static 으로 올려두고 모든 인스턴스가 공유한다.
    // 여러 스레드가 읽으므로 volatile 로 가시성을 보장한다.
    private static volatile SecretKeySpec secretKey;

    @Value("${encryption.key}")
    public void setKey(String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    "encryption.key 는 16/24/32 바이트여야 합니다. 현재: " + raw.length);
        }
        secretKey = new SecretKeySpec(raw, "AES");
    }

    /**
     * 기동 시 암·복호화를 한 번 돌려본다.
     *
     * 키 설정이 잘못됐을 때 첫 요청에서 터지는 대신 여기서 즉시 실패시킨다.
     * 민감정보라 "쓰는 시점에 알게 되는" 실패가 특히 곤란하다.
     */
    @PostConstruct
    void verifyKeyUsable() {
        String probe = "encryption-self-test";
        if (!probe.equals(convertToEntityAttribute(convertToDatabaseColumn(probe)))) {
            throw new IllegalStateException("암호화 자체 검증에 실패했습니다. encryption.key 설정을 확인하세요.");
        }
        log.info("건강정보 암호화 준비 완료 (AES-GCM)");
    }

    /**
     * 키를 가져온다.
     *
     * 빈 초기화 순서에 따라 이 컨버터가 키보다 먼저 쓰일 수 있다(리뷰 지적).
     * 그 경우 NPE 로 터지면 원인을 알기 어려우므로 무엇이 문제인지 명확히 알린다.
     */
    private static SecretKeySpec key() {
        SecretKeySpec key = secretKey;
        if (key == null) {
            throw new IllegalStateException(
                    "암호화 키가 아직 초기화되지 않았습니다. "
                    + "StringEncryptConverter 빈이 생성되기 전에 암·복호화가 호출됐습니다.");
        }
        return key;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BIT_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            // 평문이 그대로 저장되면 암호화를 하는 의미가 없으므로 실패시킨다.
            log.error("건강정보 암호화 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.ENCRYPTION_FAILED);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BIT_LENGTH, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);

        } catch (Exception e) {
            // 키가 바뀌었거나 값이 변조된 경우. 평문을 그대로 돌려주면
            // 암호문이 화면에 노출되므로 실패시킨다.
            log.error("건강정보 복호화 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.DECRYPTION_FAILED);
        }
    }
}
