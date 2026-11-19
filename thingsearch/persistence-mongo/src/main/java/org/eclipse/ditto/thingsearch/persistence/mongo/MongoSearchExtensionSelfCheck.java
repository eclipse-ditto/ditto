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

import org.eclipse.ditto.internal.utils.persistence.api.ExtensionVersionMarkers;

/**
 * Start-up check of the MongoDB search extension JAR ({@code ditto-mongo-search-extension}).
 * <p>
 * The search JAR carries only the MongoDB search classes; the MongoDB driver and Ditto's MongoDB persistence classes
 * come from the persistence extension JAR ({@code ditto-mongo-persistence-extension}), which must sit next to it.
 * {@link #verify()} first checks that the persistence JAR is there, by looking up one of its classes by name (a class
 * literal would fail with a bare {@code NoClassDefFoundError} before the check could run), and then that all MongoDB
 * extension JARs come from the same Ditto release, using their version markers {@value #MARKER_RESOURCE}.
 */
public final class MongoSearchExtensionSelfCheck {

    /**
     * The version marker each MongoDB extension JAR carries.
     */
    public static final String MARKER_RESOURCE = "META-INF/ditto-mongo-extension.properties";

    /**
     * A class of the MongoDB persistence extension JAR, named as a string so that this class loads without it.
     */
    static final String FAT_JAR_PROBE_CLASS = "org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper";

    private MongoSearchExtensionSelfCheck() {
        throw new AssertionError();
    }

    /**
     * Verifies that the MongoDB persistence extension JAR is on the classpath of this class and that every MongoDB
     * extension JAR comes from the same Ditto release.
     *
     * @throws IllegalStateException if the persistence extension JAR is missing or the JARs come from different
     * releases.
     */
    public static void verify() {
        verify(MongoSearchExtensionSelfCheck.class.getClassLoader());
    }

    static void verify(final ClassLoader classLoader) {
        try {
            Class.forName(FAT_JAR_PROBE_CLASS, false, classLoader);
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException("The MongoDB search extension JAR (ditto-mongo-search-extension) is on "
                    + "the classpath, but the MongoDB persistence extension JAR it needs "
                    + "(ditto-mongo-persistence-extension) is missing. Put both JARs from the same Ditto release into "
                    + "/opt/ditto/backends/mongodb/.", e);
        }
        ExtensionVersionMarkers.verifyConsistent(MARKER_RESOURCE, classLoader);
    }

}
