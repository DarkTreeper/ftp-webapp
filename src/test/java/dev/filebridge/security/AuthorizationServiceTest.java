package dev.filebridge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.filebridge.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class AuthorizationServiceTest {

    @Test
    void internUserStaysInsideAssignedRootPath() {
        AuthorizationService service = new AuthorizationService(properties());
        Authentication intern = authentication("intern");

        assertThat(service.startPathFor(intern, null)).isEqualTo("/Intern");
        assertThat(service.normalizeAuthorizedPath(intern, Permission.READ, "/Intern/Reports")).isEqualTo("/Intern/Reports");
        assertThat(service.parentPath(intern, "/Intern")).isNull();
    }

    @Test
    void internUserCannotEscapeIntoExternArea() {
        AuthorizationService service = new AuthorizationService(properties());
        Authentication intern = authentication("intern");

        assertThatThrownBy(() -> service.normalizeAuthorizedPath(intern, Permission.READ, "/Extern"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.normalizeAuthorizedPath(intern, Permission.READ, "/Intern/../Extern"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void viewerCannotUpload() {
        AuthorizationService service = new AuthorizationService(properties());
        Authentication intern = authentication("intern");

        assertThatThrownBy(() -> service.normalizeAuthorizedPath(intern, Permission.UPLOAD, "/Intern"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void viewerCannotDelete() {
        AuthorizationService service = new AuthorizationService(properties());
        Authentication intern = authentication("intern");

        assertThatThrownBy(() -> service.normalizeAuthorizedPath(intern, Permission.DELETE, "/Intern/test.txt"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanDeleteInsideRoot() {
        AuthorizationService service = new AuthorizationService(properties());
        Authentication admin = authentication("admin");

        assertThat(service.normalizeAuthorizedPath(admin, Permission.DELETE, "/Intern/test.txt"))
                .isEqualTo("/Intern/test.txt");
    }

    private static Authentication authentication(String username) {
        return new UsernamePasswordAuthenticationToken(username, "n/a");
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();

        AppProperties.AppUser admin = new AppProperties.AppUser();
        admin.setUsername("admin");
        admin.setRole("ADMIN");
        admin.setRootPath("/");

        AppProperties.AppUser intern = new AppProperties.AppUser();
        intern.setUsername("intern");
        intern.setRole("VIEWER");
        intern.setRootPath("/Intern");

        AppProperties.AppUser extern = new AppProperties.AppUser();
        extern.setUsername("extern");
        extern.setRole("EDITOR");
        extern.setRootPath("/Extern");

        properties.setUsers(List.of(admin, intern, extern));
        return properties;
    }
}
