package com.tongue.server.auth;

import com.tongue.server.auth.service.AuthService;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.AuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthInterceptor(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicPath(request)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            AuthContext.set(authService.authenticateToken(authorization.substring("Bearer ".length())));
            return true;
        }

        if (authProperties.isAllowDevUserId()) {
            String userId = request.getHeader("X-User-Id");
            if (!StringUtils.hasText(userId)) {
                userId = request.getParameter("userId");
            }
            if (StringUtils.hasText(userId)) {
                AuthContext.set(new CurrentUser(Long.parseLong(userId), null, "USER"));
                return true;
            }
        }

        throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null);
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        AuthContext.clear();
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/sms/")
                || path.equals("/api/auth/sms/send")
                || path.equals("/api/auth/sms/login")
                || path.equals("/api/admin/auth/login")
                || path.startsWith("/api/public/profile-avatars/")
                || (path.equals("/api/doctors") && "GET".equalsIgnoreCase(request.getMethod()))
                || path.startsWith("/error");
    }
}
