package dev.filebridge.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class RemotePathPolicy {
    private final String root;

    public RemotePathPolicy(String configuredRoot) {
        this.root = normalizeAbsolute(configuredRoot == null || configuredRoot.isBlank() ? "/" : configuredRoot);
    }

    public String root() {
        return root;
    }

    public String normalize(String rawPath) {
        String path = rawPath == null || rawPath.isBlank() ? root : rawPath.trim();
        String absolute = path.startsWith("/") ? path : join(root, path);
        String normalized = normalizeAbsolute(absolute);
        if (!isWithinRoot(normalized)) {
            throw new SecurityException("Remote path is outside the configured FTP root.");
        }
        return normalized;
    }

    public String resolveChild(String basePath, String childName) {
        String safeName = safeName(childName);
        return normalize(join(normalize(basePath), safeName));
    }

    public String parent(String path) {
        String normalized = normalize(path);
        if (normalized.equals(root)) {
            return null;
        }
        int lastSlash = normalized.lastIndexOf('/');
        String parent = lastSlash <= 0 ? "/" : normalized.substring(0, lastSlash);
        return normalize(parent);
    }

    public String fileName(String path) {
        String normalized = normalize(path);
        if (normalized.equals("/")) {
            return "";
        }
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private boolean isWithinRoot(String normalized) {
        return root.equals("/") || normalized.equals(root) || normalized.startsWith(root + "/");
    }

    private static String normalizeAbsolute(String absolutePath) {
        if (!absolutePath.startsWith("/")) {
            throw new SecurityException("Remote path must be absolute after normalization.");
        }

        Deque<String> stack = new ArrayDeque<>();
        for (String segment : absolutePath.split("/")) {
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
                continue;
            }
            stack.addLast(segment);
        }

        if (stack.isEmpty()) {
            return "/";
        }
        List<String> segments = new ArrayList<>(stack);
        return "/" + String.join("/", segments);
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be empty.");
        }
        String trimmed = name.trim();
        if (".".equals(trimmed) || "..".equals(trimmed) || trimmed.contains("/") || trimmed.contains("\\")) {
            throw new SecurityException("Name contains invalid path characters.");
        }
        return trimmed;
    }

    private static String join(String base, String child) {
        if (base.endsWith("/")) {
            return base + child;
        }
        return base + "/" + child;
    }
}
