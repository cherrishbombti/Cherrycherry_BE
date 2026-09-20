package com.example.cherry_be.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberUtilsTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "010-1234-5678, 01012345678",
            "010 1234 5678, 01012345678",
            "01012345678,   01012345678",
            "(053)950-5114, 0539505114",
            "+82 10-1234-5678, 821012345678"
    })
    @DisplayName("숫자만 남긴다 — 같은 번호가 표기 차이로 다르게 저장되지 않도록")
    void normalize(String input, String expected) {
        assertThat(PhoneNumberUtils.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("앞자리 0 이 사라지지 않는다 (숫자 타입으로 바꾸면 안 되는 이유)")
    void keepsLeadingZero() {
        assertThat(PhoneNumberUtils.normalize("010-1234-5678")).startsWith("0");
    }

    @Test
    @DisplayName("null 은 그대로, 숫자가 하나도 없으면 null")
    void nullAndBlank() {
        assertThat(PhoneNumberUtils.normalize(null)).isNull();
        assertThat(PhoneNumberUtils.normalize("---")).isNull();
        assertThat(PhoneNumberUtils.normalize("")).isNull();
    }
}
