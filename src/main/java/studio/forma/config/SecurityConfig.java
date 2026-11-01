package studio.forma.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {
  @Bean
  UserDetailsService users(
      @Value("${forma.admin.username}") String username,
      @Value("${forma.admin.password}") String password) {
    var users = new InMemoryUserDetailsManager();
    if (!password.isBlank()) {
      if (password.length() < 12
          || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
          || username.isBlank()) {
        throw new IllegalArgumentException(
            "Set FORMA_ADMIN_PASSWORD to 12–72 characters and a nonempty username.");
      }
      users.createUser(
          User.withUsername(username)
              .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
              .roles("STUDIO")
              .build());
    }
    return users;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.addFilterBefore(
            new AbuseProtectionFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/studio.html", "/api/admin/**")
                    .hasRole("STUDIO")
                    .anyRequest()
                    .permitAll())
        .csrf(Customizer.withDefaults())
        .formLogin(
            login ->
                login
                    .loginPage("/login.html")
                    .loginProcessingUrl("/login")
                    .failureUrl("/login.html?error")
                    .defaultSuccessUrl("/studio.html", true))
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .exceptionHandling(
            errors ->
                errors
                    .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request ->
                            request
                                .getRequestURI()
                                .substring(request.getContextPath().length())
                                .startsWith("/api/"))
                    .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login.html"), request -> true))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; font-src"
                                    + " 'self'; img-src 'self' data:; connect-src 'self';"
                                    + " frame-ancestors 'none'; base-uri 'self'; form-action"
                                    + " 'self'"))
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
        .build();
  }
}
