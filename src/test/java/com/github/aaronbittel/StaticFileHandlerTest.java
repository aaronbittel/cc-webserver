package com.github.aaronbittel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class StaticFileHandlerTest {

    private static final Path BASE_PATH = Path.of("base").toAbsolutePath().normalize();

    @Test
    void reject_path_not_starting_with_slash() {
        assertThatIllegalArgumentException().isThrownBy(() -> StaticFileHandler.resolveRequestPath(BASE_PATH, "some-path"));
    }

    @Test
    void reject_path_traversal() {
        assertThatIllegalArgumentException().isThrownBy(() -> StaticFileHandler.resolveRequestPath(BASE_PATH, "/../somepath"))
                .withMessage("Path traversal detected");
    }

    @Test
    void normalize_dot_segments_in_request_path() throws IOException {
        var expected = BASE_PATH.resolve("baz");
        assertThat(StaticFileHandler.resolveRequestPath(BASE_PATH, "/foo/bar/../../baz")).isEqualTo(expected);
    }

    @Test
    void reject_symlink_pointing_outside_of_root(@TempDir Path tempDir) throws IOException {
        var fileOutsideRoot = tempDir.resolve("file-outside-root");
        Files.writeString(fileOutsideRoot, "Hello, World!");

        var root = tempDir.resolve("root");
        Files.createDirectory(root);

        var symPath = root.resolve("symlink");

        createSymlinkOrAbort(symPath, fileOutsideRoot);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> StaticFileHandler.resolveRequestPath(root, "/" + symPath))
                .withMessage("Path traversal detected");
    }

    @Test
    void resolves_relative_symlink_inside_root(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("www"));
        Path documents = Files.createDirectory(root.resolve("documents"));

        Path targetFile = documents.resolve("report.txt");
        Files.writeString(targetFile, "Hello from the target");

        Path symlink = root.resolve("latest.txt");
        createSymlinkOrAbort(symlink, root.relativize(targetFile));

        Path resolved = StaticFileHandler.resolveRequestPath(root, "/latest.txt");

        assertThat(targetFile.toRealPath()).isEqualTo(resolved.toRealPath());
    }

    private static void createSymlinkOrAbort(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (FileSystemException e) {
            throw new TestAbortedException("Cannot create symbolic link; privileges may be missing", e);
        }
    }

}
