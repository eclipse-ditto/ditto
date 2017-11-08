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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAclEntry}.
 */
public final class ImmutableAclEntryTest {

    private static final AuthorizationSubject KNOWN_AUTH_SUBJECT = AuthorizationSubject.newInstance("antman");

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAclEntry.class,
                areImmutable(),
                provided(JsonObject.class, JsonFieldDefinition.class, AuthorizationSubject.class).areAlsoImmutable(),
                assumingFields("permissions").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                assumingFields("cachedJsonObject").areModifiedAsPartOfAnUnobservableCachingStrategy());

        final EnumSet<Permission> initialSet = EnumSet.of(Permission.READ, Permission.ADMINISTRATE);
        final AclEntry underTest = ImmutableAclEntry.of(KNOWN_AUTH_SUBJECT, initialSet);
        Set<Permission> actualPermissions = underTest.getPermissions();

        assertThat(actualPermissions).hasSameSizeAs(initialSet).containsAll(initialSet);

        initialSet.remove(Permission.ADMINISTRATE);
        actualPermissions = underTest.getPermissions();

        assertThat(actualPermissions).containsOnly(Permission.READ, Permission.ADMINISTRATE);
    }

    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableAclEntry.class)
                .usingGetClass()
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAclPermission() {
        ImmutableAclEntry.of(KNOWN_AUTH_SUBJECT, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAuthorizationSubject() {
        ImmutableAclEntry.of(null, Permission.READ);
    }

    @Test
    public void entryDoesNotContainNullPermission() {
        final AclEntry underTest = ImmutableAclEntry.of(KNOWN_AUTH_SUBJECT, Permission.READ);

        assertThat(underTest.contains(null)).isFalse();
    }

    @Test
    public void entryDoesNotContainAllPermissionsForNull() {
        final AclEntry underTest =
                ImmutableAclEntry.of(KNOWN_AUTH_SUBJECT, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);

        assertThat(underTest.containsAll(null)).isFalse();
    }

    @Test
    public void entryDoesNotContainAllPermissions() {
        final AclEntry underTest = ImmutableAclEntry.of(KNOWN_AUTH_SUBJECT, Permission.READ, Permission.WRITE);

        assertThat(underTest.containsAll(EnumSet.of(Permission.WRITE, Permission.ADMINISTRATE))).isFalse();
    }

    @Test
    public void entryContainsAllPermissions() {
        final AclEntry underTest = ImmutableAclEntry.of(KNOWN_AUTH_SUBJECT, Permission.READ, Permission.WRITE);

        assertThat(underTest.containsAll(EnumSet.of(Permission.WRITE, Permission.READ))).isTrue();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceFromNullJsonObject() {
        ImmutableAclEntry.fromJson(null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToCreateInstanceFromEmptyJsonObject() {
        ImmutableAclEntry.fromJson(JsonFactory.newObject());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToCreateInstanceFromJsonObjectContainingSchemaVersionOnly() {
        final JsonObject jsonObject = JsonFactory.newObject("{\"__schemaVersion\":1}");
        ImmutableAclEntry.fromJson(jsonObject);
    }

    @Test
    public void createInstanceFromJsonObjectContainingMoreThanOneAclEntry() {
        final String authSubjectId1 = "conquest";
        final String authSubjectId2 = "war";
        final String authSubjectId3 = "famine";
        final String authSubjectId4 = "death";

        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonSchemaVersion.getJsonKey(), 1)
                .set(authSubjectId1, JsonFactory.newObjectBuilder()
                        .set(Permission.READ.toJsonKey(), true)
                        .set(Permission.WRITE.toJsonKey(), false)
                        .set(Permission.ADMINISTRATE.toJsonKey(), false)
                        .build())
                .set(authSubjectId2, JsonFactory.newObjectBuilder()
                        .set(Permission.READ.toJsonKey(), true)
                        .set(Permission.WRITE.toJsonKey(), true)
                        .set(Permission.ADMINISTRATE.toJsonKey(), false)
                        .build())
                .set(authSubjectId3, JsonFactory.newObjectBuilder()
                        .set(Permission.READ.toJsonKey(), false)
                        .set(Permission.WRITE.toJsonKey(), true)
                        .set(Permission.ADMINISTRATE.toJsonKey(), false)
                        .build())
                .set(authSubjectId4, JsonFactory.newObjectBuilder()
                        .set(Permission.READ.toJsonKey(), true)
                        .set(Permission.WRITE.toJsonKey(), true)
                        .set(Permission.ADMINISTRATE.toJsonKey(), true)
                        .build())
                .build();

        final AclEntry actualAclEntry = ImmutableAclEntry.fromJson(jsonObject);
        final AclEntry expectedAclEntry =
                ImmutableAclEntry.of(AuthorizationSubject.newInstance(authSubjectId1), Permission.READ);

        assertThat(actualAclEntry).isEqualTo(expectedAclEntry);
    }

    @Test(expected = AclEntryInvalidException.class)
    public void createInstanceFromJsonObjectContainingOnlyInvalidPermissions() {
        final JsonObject jsonObject = JsonFactory.newObject("{\"authentication_subject\":{\"BUMLUX\":true}}");
        ImmutableAclEntry.fromJson(jsonObject);
    }

    @Test
    public void toJsonWithSchemaVersion1ReturnsJsonObjectWithRegularFieldsOnly() throws JSONException {
        final JsonSchemaVersion schemaVersion = JsonSchemaVersion.V_1;
        final AuthorizationSubject authorizationSubject = KNOWN_AUTH_SUBJECT;
        final AclEntry underTest = ImmutableAclEntry.of(authorizationSubject, Permission.READ, Permission.WRITE);
        final JsonObject actualJsonObject = underTest.toJson(schemaVersion, FieldType.notHidden());

        final JsonObject expectedJsonObject = JsonFactory.newObjectBuilder()
                .set(JsonFactory.newKey(authorizationSubject.getId()), JsonFactory.newObjectBuilder()
                        .set(Permission.READ.toJsonKey(), true)
                        .set(Permission.WRITE.toJsonKey(), true)
                        .set(Permission.ADMINISTRATE.toJsonKey(), false)
                        .build())
                .build();

        JSONAssert.assertEquals(actualJsonObject.toString(), expectedJsonObject.toString(),
                JSONCompareMode.NON_EXTENSIBLE);
        assertThat(actualJsonObject.getValue(JsonSchemaVersion.getJsonKey())).isEmpty();
    }

    @Test
    public void createAuthorizationSubjectContainsSlash() {
        AuthorizationSubject.newInstance("slash/separated");
    }

}
