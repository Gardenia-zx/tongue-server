package com.tongue.server.auth;

import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;

public final class AuthContext {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<CurrentUser>();

    private AuthContext() {
    }

    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    public static CurrentUser get() {
        return CURRENT.get();
    }

    public static Long requireUserId() {
        CurrentUser user = CURRENT.get();
        if (user == null || user.userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null);
        }
        return user.userId;
    }

    public static void requireRole(String role) {
        CurrentUser user = CURRENT.get();
        if (user == null || user.role == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null);
        }
        if (!role.equals(user.role) && !"ADMIN".equals(user.role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有访问权限", null);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
