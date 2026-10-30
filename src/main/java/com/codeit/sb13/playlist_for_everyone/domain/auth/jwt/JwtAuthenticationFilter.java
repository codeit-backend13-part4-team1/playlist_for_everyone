package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청의 Bearer 토큰을 검증하고 Spring Security 인증 정보를 설정한다.
 *
 * URL별 접근 권한 판단은 SecurityFilterChain에서 담당한다.
 */

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX =  "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    // 인증 정보를 이 객체 내부가 아닌 HttpServletRequest의 속성에 저장한다.
    // 기본 속성 이름을 사용하므로 보안 필터 체인의 요청 속성 저장소도 같은 값을 읽을 수 있다.
    // 저장 범위는 동일 HTTP 요청이며, HTTP 세션이나 Redis에 사용자 인증을 보관하는 설정이 아니다.
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserService userService, AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * 로그인, 회원가입은 기존 JWT 상태와 관계없이 처리한다.
     *
     * 로그인은 이메일, 비밀번호로 인증하고,
     * 회원가입 요청 값 검증을 거쳐 사용자를 생성한다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        // /mopl 같은 context path가 지정된 실행 환경도 고려한다.
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        return (contextPath + "/api/auth/sign-in").equals(requestUri)
                || (contextPath + "/api/users").equals(requestUri);

    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null) {
                Authentication authentication = createAuthentication(token, request);

                // 검증이 모두 끝난 뒤 새로운 Context에 인증 정보를 설정한다.
                SecurityContext context = SecurityContextHolder.createEmptyContext();

                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                // SecurityContextHolder는 현재 실행 스레드의 인증을 제공한다.
                // SSE complete 등의 종료 과정에서는 같은 HTTP 요청이 다른 스레드에서 ASYNC 재처리될 수 있다.
                // 이 필터는 기본적으로 ASYNC에서 토큰을 다시 검사하지 않으므로, 보안 필터가 요청 속성에서
                // 인증을 복원할 수 있도록 명시적으로 저장한다. 별도 @Async 작업에 인증을 전달하는 기능은 아니다.
                // 재연결은 새 HTTP 요청이므로 JWT를 다시 검증하며, 다른 인스턴스로 이 상태를 공유하지 않는다.
                securityContextRepository.saveContext(context, request, response);
            }
        } catch (JwtException | UserNotFoundException exception) {
            // 토큰 오류와 토큰에 해당하는 사용자의 미존재는 인증 실패로 처리한다.
            handleAuthenticationFailure(
                    request,
                    response,
                    new BadCredentialsException("유효하지 않은 인증 정보입니다.", exception)
            );

            return;
        } catch (AuthenticationException exception) {
            // 빈 Bearer 토큰, 계정 잠금 등 인증 과정에서 발생한 실패를 처리한다.
            handleAuthenticationFailure(request, response, exception);
            return;
        }
        // 인증 처리의 try-catch 바깥에서 호출한다.
        // 이후 서비스 등의 오류를 JWT 인증 실패로 잘못 변환하지 않기 위함이다.
        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에 Bearer 토큰을 추출한다.
     *
     * 헤더가 없거나 다른 인증 방식이면 null을 반환한다.
     * Bearer 방식인데 토큰이 비어 있으면 인증 실패로 처리한다.
     */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null) {
            return null;
        }

        // Bearer 만 전달된 경우도 토큰 누락으로 처리한다.
        if("Bearer".equalsIgnoreCase(authorization)) {
            throw new BadCredentialsException("Bearer 토큰이 비어 있습니다.");
        }

        // 인증 방식 이름은 대소문자를 구분하지 않는다.
        if (!authorization.regionMatches(
                true,
                0,
                BEARER_PREFIX,
                0,
                BEARER_PREFIX.length()
        )){
            return null;
        }

        String token = authorization
                .substring(BEARER_PREFIX.length())
                .trim();

        if (token.isEmpty()) {
            throw new BadCredentialsException("Bearer 토큰이 비어 있습니다.");
        }

        return token;
    }

    /**
     * 토큰과 현재 사용자 상태를 확인한 뒤 인증 객체를 만든다.
     */
    private Authentication createAuthentication(String token, HttpServletRequest request) {
        UUID userId = jwtTokenProvider.parseAccessToken(token);
        User user = userService.getById(userId);

        // 토큰이 유효하더라도 현재 계정이 잠겨 있으면 인증하지 않는다.
        if (user.isLocked()) {
            throw new LockedException("잠긴 계정입니다.");
        }

        // JWT 인증에서는 비밀번호를 비교하지 않는다.
        // 상위 User 생성자가 null 비밀번호를 허용하지 않아 빈 문자열을 전달한다.
        SecurityUser principal = new SecurityUser(
                user.getId(),
                user.getEmail(),
                "",
                user.getRole(),
                user.isLocked()
        );
        // 인증 정보에 불필요한 비밀번호 필드가 남지 않도록 제거한다.
        principal.eraseCredentials();

        // 토큰과 계정 상태를 검증했으므로 인증 완료 객체를 생성한다.
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        // 요청의 원격 주소 등 부가 정보를 연결 한다.
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        return authentication;
    }

    private void handleAuthenticationFailure(HttpServletRequest request,
                                             HttpServletResponse response,
                                             AuthenticationException exception) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, exception);

    }

}
