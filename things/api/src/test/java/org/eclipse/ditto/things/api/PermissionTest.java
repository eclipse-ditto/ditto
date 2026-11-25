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
package org.eclipse.ditto.things.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Permissions;
import org.junit.Test;

/**
 * Unit tests for {@link Permission} — focusing on the {@code READ_TS} permission introduced for the
 * Timeseries API (Ditto issue #2291).
 */
public final class PermissionTest {

    @Test
    public void readTsConstantHasExpectedStringValue() {
        assertThat(Permission.READ_TS).isEqualTo("READ_TS");
    }

    @Test
    public void readTsIsNotIncludedInDefaultThingPermissions() {
        // Least-privilege invariant: a Thing without an explicit policy must NOT auto-grant timeseries
        // read access. Operators must opt in via policy.
        assertThat(Permission.DEFAULT_THING_PERMISSIONS).doesNotContain(Permission.READ_TS);
    }

    @Test
    public void readTsCanBeUsedToConstructPermissions() {
        final Permissions permissions = Permissions.newInstance(Permission.READ_TS);

        assertThat(permissions.contains(Permission.READ_TS)).isTrue();
        assertThat(permissions).hasSize(1);
    }

    @Test
    public void readTsCoexistsWithReadAndWriteInAPermissionsSet() {
        final Permissions permissions =
                Permissions.newInstance(Permission.READ, Permission.WRITE, Permission.READ_TS);

        assertThat(permissions.contains(Permission.READ, Permission.WRITE, Permission.READ_TS)).isTrue();
        assertThat(permissions).hasSize(3);
    }

    @Test
    public void readTsSurvivesJsonSerialization() {
        final Permissions permissions = Permissions.newInstance(Permission.READ_TS);

        final JsonArray json = permissions.toJson();

        assertThat(json.contains(JsonValue.of("READ_TS"))).isTrue();
        assertThat(json).hasSize(1);
    }

    @Test
    public void permissionsWithReadTsAndReadJsonContainsBoth() {
        final Permissions permissions = Permissions.newInstance(Permission.READ, Permission.READ_TS);

        final JsonArray json = permissions.toJson();

        assertThat(json.contains(JsonValue.of("READ"))).isTrue();
        assertThat(json.contains(JsonValue.of("READ_TS"))).isTrue();
        assertThat(json).hasSize(2);
    }

    @Test
    public void readTsRoundTripsThroughJson() {
        final Permissions original = Permissions.newInstance(Permission.READ, Permission.READ_TS);

        final JsonArray json = original.toJson();
        final Permissions reconstructed = jsonArrayToPermissions(json);

        assertThat(reconstructed).isEqualTo(original);
        assertThat(reconstructed.contains(Permission.READ_TS)).isTrue();
        assertThat(reconstructed.contains(Permission.READ)).isTrue();
    }

    private static Permissions jsonArrayToPermissions(final JsonArray jsonArray) {
        final String[] strings = jsonArray.stream()
                .map(value -> value.asString())
                .toArray(String[]::new);
        if (strings.length == 0) {
            return Permissions.none();
        }
        final String first = strings[0];
        final String[] rest = new String[strings.length - 1];
        System.arraycopy(strings, 1, rest, 0, rest.length);
        return Permissions.newInstance(first, rest);
    }
}
