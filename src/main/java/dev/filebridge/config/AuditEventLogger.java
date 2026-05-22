package dev.filebridge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuditEventLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditEventLogger.class);

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("audit login-success user={}", event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        log.warn("audit login-failure principal={}", principal);
    }
}
