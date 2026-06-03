package dev.filebridge.security;

import dev.filebridge.config.AppProperties;
import dev.filebridge.service.RemotePathPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private final AppProperties properties;

    public AuthorizationService(AppProperties properties) {
        this.properties = properties;
    }

    public UserAccess accessFor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("Unknown user.");
        }

        AppProperties.AppUser user = properties.getUsers().stream()
                .filter(candidate -> authentication.getName().equals(candidate.getUsername()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));

        FileRole role = FileRole.valueOf(user.getRole());
        String normalizedRoot = new RemotePathPolicy(user.getRootPath()).root();
        return new UserAccess(user.getUsername(), role, normalizedRoot);
    }

    public String startPathFor(Authentication authentication, String requestedPath) {
        UserAccess access = accessFor(authentication);
        String path = requestedPath == null || requestedPath.isBlank() ? access.rootPath() : requestedPath;
        return normalizeAuthorizedPath(authentication, Permission.READ, path);
    }

    public String normalizeAuthorizedPath(Authentication authentication, Permission permission, String requestedPath) {
        UserAccess access = accessFor(authentication);
        if (!access.role().has(permission)) {
            throw new AccessDeniedException("Permission denied.");
        }

        RemotePathPolicy userPathPolicy = new RemotePathPolicy(access.rootPath());
        return userPathPolicy.normalize(requestedPath);
    }

    public String parentPath(Authentication authentication, String requestedPath) {
        UserAccess access = accessFor(authentication);
        RemotePathPolicy userPathPolicy = new RemotePathPolicy(access.rootPath());
        return userPathPolicy.parent(requestedPath);
    }
}
