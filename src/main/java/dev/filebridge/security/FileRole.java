package dev.filebridge.security;

import java.util.Set;

public enum FileRole {
    VIEWER(Set.of(Permission.READ, Permission.DOWNLOAD)),
    EDITOR(Set.of(
            Permission.READ,
            Permission.DOWNLOAD,
            Permission.UPLOAD,
            Permission.CREATE_DIRECTORY,
            Permission.RENAME
    )),
    ADMIN(Set.of(
            Permission.READ,
            Permission.DOWNLOAD,
            Permission.UPLOAD,
            Permission.CREATE_DIRECTORY,
            Permission.RENAME,
            Permission.DELETE
    ));

    private final Set<Permission> permissions;

    FileRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
