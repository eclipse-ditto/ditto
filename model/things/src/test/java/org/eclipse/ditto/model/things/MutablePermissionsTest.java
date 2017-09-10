/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;

import java.util.EnumSet;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MutablePermissions}.
 */
public final class MutablePermissionsTest {

    private Permissions underTest = null;


    @Before
    public void setUp() {
        underTest = MutablePermissions.none();
    }


    @Ignore("EqualsVerifier does not like extending AbstractCollection")
    @Test
    public void testHashCodeAndEquals() {
        final Permissions red = Permissions.newInstance(Permission.READ);
        final Permissions black = Permissions.newInstance(Permission.WRITE, Permission.ADMINISTRATE);

        EqualsVerifier.forClass(MutablePermissions.class) //
                .usingGetClass() //
                .withPrefabValues(Permissions.class, red, black) //
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNull() {
        underTest.add(null);
    }


    @Test
    public void addNonExistingEntry() {
        final Permission read = Permission.READ;

        assertThat(underTest.add(read)).isTrue();
        Assertions.assertThat(underTest).containsOnly(read);
    }


    @Test
    public void addExistingEntryAgain() {
        underTest.add(Permission.READ);
        underTest.add(Permission.WRITE);

        assertThat(underTest.add(Permission.WRITE)).isFalse();
        Assertions.assertThat(underTest).containsOnly(Permission.READ, Permission.WRITE);
    }


    @Test
    public void createNewMutablePermissionsFromGivenSet() {
        final EnumSet<Permission> allMutablePermissions = EnumSet.allOf(Permission.class);
        underTest = new MutablePermissions(allMutablePermissions);

        Assertions.assertThat(underTest).hasSameSizeAs(allMutablePermissions).containsAll(allMutablePermissions);
    }


    @Test
    public void removeExistingEntry() {
        final Permission write = Permission.WRITE;
        underTest.add(write);

        final Permission administrate = Permission.ADMINISTRATE;
        underTest.add(administrate);

        assertThat(underTest.remove(administrate)).isTrue();
        Assertions.assertThat(underTest).containsOnly(write);
    }


    @Test
    public void removeNonExistingEntry() {
        assertThat(underTest.remove(Permission.READ)).isFalse();
        Assertions.assertThat(underTest).isEmpty();
    }


    @Test
    public void containsWorksAsExpected() {
        underTest = Permissions.newInstance(Permission.READ, Permission.WRITE);

        assertThat(underTest.contains(Permission.READ)).isTrue();
        assertThat(underTest.contains(Permission.WRITE)).isTrue();
        assertThat(underTest.contains(Permission.ADMINISTRATE)).isFalse();

        assertThat(underTest.contains(Permission.READ, Permission.WRITE)).isTrue();
        assertThat(underTest.contains(Permission.READ, Permission.ADMINISTRATE)).isFalse();
        assertThat(underTest.contains(Permission.WRITE, Permission.ADMINISTRATE)).isFalse();

        underTest = Permissions.newInstance(Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);

        assertThat(underTest.contains(Permission.READ, Permission.ADMINISTRATE)).isTrue();
    }


    @Test
    public void createJsonRepresentationOfEmptyMutablePermissions() {
        final JsonValue actualJsonValue = underTest.toJson();

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        for (final Permission permission : Permission.values()) {
            jsonObjectBuilder.set(permission.toJsonKey(), false);
        }
        final JsonObject expectedJsonObject = jsonObjectBuilder.build();

        assertThat(actualJsonValue).isEqualTo(expectedJsonObject);
    }


    @Test
    public void createJsonRepresentationOfMutablePermissions() {
        underTest.addAll(EnumSet.allOf(Permission.class));
        final JsonValue actualJsonValue = underTest.toJson();

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        for (final Permission permission : Permission.values()) {
            jsonObjectBuilder.set(permission.toJsonKey(), true);
        }
        final JsonObject expectedJsonObject = jsonObjectBuilder.build();

        assertThat(actualJsonValue).isEqualTo(expectedJsonObject);
    }

}
