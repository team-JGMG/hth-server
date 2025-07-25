package org.bobj.user.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    // UserMapper 의존성을 제거합니다.

    @Value("${custom.oauth2.redirect-uri}")
    private String frontendRedirectUri;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 Login 성공. '사전 인증' 임시 JWT 발급을 시작합니다.");


        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // CustomOAuth2UserService에서 담아준 정보 추출
        String provider = (String) attributes.get("provider");
        String providerId = attributes.get("id").toString();
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        String email = (String) kakaoAccount.get("email");
        String nickname = ((Map<String, String>) kakaoAccount.get("profile")).get("nickname");

        log.info("임시 JWT 생성을 위한 정보: email={}, nickname={}, provider={}, providerId={}", email, nickname, provider, providerId);

        // DB 조회 없이, 소셜 정보만으로 '사전 인증' 상태의 임시 JWT를 생성합니다.
        // 참고: 이 로직이 동작하려면 JwtTokenProvider에 createPreAuthToken 메소드가 필요합니다.
        String preAuthToken = jwtTokenProvider.createPreAuthToken(email, nickname, provider, providerId);

        log.info("사전 인증 토큰 생성 완료.");


        // 프론트엔드로 이 임시 토큰을 전달합니다.
        String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri) // 주입받은 값 사용
                .queryParam("token", preAuthToken)
                .queryParam("status", "PRE_AUTH")
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}