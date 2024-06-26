package com.sparta.ottoon.auth.filter;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ottoon.auth.dto.LoginRequestDto;
import com.sparta.ottoon.auth.entity.User;
import com.sparta.ottoon.auth.jwt.JwtUtil;
import com.sparta.ottoon.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j(topic = "JwtAuthenticationFilter")
@Component
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final UserRepository userRepository;

    public JwtAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;

        // 이 경로로 요청이 들어올 경우 인증 필터가 동작한다.
        setFilterProcessesUrl("/api/auth/login");
    }

    /**
     * 사용자 인증을 시도하는 메서드
     * 클라이언트에서 로그인 요청이 들어왔을 때 호출되어 사용자의 인증을 처리
     * @param request
     * @param response
     * @return
     * @throws AuthenticationException
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {
            // 클라이언트가 전송한 JSON 형식의 로그인 요청 데이터를 'LoginRequestDto' 객체로 매핑
            LoginRequestDto requestDto = new ObjectMapper().readValue(request.getInputStream(), LoginRequestDto.class);

            // spring security의 인증 매니저를 통해 인증 시도
            return getAuthenticationManager().authenticate(
                    new UsernamePasswordAuthenticationToken(requestDto.getUsername(), requestDto.getPassword(), null)
            );
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * 로그인이 성공적으로 완료되었을 때 실행
     * @param request
     * @param response
     * @param chain
     * @param authResult
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException {
        String username = ((UserDetails) authResult.getPrincipal()).getUsername();

        // access token과 refresh 토큰 생성
        String token = JwtUtil.createToken(username, JwtUtil.ACCESS_TOKEN_EXPIRATION);
        String refresh = JwtUtil.createToken(username, JwtUtil.REFRESH_TOKEN_EXPIRATION);

        // refresh token 저장
        saveRefreshToken(username, refresh);

        response.addHeader(JwtUtil.AUTHORIZATION_HEADER, token); // response header에 access token 넣기

        responseSetting(response, 200, "로그인에 성공하였습니다.");
    }

    /**
     * 로그인이 실패했을 때 실행
     * @param request
     * @param response
     * @param failed
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        responseSetting(response, 401, "로그인에 실패하였습니다.");
    }

    /**
     * refresh token을 user DB에 저장
     * @param username
     * @param refresh
     */
    private void saveRefreshToken(String username, String refresh) {
        User user = userRepository.findByUsername(username).orElseThrow(
                () -> new IllegalArgumentException("not found user")
        );
        user.updateRefresh(refresh); // 사용자의 refresh token을 업데이트 해준다.
        userRepository.save(user);
    }

    private void responseSetting(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);

    }
}