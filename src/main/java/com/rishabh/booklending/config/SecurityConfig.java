package com.rishabh.booklending.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        // 401/403 raised inside the filter chain never reach a controller, so hand them to
        // GlobalExceptionHandler. That keeps one Problem Details format for every error.
        AuthenticationEntryPoint unauthenticated = (req, res, ex) -> resolver.resolveException(req, res, null, ex);
        AccessDeniedHandler denied = (req, res, ex) -> resolver.resolveException(req, res, null, ex);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health/**", "/actuator/info",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("LIBRARIAN")
                .requestMatchers(HttpMethod.GET, "/api/books/**").authenticated()
                .requestMatchers("/api/books/**").hasRole("LIBRARIAN")
                .requestMatchers(HttpMethod.GET, "/api/members/me", "/api/members/*/loans").authenticated()
                .requestMatchers("/api/members/**").hasRole("LIBRARIAN")
                .requestMatchers("/api/loans/**").authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(unauthenticated).accessDeniedHandler(denied))
            .httpBasic(basic -> basic.authenticationEntryPoint(unauthenticated));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityUsersProperties props) {
        List<UserDetails> users = props.users().stream()
                .map(u -> User.withUsername(u.username())
                        .password(u.password())
                        .roles(u.roles().toArray(String[]::new))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }
}