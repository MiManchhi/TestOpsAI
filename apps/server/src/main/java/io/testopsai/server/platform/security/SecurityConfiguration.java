package io.testopsai.server.platform.security;

import io.testopsai.server.platform.cache.CacheProperties;
import io.testopsai.server.platform.ratelimit.RateLimitFilter;
import io.testopsai.server.platform.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({
  JwtProperties.class,
  CacheProperties.class,
  RateLimitProperties.class
})
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
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/login")
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
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}
