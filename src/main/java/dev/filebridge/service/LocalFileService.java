package dev.filebridge.service;

import dev.filebridge.config.AppProperties;
import dev.filebridge.model.DirectoryListing;
import dev.filebridge.model.FileEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileService {
    private final Path root;
    private final long maxUploadBytes;

    public LocalFileService(AppProperties properties) throws IOException {
        Files.createDirectories(properties.getLocalRoot());
        this.root = properties.getLocalRoot().toAbsolutePath().normalize().toRealPath();
        this.maxUploadBytes = properties.getMaxUploadBytes();
    }

    public Path root() {
        return root;
    }

    public DirectoryListing list(String relativePath) throws IOException {
        Path directory = resolve(relativePath);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Local path is not a directory.");
        }

        try (Stream<Path> stream = Files.list(directory)) {
            List<FileEntry> entries = stream
                    .map(this::toEntry)
                    .sorted(Comparator.comparing(FileEntry::directory).reversed().thenComparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            String normalized = toRelative(directory);
            return new DirectoryListing(normalized, parentOf(normalized), entries);
        }
    }

    public Path resolveDownload(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Only regular files can be downloaded.");
        }
        return file;
    }

    public Path resolveExistingFile(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Local source must be a regular file.");
        }
        return file;
    }

    public Path resolveExistingDirectory(String relativePath) {
        Path directory = resolve(relativePath);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Local target must be a directory.");
        }
        return directory;
    }

    public void createDirectory(String parentRelativePath, String directoryName) throws IOException {
        Path parent = resolveExistingDirectory(parentRelativePath);
        Path target = parent.resolve(safeName(directoryName)).normalize();
        ensureInsideRoot(target);
        Files.createDirectory(target);
    }

    public void delete(String relativePath) throws IOException {
        Path target = resolve(relativePath);
        if (target.equals(root)) {
            throw new SecurityException("The configured local root cannot be deleted.");
        }
        Files.delete(target);
    }

    public Path saveMultipart(String targetDirectoryRelativePath, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file first.");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("File is larger than the configured upload limit.");
        }

        Path targetDirectory = resolveExistingDirectory(targetDirectoryRelativePath);
        String originalName = file.getOriginalFilename() == null ? "upload.bin" : Path.of(file.getOriginalFilename()).getFileName().toString();
        Path target = uniqueTarget(targetDirectory, safeName(originalName));
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target);
        }
        return target;
    }

    public Path createUniqueFileTarget(String targetDirectoryRelativePath, String preferredName) throws IOException {
        Path targetDirectory = resolveExistingDirectory(targetDirectoryRelativePath);
        return uniqueTarget(targetDirectory, safeName(preferredName));
    }

    private FileEntry toEntry(Path path) {
        try {
            boolean directory = Files.isDirectory(path);
            long size = directory ? 0L : Files.size(path);
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            return new FileEntry(path.getFileName().toString(), toRelative(path), directory, size, modified);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read local file metadata.");
        }
    }

    private Path resolve(String relativePath) {
        String clean = relativePath == null ? "" : relativePath.trim();
        if (clean.startsWith("/") || clean.startsWith("\\") || clean.contains("\0")) {
            throw new SecurityException("Local path is invalid.");
        }
        Path target = root.resolve(clean).normalize();
        ensureInsideRoot(target);
        return target;
    }

    private void ensureInsideRoot(Path target) {
        if (!target.startsWith(root)) {
            throw new SecurityException("Local path is outside the configured root.");
        }
    }

    private String toRelative(Path path) {
        ensureInsideRoot(path.normalize());
        String value = root.relativize(path.normalize()).toString();
        return value.replace('\\', '/');
    }

    private static String parentOf(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path parent = Path.of(relativePath).getParent();
        return parent == null ? "" : parent.toString().replace('\\', '/');
    }

    private Path uniqueTarget(Path directory, String safeName) throws IOException {
        Path target = directory.resolve(safeName).normalize();
        ensureInsideRoot(target);
        if (!Files.exists(target)) {
            return target;
        }

        String base = safeName;
        String extension = "";
        int dot = safeName.lastIndexOf('.');
        if (dot > 0) {
            base = safeName.substring(0, dot);
            extension = safeName.substring(dot);
        }

        for (int i = 1; i < 10_000; i++) {
            Path candidate = directory.resolve(base + " (" + i + ")" + extension).normalize();
            ensureInsideRoot(candidate);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not create a unique local file name.");
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be empty.");
        }
        String trimmed = name.trim();
        if (".".equals(trimmed) || "..".equals(trimmed) || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("\0")) {
            throw new SecurityException("Name contains invalid path characters.");
        }
        return trimmed;
    }
}
