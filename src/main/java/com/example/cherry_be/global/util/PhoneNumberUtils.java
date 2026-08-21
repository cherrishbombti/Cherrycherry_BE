package com.example.cherry_be.global.util;

/**
 * 전화번호 정규화.
 *
 * 저장은 숫자만 남긴 형태("01012345678")로 통일한다.
 *  - 하이픈 유무에 따라 같은 번호가 다르게 저장되는 것을 막는다
 *  - SMS 제공사 API 는 대부분 하이픈 없는 형식을 요구한다
 *  - 표시용 하이픈은 프론트에서 붙인다
 *
 * 타입은 String 을 유지한다. 숫자 타입으로 바꾸면 앞자리 0 이 사라진다.
 */
public final class PhoneNumberUtils {

    private PhoneNumberUtils() {
    }

    /** 숫자를 제외한 모든 문자를 제거한다. null 은 그대로 반환. */
    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
