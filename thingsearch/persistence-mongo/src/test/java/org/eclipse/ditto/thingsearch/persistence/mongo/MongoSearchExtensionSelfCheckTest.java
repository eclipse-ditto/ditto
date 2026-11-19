/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.thingsearch.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests {@link MongoSearchExtensionSelfCheck}.
 */
public final class MongoSearchExtensionSelfCheckTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void verifyPassesOnTheModuleClasspath() {
        assertThatCode(MongoSearchExtensionSelfCheck::verify).doesNotThrowAnyException();
    }

    @Test
    public void verifyFailsNamingTheFatJarWhenItIsMissing() throws IOException {
        try (final URLClassLoader loader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            assertThatThrownBy(() -> MongoSearchExtensionSelfCheck.verify(loader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ditto-mongo-search-extension")
                    .hasMessageContaining("ditto-mongo-persistence-extension")
                    .hasMessageContaining("/opt/ditto/backends/mongodb/")
                    .hasCauseInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    public void verifyFailsOnMixedReleaseMarkers() throws IOException {
        try (final URLClassLoader loader = markerLoader("1.0.0", "2.0.0")) {
            assertThatThrownBy(() -> MongoSearchExtensionSelfCheck.verify(loader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("persistence (test)=1.0.0")
                    .hasMessageContaining("search (test)=2.0.0");
        }
    }

    @Test
    public void verifyPassesOnSameReleaseMarkers() throws IOException {
        try (final URLClassLoader loader = markerLoader("1.0.0", "1.0.0")) {
            assertThatCode(() -> MongoSearchExtensionSelfCheck.verify(loader)).doesNotThrowAnyException();
        }
    }

    @Test
    public void probeNamesMongoClientWrapper() {
        assertThat(MongoSearchExtensionSelfCheck.FAT_JAR_PROBE_CLASS).isEqualTo(MongoClientWrapper.class.getName());
    }

    /**
     * A loader over two marker directories whose parent is the test loader, so the probe class resolves and only the
     * markers decide.
     */
    private URLClassLoader markerLoader(final String persistenceVersion, final String searchVersion)
            throws IOException {
        final URL persistence = markerDir("persistence (test)", persistenceVersion).toURI().toURL();
        final URL search = markerDir("search (test)", searchVersion).toURI().toURL();
        return new URLClassLoader(new URL[]{persistence, search}, getClass().getClassLoader());
    }

    private File markerDir(final String component, final String version) throws IOException {
        final File dir = temporaryFolder.newFolder();
        final File marker = new File(dir, MongoSearchExtensionSelfCheck.MARKER_RESOURCE);
        Files.createDirectories(marker.getParentFile().toPath());
        Files.writeString(marker.toPath(), "component=" + component + "\nversion=" + version + "\n",
                StandardCharsets.ISO_8859_1);
        return dir;
    }

}
