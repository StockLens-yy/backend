package com.example.Stock;

import com.example.Stock.service.CustomOAuth2UserService;
import com.example.Stock.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .userDetailsService(customUserDetailsService)
                .csrf(csrf -> csrf.disable())
                // 로그인·로그아웃처럼 Security 필터가 직접 응답하는 요청에도 AppConfig 의 CORS 설정을 적용한다
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/stocks/*/briefing", "/api/stocks/watchlist/**").authenticated()
                        .requestMatchers("/api/portfolio/**").authenticated()
                        .anyRequest().permitAll()
                )
                // /api/** 는 REST 이므로 로그인 폼으로 리다이렉트(302)하지 않고 401 을 준다
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        (req, res, authEx) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
                        },
                        request -> request.getRequestURI().startsWith("/api/")
                ))
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler((req, res, ex) -> {
                            res.setStatus(401);
                            res.getWriter().write("로그인 실패");
                        })
                        .successHandler((req, res, auth) -> {
                            res.setStatus(200);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"로그인 성공\"}");
                        })
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("http://localhost:5173", true)
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            res.setStatus(200);
                            res.getWriter().write("로그아웃 성공");
                        })
                );

        return http.build();
    }
}