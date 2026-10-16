package com.example.Stock.util;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 인증 주체에서 이메일을 꺼낸다. 로컬 로그인과 구글 OAuth2 로그인 형태가 서로 다르다. */
public final class PrincipalUtils {

    private PrincipalUtils() {
    }

    /** 이메일. 로그인하지 않았거나 알 수 없는 형태면 null. */
    public static String emailOf(Object principal) {
        if (principal instanceof UserDetails u) {
            return u.getUsername();
        }
        if (principal instanceof OAuth2User o) {
            return o.getAttribute("email");
        }
        return null;
    }
}
