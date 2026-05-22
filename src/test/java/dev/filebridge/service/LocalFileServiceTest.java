package dev.filebridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.filebridge.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listsOnlyInsideConfiguredRoot() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        LocalFileService service = new LocalFileService(properties(tempDir));

        assertThat(service.list("").entries())
                .extracting("name")
                .containsExactly("a.txt");
    }

    @Test
    void rejectsTraversalOutsideRoot() throws Exception {
        LocalFileService service = new LocalFileService(properties(tempDir));

        assertThatThrownBy(() -> service.list("../"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void createsUniqueTargetsWithoutOverwriting() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "original");
        LocalFileService service = new LocalFileService(properties(tempDir));

        assertThat(service.createUniqueFileTarget("", "file.txt").getFileName().toString())
                .isEqualTo("file (1).txt");
    }

    private static AppProperties properties(Path root) {
        AppProperties properties = new AppProperties();
        properties.setLocalRoot(root);
        return properties;
    }
}
