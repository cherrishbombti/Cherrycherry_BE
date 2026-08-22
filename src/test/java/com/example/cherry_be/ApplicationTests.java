package com.example.cherry_be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// test 프로파일이 없으면 운영 설정의 ${DB_URL} 등을 해석하지 못해 컨텍스트가 죽는다.
// 설정은 src/test/resources/application-test.yaml 참고.
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
