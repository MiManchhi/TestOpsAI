package io.testopsai.server.platform.security;

import io.testopsai.server.platform.ratelimit.RateLimitFilter;
import io.testopsai.server.platform.ratelimit.RateLimitProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
class SecurityConfiguration {
  private final JwtAuthenticationFilter jwtFilter;
  private final RateLimitFilter rateLimitFilter;
  private final SecurityExceptionHandler securityExceptionHandler;

  SecurityConfiguration(
      JwtAuthenticationFilter jwtFilter,
      RateLimitFilter rateLimitFilter,
      SecurityExceptionHandler securityExceptionHandler) {
    this.jwtFilter = jwtFilter;
    this.rateLimitFilter = rateLimitFilter;
    this.securityExceptionHandler = securityExceptionHandler;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        new AndRequestMatcher(
                            new DispatcherTypeRequestMatcher(DispatcherType.ASYNC),
                            PathPatternRequestMatcher.withDefaults()
                                .matcher(
                                    HttpMethod.GET,
                                    "/api/v3/projects/{projectId}/turns/{turnId}/events")))
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v2/evidence/resolve")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/internal/v1/objects/*/content")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler))
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable())
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new AccountPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}
