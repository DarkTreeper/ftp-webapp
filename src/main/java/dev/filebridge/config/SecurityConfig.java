package dev.filebridge.config;

import java.util.List;

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
        String username = required(properties.getSecurity().getUsername(), "APP_USERNAME");
        String configuredHash = trimToNull(properties.getSecurity().getPasswordHash());
        String configuredPassword = trimToNull(properties.getSecurity().getPassword());

        if (configuredHash == null && configuredPassword == null) {
            throw new IllegalStateException("Set APP_PASSWORD_HASH or APP_PASSWORD before starting the application.");
        }

        String encodedPassword = configuredHash != null ? validateBcrypt(configuredHash) : passwordEncoder.encode(configuredPassword);

        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(encodedPassword)
                        .roles("USER")
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
                        .logoutSuccessUrl("/login?logout")
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

                List<String> missing = List.of(
                        propertyMissing(properties.getFtp().getHost(), "FTP_HOST"),
                        propertyMissing(properties.getFtp().getUsername(), "FTP_USERNAME"),
                        propertyMissing(properties.getFtp().getPassword(), "FTP_PASSWORD")
                ).stream().filter(value -> !value.isBlank()).toList();

                if (!missing.isEmpty()) {
                    throw new IllegalStateException("Missing required configuration: " + String.join(", ", missing));
                }
            }
        };
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
