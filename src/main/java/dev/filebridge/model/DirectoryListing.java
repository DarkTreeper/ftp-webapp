package dev.filebridge.model;

import java.util.List;

public record DirectoryListing(
        String path,
        String parentPath,
        List<FileEntry> entries
) {
}
