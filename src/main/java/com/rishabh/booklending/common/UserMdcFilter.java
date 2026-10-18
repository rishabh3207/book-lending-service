package com.rishabh.booklending.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the logged-in username to every log line of the request. Registered inside the security
 * chain right after authentication (see SecurityConfig), because the user isn't known before that.
 * Deliberately not a @Component: Spring Boot would also register it as a plain servlet filter.
 *
 * It does not remove the key itself: Spring Security clears the user before the request ends,
 * and RequestIdFilter's access line is written after that. RequestIdFilter removes the key.
 */
public class UserMdcFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            MDC.put(MDC_KEY, auth.getName());
        }
        chain.doFilter(request, response);
    }
}
