package dev.filebridge.config;

import dev.filebridge.security.FileRole;
import dev.filebridge.service.RemotePathPolicy;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(AppProperties properties, PasswordEncoder passwordEncoder) {
        if (!properties.getUsers().isEmpty()) {
            List<org.springframework.security.core.userdetails.UserDetails> users = properties.getUsers().stream()
                    .map(appUser -> User.withUsername(required(appUser.getUsername(), "filebridge.users[].username"))
                            .password(resolvePassword(appUser.getPasswordHash(), appUser.getPassword(), passwordEncoder))
                            .roles(validatedRole(appUser.getRole()).name())
                            .build())
                    .toList();
            return new InMemoryUserDetailsManager(users);
        }

        String username = required(properties.getSecurity().getUsername(), "APP_USERNAME");
        String encodedPassword = resolvePassword(
                properties.getSecurity().getPasswordHash(),
                properties.getSecurity().getPassword(),
                passwordEncoder
        );

        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(encodedPassword)
                        .roles(FileRole.ADMIN.name())
                        .build()
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.sendRedirect("/login"))
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                "img-src 'self' data:; " +
                                "style-src 'self'; " +
                                "script-src 'self'; " +
                                "object-src 'none'; " +
                                "base-uri 'self'; " +
                                "frame-ancestors 'none'; " +
                                "form-action 'self'"
                        ))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                )
                .build();
    }

    @Bean
    public ApplicationRunner startupValidation(AppProperties properties) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                if (properties.isDemoMode()) {
                    return;
                }

                if (!properties.getUsers().isEmpty()) {
                    properties.getUsers().forEach(this::validateConfiguredUser);
                    validateAdminExists(properties);
                    return;
                }

                List<String> missing = List.of(
                        propertyMissing(properties.getFtp().getHost(), "FTP_HOST"),
                        propertyMissing(properties.getFtp().getUsername(), "FTP_USERNAME"),
                        propertyMissing(properties.getFtp().getPassword(), "FTP_PASSWORD")
                ).stream().filter(value -> !value.isBlank()).toList();

                if (!missing.isEmpty()) {
                    throw new IllegalStateException("Missing required configuration: " + String.join(", ", missing));
                }
            }

            private void validateConfiguredUser(AppProperties.AppUser user) {
                required(user.getUsername(), "filebridge.users[].username");
                validatedRole(user.getRole());
                new RemotePathPolicy(required(user.getRootPath(), "filebridge.users[].root-path"));
                resolvePassword(user.getPasswordHash(), user.getPassword(), null);
            }

            private void validateAdminExists(AppProperties properties) {
                boolean hasAdmin = properties.getUsers().stream()
                        .anyMatch(user -> "admin".equals(user.getUsername()) && "ADMIN".equals(trimToNull(user.getRole())));
                if (!hasAdmin) {
                    throw new AccessDeniedException("Configured users must include admin with ADMIN role.");
                }
            }
        };
    }

    private static String resolvePassword(String configuredHash, String configuredPassword, PasswordEncoder passwordEncoder) {
        String hash = trimToNull(configuredHash);
        String password = trimToNull(configuredPassword);

        if (hash == null && password == null) {
            throw new IllegalStateException("Set a password hash or password for each configured user.");
        }
        if (hash != null) {
            return validateBcrypt(hash);
        }
        if (passwordEncoder == null) {
            return password;
        }
        return passwordEncoder.encode(password);
    }

    private static FileRole validatedRole(String role) {
        String trimmedRole = required(role, "filebridge.users[].role");
        try {
            return FileRole.valueOf(trimmedRole);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unsupported role configured: " + trimmedRole);
        }
    }

    private static String required(String value, String envName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalStateException("Set " + envName + " before starting the application.");
        }
        return trimmed;
    }

    private static String validateBcrypt(String hash) {
        if (!hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
            throw new IllegalStateException("APP_PASSWORD_HASH must be a BCrypt hash.");
        }
        return hash;
    }

    private static String propertyMissing(String value, String envName) {
        return trimToNull(value) == null ? envName : "";
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
