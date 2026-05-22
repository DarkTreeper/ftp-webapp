package dev.filebridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RemotePathPolicyTest {

    @Test
    void normalizesPathsInsideConfiguredRoot() {
        RemotePathPolicy policy = new RemotePathPolicy("/share");

        assertThat(policy.normalize("/share/a/../b")).isEqualTo("/share/b");
        assertThat(policy.resolveChild("/share/b", "file.txt")).isEqualTo("/share/b/file.txt");
        assertThat(policy.parent("/share/b/file.txt")).isEqualTo("/share/b");
    }

    @Test
    void rejectsPathsOutsideConfiguredRoot() {
        RemotePathPolicy policy = new RemotePathPolicy("/share");

        assertThatThrownBy(() -> policy.normalize("/share/../../etc"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsUnsafeChildNames() {
        RemotePathPolicy policy = new RemotePathPolicy("/");

        assertThatThrownBy(() -> policy.resolveChild("/", "../secret"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> policy.resolveChild("/", "a/b"))
                .isInstanceOf(SecurityException.class);
    }
}
