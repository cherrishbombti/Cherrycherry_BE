package com.example.cherry_be;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class Application {

	// LocalDateTime.now()가 참조하는 JVM 기본 타임존을 KST로 고정
	// (배포 환경이 UTC면 저장·비교·날짜 경계가 모두 9시간 밀림)
	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();

		dotenv.entries().forEach(entry -> {
			System.setProperty(entry.getKey(), entry.getValue());
		});

		// 3. 이제 환경 변수가 준비되었으니 스프링을 실행합니다.
		SpringApplication.run(Application.class, args);
	}
}