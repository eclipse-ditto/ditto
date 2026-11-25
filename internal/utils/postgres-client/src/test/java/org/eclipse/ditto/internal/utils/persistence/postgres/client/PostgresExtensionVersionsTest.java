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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit test for {@link PostgresExtensionVersions} — the layered-extension same-release boot self-check.
 */
public final class PostgresExtensionVersionsTest {

    @Test
    public void matchingVersionsPass() {
        final Map<String, String> markers = new LinkedHashMap<>();
        markers.put("base (ditto-postgres-client-extension)", "3.7.0");
        markers.put("persistence (ditto-postgres-persistence-extension)", "3.7.0");
        assertThatCode(() -> PostgresExtensionVersions.checkConsistency(markers)).doesNotThrowAnyException();
    }

    @Test
    public void singleMarkerPasses() {
        final Map<String, String> markers = Map.of("base (ditto-postgres-client-extension)", "3.7.0");
        assertThatCode(() -> PostgresExtensionVersions.checkConsistency(markers)).doesNotThrowAnyException();
    }

    @Test
    public void emptyMarkersPass() {
        // A dev run from the raw module jars carries no markers — the check must pass quietly.
        assertThatCode(() -> PostgresExtensionVersions.checkConsistency(Map.of())).doesNotThrowAnyException();
    }

    @Test
    public void divergentVersionsFailFastNamingComponentsAndVersions() {
        final Map<String, String> markers = new LinkedHashMap<>();
        markers.put("base (ditto-postgres-client-extension)", "3.7.0");
        markers.put("persistence (ditto-postgres-persistence-extension)", "3.6.9");
        assertThatThrownBy(() -> PostgresExtensionVersions.checkConsistency(markers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIFFERENT Ditto releases")
                .hasMessageContaining("ditto-postgres-client-extension")
                .hasMessageContaining("ditto-postgres-persistence-extension")
                .hasMessageContaining("3.7.0")
                .hasMessageContaining("3.6.9");
    }

    @Test
    public void verifyConsistentAgainstOwnClassLoaderDoesNotThrow() {
        // The postgres-client test classpath carries no extension markers, so the real classpath scan passes.
        assertThatCode(PostgresExtensionVersions::verifyConsistent).doesNotThrowAnyException();
        assertThat(PostgresExtensionVersions.readMarkers(getClass().getClassLoader())).isEmpty();
    }

}
