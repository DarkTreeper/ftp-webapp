package dev.filebridge.security;

public record UserAccess(String username, FileRole role, String rootPath) {
}
