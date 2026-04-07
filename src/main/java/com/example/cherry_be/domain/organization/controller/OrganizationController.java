package com.example.cherry_be.domain.organization.controller;

import com.example.cherry_be.domain.organization.dto.LoginRequest;
import com.example.cherry_be.domain.organization.dto.SignUpRequest;
import com.example.cherry_be.domain.organization.service.OrganizationService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // 이 클래스가 REST API 안내데스크 역할을 한다고 선언
@RequestMapping("/api/org") // 이 컨트롤러의 기본 주소 설정
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * 기관 회원가입 API
     * [POST] /api/org/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody SignUpRequest request) {
        // 1. 리액트에서 보낸 DTO 데이터(상자)를 열어서 Service 부서로 넘겨줌
        Long savedOrgId = organizationService.signUp(
                request.getOrgId(),
                request.getPassword(),
                request.getName()
        );

        // 2. Service 부서가 일을 성공적으로 마치면 리액트에게 성공 메시지와 상태 코드 200(OK)을 반환
        return ResponseEntity.ok("회원가입이 완료되었습니다. 고유 ID: " + savedOrgId);
    }

    /**
     * 기관 로그인 API
     * [POST] /api/org/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {

        // 1. Service 부서로 아이디와 비밀번호를 넘겨서 검증받고, 성공하면 토큰을 받아옵니다.
        String token = organizationService.login(request.getOrgId(), request.getPassword());

        // 2. 리액트(프론트엔드)가 쉽게 꺼내 쓸 수 있도록 {"token": "eyJhbGci..."} 형태의 JSON으로 포장합니다.
        Map<String, String> response = new HashMap<>();
        response.put("token", token);

        // 3. 성공 상태 코드(200 OK)와 함께 포장된 토큰을 응답으로 보냅니다.
        return ResponseEntity.ok(response);
    }

}