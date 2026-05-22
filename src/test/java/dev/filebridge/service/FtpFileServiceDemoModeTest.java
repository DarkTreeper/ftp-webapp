package dev.filebridge.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.filebridge.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FtpFileServiceDemoModeTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadsListsAndStreamsFilesInDemoMode() throws Exception {
        Path remoteRoot = Files.createDirectory(tempDir.resolve("demo-remote"));
        AppProperties properties = new AppProperties();
        properties.setDemoMode(true);
        properties.setDemoRemoteRoot(remoteRoot);
        properties.setRemoteRoot("/");

        FtpFileService service = new FtpFileService(properties);
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "Hallo FTP".getBytes()
        );

        service.upload(upload, "/");

        assertThat(service.list("/").entries())
                .extracting("name")
                .containsExactly("hello.txt");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.streamDownload("/hello.txt", outputStream);

        assertThat(outputStream.toString()).isEqualTo("Hallo FTP");
    }
}
