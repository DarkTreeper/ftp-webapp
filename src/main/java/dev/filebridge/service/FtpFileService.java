package dev.filebridge.service;

import dev.filebridge.config.AppProperties;
import dev.filebridge.model.DirectoryListing;
import dev.filebridge.model.FileEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FtpFileService {
    private final AppProperties.Ftp ftpProperties;
    private final RemotePathPolicy remotePathPolicy;
    private final boolean demoMode;
    private final Path demoRemoteRoot;

    public FtpFileService(AppProperties properties) throws IOException {
        this.ftpProperties = properties.getFtp();
        this.remotePathPolicy = new RemotePathPolicy(properties.getRemoteRoot());
        this.demoMode = properties.isDemoMode();
        if (demoMode) {
            Files.createDirectories(properties.getDemoRemoteRoot());
            this.demoRemoteRoot = properties.getDemoRemoteRoot().toAbsolutePath().normalize().toRealPath();
        } else {
            this.demoRemoteRoot = null;
        }
    }

    public String root() {
        return remotePathPolicy.root();
    }

    public DirectoryListing list(String remotePath) throws IOException {
        String normalized = remotePathPolicy.normalize(remotePath);
        if (demoMode) {
            Path directory = resolveDemoPath(normalized);
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Remote path is not a directory.");
            }
            try (Stream<Path> stream = Files.list(directory)) {
                List<FileEntry> entries = stream
                        .map(path -> toDemoEntry(normalized, path))
                        .sorted(Comparator.comparing(FileEntry::directory).reversed().thenComparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER))
                        .toList();
                return new DirectoryListing(normalized, remotePathPolicy.parent(normalized), entries);
            }
        }

        return withClient(client -> {
            FTPFile[] files = client.listFiles(normalized);
            ensurePositive(client, "Could not list remote directory.");
            List<FileEntry> entries = Arrays.stream(files)
                    .filter(file -> !".".equals(file.getName()) && !"..".equals(file.getName()))
                    .map(file -> toEntry(normalized, file))
                    .sorted(Comparator.comparing(FileEntry::directory).reversed().thenComparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new DirectoryListing(normalized, remotePathPolicy.parent(normalized), entries);
        });
    }

    public void createDirectory(String parentRemotePath, String directoryName) throws IOException {
        String target = remotePathPolicy.resolveChild(parentRemotePath, directoryName);
        if (demoMode) {
            Files.createDirectory(resolveDemoPath(target));
            return;
        }

        withClient(client -> {
            if (!client.makeDirectory(target)) {
                ensurePositive(client, "Could not create remote directory.");
            }
            return null;
        });
    }

    public void delete(String remotePath, boolean directory) throws IOException {
        String normalized = remotePathPolicy.normalize(remotePath);
        if (normalized.equals(remotePathPolicy.root())) {
            throw new SecurityException("The configured FTP root cannot be deleted.");
        }
        if (demoMode) {
            Files.delete(resolveDemoPath(normalized));
            return;
        }

        withClient(client -> {
            boolean deleted = directory ? client.removeDirectory(normalized) : client.deleteFile(normalized);
            if (!deleted) {
                ensurePositive(client, "Could not delete remote item.");
            }
            return null;
        });
    }

    public void upload(Path localFile, String targetRemoteDirectory) throws IOException {
        String remoteDirectory = remotePathPolicy.normalize(targetRemoteDirectory);
        if (demoMode) {
            Path targetDirectory = resolveDemoPath(remoteDirectory);
            Files.copy(localFile, uniqueDemoTarget(targetDirectory, localFile.getFileName().toString()), StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }

        withClient(client -> {
            String target = uniqueRemotePath(client, remoteDirectory, localFile.getFileName().toString());
            try (InputStream inputStream = Files.newInputStream(localFile)) {
                if (!client.storeFile(target, inputStream)) {
                    ensurePositive(client, "Could not upload file to FTP server.");
                }
            }
            return null;
        });
    }

    public void upload(MultipartFile file, String targetRemoteDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file first.");
        }

        String remoteDirectory = remotePathPolicy.normalize(targetRemoteDirectory);
        String originalName = file.getOriginalFilename() == null
                ? "upload.bin"
                : Path.of(file.getOriginalFilename()).getFileName().toString();

        if (demoMode) {
            Path targetDirectory = resolveDemoPath(remoteDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, uniqueDemoTarget(targetDirectory, originalName));
            }
            return;
        }

        withClient(client -> {
            String target = uniqueRemotePath(client, remoteDirectory, originalName);
            try (InputStream inputStream = file.getInputStream()) {
                if (!client.storeFile(target, inputStream)) {
                    ensurePositive(client, "Could not upload file to FTP server.");
                }
            }
            return null;
        });
    }

    public void download(String remoteFilePath, Path localTarget) throws IOException {
        String normalized = remotePathPolicy.normalize(remoteFilePath);
        if (demoMode) {
            Files.copy(resolveDemoPath(normalized), localTarget, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }

        withClient(client -> {
            try (OutputStream outputStream = Files.newOutputStream(localTarget)) {
                if (!client.retrieveFile(normalized, outputStream)) {
                    Files.deleteIfExists(localTarget);
                    ensurePositive(client, "Could not download file from FTP server.");
                }
            }
            return null;
        });
    }

    public void streamDownload(String remoteFilePath, OutputStream outputStream) throws IOException {
        String normalized = remotePathPolicy.normalize(remoteFilePath);
        if (demoMode) {
            try (InputStream inputStream = Files.newInputStream(resolveDemoPath(normalized))) {
                inputStream.transferTo(outputStream);
            }
            return;
        }

        withClient(client -> {
            if (!client.retrieveFile(normalized, outputStream)) {
                ensurePositive(client, "Could not download file from FTP server.");
            }
            return null;
        });
    }

    public String fileName(String remotePath) {
        return remotePathPolicy.fileName(remotePath);
    }

    private FileEntry toEntry(String parent, FTPFile file) {
        Instant modified = file.getTimestamp() == null ? null : file.getTimestamp().toInstant();
        return new FileEntry(
                file.getName(),
                remotePathPolicy.resolveChild(parent, file.getName()),
                file.isDirectory(),
                file.isDirectory() ? 0L : file.getSize(),
                modified
        );
    }

    private FileEntry toDemoEntry(String parent, Path path) {
        try {
            boolean directory = Files.isDirectory(path);
            return new FileEntry(
                    path.getFileName().toString(),
                    remotePathPolicy.resolveChild(parent, path.getFileName().toString()),
                    directory,
                    directory ? 0L : Files.size(path),
                    Files.getLastModifiedTime(path).toInstant()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read demo remote file metadata.");
        }
    }

    private Path resolveDemoPath(String remotePath) {
        if (!demoMode) {
            throw new IllegalStateException("Demo mode is not active.");
        }

        String normalized = remotePathPolicy.normalize(remotePath);
        String relative = demoRelativePath(normalized);
        Path target = demoRemoteRoot.resolve(relative).normalize();
        if (!target.startsWith(demoRemoteRoot)) {
            throw new SecurityException("Demo remote path is outside the configured root.");
        }
        return target;
    }

    private String demoRelativePath(String normalizedRemotePath) {
        String root = remotePathPolicy.root();
        if (root.equals("/")) {
            return normalizedRemotePath.equals("/") ? "" : normalizedRemotePath.substring(1);
        }
        if (normalizedRemotePath.equals(root)) {
            return "";
        }
        return normalizedRemotePath.substring(root.length() + 1);
    }

    private Path uniqueDemoTarget(Path directory, String originalName) throws IOException {
        String safeName = originalName.replace("/", "_").replace("\\", "_").trim();
        if (safeName.isBlank() || ".".equals(safeName) || "..".equals(safeName)) {
            safeName = "upload.bin";
        }

        Path target = directory.resolve(safeName).normalize();
        ensureInsideDemoRoot(target);
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
            ensureInsideDemoRoot(candidate);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not create a unique demo remote file name.");
    }

    private void ensureInsideDemoRoot(Path target) {
        if (!target.startsWith(demoRemoteRoot)) {
            throw new SecurityException("Demo remote path is outside the configured root.");
        }
    }

    private String uniqueRemotePath(FTPClient client, String remoteDirectory, String originalName) throws IOException {
        String safeName = originalName.replace("/", "_").replace("\\", "_").trim();
        if (safeName.isBlank() || ".".equals(safeName) || "..".equals(safeName)) {
            safeName = "upload.bin";
        }

        List<String> existing = Arrays.stream(client.listFiles(remoteDirectory))
                .map(FTPFile::getName)
                .toList();
        ensurePositive(client, "Could not inspect remote directory.");

        if (!existing.contains(safeName)) {
            return remotePathPolicy.resolveChild(remoteDirectory, safeName);
        }

        String base = safeName;
        String extension = "";
        int dot = safeName.lastIndexOf('.');
        if (dot > 0) {
            base = safeName.substring(0, dot);
            extension = safeName.substring(dot);
        }
        for (int i = 1; i < 10_000; i++) {
            String candidate = base + " (" + i + ")" + extension;
            if (!existing.contains(candidate)) {
                return remotePathPolicy.resolveChild(remoteDirectory, candidate);
            }
        }
        throw new IOException("Could not create a unique remote file name.");
    }

    private <T> T withClient(FtpOperation<T> operation) throws IOException {
        FTPClient client = ftpProperties.isFtps() ? new FTPSClient() : new FTPClient();
        client.setConnectTimeout(ftpProperties.getConnectTimeoutMillis());
        client.setDataTimeout(Duration.ofMillis(ftpProperties.getDataTimeoutMillis()));

        try {
            client.connect(ftpProperties.getHost(), ftpProperties.getPort());
            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                throw new IOException("FTP server refused the connection.");
            }
            if (!client.login(ftpProperties.getUsername(), ftpProperties.getPassword())) {
                throw new IOException("FTP login failed.");
            }
            if (client instanceof FTPSClient ftpsClient) {
                ftpsClient.execPBSZ(0);
                ftpsClient.execPROT("P");
            }
            client.setFileType(FTPClient.BINARY_FILE_TYPE);
            if (ftpProperties.isPassiveMode()) {
                client.enterLocalPassiveMode();
            }
            return operation.run(client);
        } finally {
            if (client.isConnected()) {
                try {
                    client.logout();
                } finally {
                    client.disconnect();
                }
            }
        }
    }

    private static void ensurePositive(FTPClient client, String message) throws IOException {
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            throw new IOException(message);
        }
    }

    @FunctionalInterface
    private interface FtpOperation<T> {
        T run(FTPClient client) throws IOException;
    }
}
