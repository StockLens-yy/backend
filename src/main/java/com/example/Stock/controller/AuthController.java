package com.example.Stock.controller;

import com.example.Stock.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // 회원가입
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            userService.register(
                    body.get("email"),
                    body.get("password"),
                    body.get("name")
            );
            return ResponseEntity.ok("회원가입 성공");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 현재 로그인 유저 정보
    @GetMapping("/me")
    public ResponseEntity<?> me(@org.springframework.security.core.annotation.AuthenticationPrincipal Object principal) {
        if (principal == null) return ResponseEntity.status(401).body("로그인 필요");

        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User o) {
            return ResponseEntity.ok(Map.of(
                    "name", o.getAttribute("name"),
                    "email", o.getAttribute("email"),
                    "picture", o.getAttribute("picture") != null ? o.getAttribute("picture") : ""
            ));
        } else if (principal instanceof org.springframework.security.core.userdetails.User u) {
            return ResponseEntity.ok(Map.of(
                    "email", u.getUsername(),
                    "name", u.getUsername()
            ));
        }
        return ResponseEntity.status(401).body("로그인 필요");
    }
}