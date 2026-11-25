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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Boot-time same-release self-check for the layered Postgres extension jars.
 * <p>
 * The Postgres backend ships as THREE layered drop-in jars that MUST come from the same Ditto release: the shared base
 * {@code ditto-postgres-client-extension} plus the thin {@code ditto-postgres-persistence-extension} and/or
 * {@code ditto-postgres-search-extension}. This class lives in the base module (so BOTH the persistence and the search
 * provider can call it once the base is present) and detects the classic operator mistake of staging the extensions
 * directory with jars from different releases (e.g. upgrading only the thin persistence jar and forgetting the base).
 * <p>
 * Each extension jar carries a marker resource {@value #MARKER_RESOURCE} declaring its {@code component} and
 * {@code version} (the release it was built from). {@link #verifyConsistent()} reads every such marker visible to this
 * class's class loader and fails fast with an actionable error naming each component and version if they diverge. When
 * fewer than two markers are visible (e.g. a dev run from the raw module jars, which carry no marker) the check passes
 * quietly — the version-skew concern only exists for the shaded drop-in deployment.
 *
 * @since 4.0.0
 */
public final class PostgresExtensionVersions {

    /**
     * The classpath resource each layered Postgres extension jar carries with its {@code component}/{@code version}.
     */
    public static final String MARKER_RESOURCE = "META-INF/ditto-postgres-extension.properties";

    static final String COMPONENT_KEY = "component";
    static final String VERSION_KEY = "version";

    private PostgresExtensionVersions() {
        throw new AssertionError("nope");
    }

    /**
     * Verifies that every Postgres extension version marker visible to this class's class loader declares the same
     * release version. Called from the Postgres persistence/search providers at construction (after the base presence is
     * assured). See the class javadoc for the full contract.
     *
     * @throws IllegalStateException if two mounted extension jars declare different versions, or a marker cannot be read.
     */
    public static void verifyConsistent() {
        verifyConsistent(PostgresExtensionVersions.class.getClassLoader());
    }

    static void verifyConsistent(final ClassLoader classLoader) {
        checkConsistency(readMarkers(classLoader));
    }

    /**
     * Reads all {@value #MARKER_RESOURCE} markers visible to the given class loader.
     *
     * @param classLoader the class loader whose visible markers are read.
     * @return an ordered map component-label &rarr; version for every marker found (possibly empty).
     */
    static Map<String, String> readMarkers(final ClassLoader classLoader) {
        final Map<String, String> componentToVersion = new LinkedHashMap<>();
        try {
            final Enumeration<URL> urls = classLoader.getResources(MARKER_RESOURCE);
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                final Properties properties = new Properties();
                try (final InputStream in = url.openStream()) {
                    properties.load(in);
                }
                final String component = properties.getProperty(COMPONENT_KEY, url.toString());
                final String version = properties.getProperty(VERSION_KEY, "<unknown>");
                componentToVersion.put(component, version);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(
                    "Could not read the Postgres extension version markers (" + MARKER_RESOURCE + ").", e);
        }
        return componentToVersion;
    }

    /**
     * Fails if the given component&rarr;version map contains more than one distinct version.
     *
     * @param componentToVersion the component-label &rarr; version map (from {@link #readMarkers}).
     * @throws IllegalStateException naming each component and version if the versions diverge.
     */
    static void checkConsistency(final Map<String, String> componentToVersion) {
        final Set<String> distinctVersions = new LinkedHashSet<>(componentToVersion.values());
        if (distinctVersions.size() > 1) {
            throw new IllegalStateException(
                    "The mounted Postgres extension jars are from DIFFERENT Ditto releases: " + componentToVersion
                            + ". All layered Postgres extension jars (the base ditto-postgres-client-extension plus the "
                            + "thin ditto-postgres-persistence-extension / ditto-postgres-search-extension) MUST come "
                            + "from the SAME Ditto release. Re-stage the /opt/ditto/extensions/ directory so every "
                            + "Postgres extension jar is at the same version " + distinctVersions + ".");
        }
    }

}
