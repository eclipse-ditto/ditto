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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Same-release check for the drop-in extension JARs of a persistence backend.
 * <p>
 * Each extension JAR of a backend carries a marker resource (a properties file with a {@code component} label and the
 * {@code version} of the Ditto release it was built from). {@link #verifyConsistent(String, ClassLoader)} reads every
 * copy of the named marker that the given class loader sees and fails when they declare more than one version. Two
 * copies of the same JAR from different releases fail as well: every marker counts, not every component. No marker
 * or a single marker passes; a development run from the plain module JARs carries none.
 */
public final class ExtensionVersionMarkers {

    private static final String COMPONENT_KEY = "component";
    private static final String VERSION_KEY = "version";
    private static final String UNKNOWN_VERSION = "<unknown>";

    private ExtensionVersionMarkers() {
        throw new AssertionError();
    }

    /**
     * Verifies that every marker {@code markerResource} visible to {@code classLoader} declares the same version.
     *
     * @param markerResource the classpath resource name of the backend's marker.
     * @param classLoader the class loader that sees the extension JARs.
     * @throws NullPointerException if an argument is {@code null}.
     * @throws IllegalStateException if the markers declare different versions, or a marker cannot be read.
     */
    public static void verifyConsistent(final String markerResource, final ClassLoader classLoader) {
        Objects.requireNonNull(markerResource, "markerResource");
        Objects.requireNonNull(classLoader, "classLoader");
        final List<String> markers = new ArrayList<>();
        final Set<String> distinctVersions = new LinkedHashSet<>();
        try {
            final Enumeration<URL> urls = classLoader.getResources(markerResource);
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                final Properties properties = new Properties();
                try (final InputStream in = url.openStream()) {
                    properties.load(in);
                }
                final String component = properties.getProperty(COMPONENT_KEY, url.toString());
                final String version = properties.getProperty(VERSION_KEY, UNKNOWN_VERSION);
                markers.add(component + "=" + version);
                distinctVersions.add(version);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(
                    "Could not read the extension version markers (" + markerResource + ").", e);
        }
        if (distinctVersions.size() > 1) {
            throw new IllegalStateException("The extension JARs that carry " + markerResource
                    + " come from different Ditto releases: " + markers
                    + ". All of them must come from the same release.");
        }
    }

}
