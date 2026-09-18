package com.devdhar.taskmanager.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Small helper so services can grab "who is making this request" without
 * digging through SecurityContextHolder everywhere. Populated by JwtAuthFilter
 * on every authenticated request.
 */
@Component
public class AuthUtils {

    public Long getCurrentUserId() {
        UserDetailsImpl principal =
                (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }

    public String getCurrentUsername() {
        UserDetailsImpl principal =
                (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUsername();
    }
}
