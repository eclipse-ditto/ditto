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
package org.eclipse.ditto.internal.utils.persistence.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests {@link ExtensionVersionMarkers}. Every loader sees only the marker directories it is given (parent = the
 * bootstrap loader), so no marker of a real extension JAR on the test classpath can interfere.
 */
public final class ExtensionVersionMarkersTest {

    private static final String MARKER = "META-INF/test-extension-marker.properties";
    private static final String OTHER_MARKER = "META-INF/other-extension-marker.properties";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void noMarkerIsANoOp() throws IOException {
        final URLClassLoader loader = loaderOf(temporaryFolder.newFolder("empty"));

        assertThatCode(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader)).doesNotThrowAnyException();
    }

    @Test
    public void oneMarkerPasses() throws IOException {
        final URLClassLoader loader = loaderOf(markerDir(MARKER, "persistence (a)", "1.0.0"));

        assertThatCode(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader)).doesNotThrowAnyException();
    }

    @Test
    public void twoMarkersWithTheSameVersionPass() throws IOException {
        final URLClassLoader loader = loaderOf(markerDir(MARKER, "persistence (a)", "1.0.0"),
                markerDir(MARKER, "search (b)", "1.0.0"));

        assertThatCode(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader)).doesNotThrowAnyException();
    }

    @Test
    public void twoMarkersWithDifferentVersionsFailListingBoth() throws IOException {
        final URLClassLoader loader = loaderOf(markerDir(MARKER, "persistence (a)", "1.0.0"),
                markerDir(MARKER, "search (b)", "2.0.0"));

        assertThatThrownBy(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The extension JARs that carry " + MARKER + " come from different Ditto releases: "
                        + "[persistence (a)=1.0.0, search (b)=2.0.0]. All of them must come from the same release.");
    }

    @Test
    public void sameComponentAtTwoVersionsFails() throws IOException {
        final URLClassLoader loader = loaderOf(markerDir(MARKER, "persistence (a)", "1.0.0"),
                markerDir(MARKER, "persistence (a)", "2.0.0"));

        assertThatThrownBy(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persistence (a)=1.0.0")
                .hasMessageContaining("persistence (a)=2.0.0");
    }

    @Test
    public void markerWithoutVersionCountsAsUnknown() throws IOException {
        final URLClassLoader loader = loaderOf(markerDir(MARKER, "persistence (a)", null),
                markerDir(MARKER, "search (b)", "1.0.0"));

        assertThatThrownBy(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persistence (a)=<unknown>")
                .hasMessageContaining("search (b)=1.0.0");
    }

    @Test
    public void onlyTheNamedResourceIsRead() throws IOException {
        final URLClassLoader loader = loaderOf(markerDir(MARKER, "persistence (a)", "1.0.0"),
                markerDir(OTHER_MARKER, "other (c)", "8.8.8"),
                markerDir(OTHER_MARKER, "other (d)", "9.9.9"));

        assertThatCode(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, loader)).doesNotThrowAnyException();
        // the mismatching markers are found when, and only when, their own name is asked for
        assertThatThrownBy(() -> ExtensionVersionMarkers.verifyConsistent(OTHER_MARKER, loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other (c)=8.8.8")
                .hasMessageContaining("other (d)=9.9.9")
                .hasMessageContaining(OTHER_MARKER);
    }

    @Test
    public void nullArgumentsAreRejected() throws IOException {
        final URLClassLoader loader = loaderOf(temporaryFolder.newFolder("empty"));

        assertThatThrownBy(() -> ExtensionVersionMarkers.verifyConsistent(null, loader))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("markerResource");
        assertThatThrownBy(() -> ExtensionVersionMarkers.verifyConsistent(MARKER, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("classLoader");
    }

    private File markerDir(final String resource, final String component, final String version) throws IOException {
        final File dir = temporaryFolder.newFolder();
        final File marker = new File(dir, resource);
        Files.createDirectories(marker.getParentFile().toPath());
        final StringBuilder content = new StringBuilder("component=").append(component).append('\n');
        if (version != null) {
            content.append("version=").append(version).append('\n');
        }
        Files.writeString(marker.toPath(), content, StandardCharsets.ISO_8859_1);
        return dir;
    }

    private static URLClassLoader loaderOf(final File... dirs) throws IOException {
        final URL[] urls = new URL[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            urls[i] = dirs[i].toURI().toURL();
        }
        return new URLClassLoader(urls, null);
    }

}
