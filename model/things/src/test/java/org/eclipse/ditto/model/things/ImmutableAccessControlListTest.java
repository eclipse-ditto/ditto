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

import static org.eclipse.ditto.json.JsonFactory.newObject;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.things.ThingsModelFactory.newAclEntry;
import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areEffectivelyImmutable;

import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link AccessControlList}.
 */
public final class ImmutableAccessControlListTest {

    private static final AuthorizationSubject KNOWN_AUTH_SUBJECT_FOO = newAuthSubject("foo");
    private static final AuthorizationSubject KNOWN_AUTH_SUBJECT_BAR = newAuthSubject("bar");
    private static final AuthorizationSubject KNOWN_AUTH_SUBJECT_BAZ = newAuthSubject("baz");

    private AccessControlList underTest = null;

    private static AclEntry toAclEntry(final CharSequence authSubjectId, final Permission permission,
            final Permission... permissions) {
        final AuthorizationSubject authorizationSubject = newAuthSubject(authSubjectId);
        return newAclEntry(authorizationSubject, permission, permissions);
    }


    @Before
    public void setUp() {
        underTest = ImmutableAccessControlList.empty();
    }


    @Ignore("The class is immutable")
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAccessControlList.class,
                areEffectivelyImmutable(),
                assumingFields("entries").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableAccessControlList.class)
                .usingGetClass()
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }


    @Test
    public void emptyAclIsEmpty() {
        assertThat(underTest.isEmpty()).isTrue();
    }


    @Test
    public void emptyAclHasSizeZero() {
        assertThat(underTest.getSize()).isZero();
    }


    @Test
    public void mergeAclEntryWorksAsExpected() {
        final Permission permission = Permission.WRITE;
        final AclEntry entryToAdd = newAclEntry(KNOWN_AUTH_SUBJECT_FOO, permission);
        underTest = underTest.merge(entryToAdd);

        assertThat(underTest).containsExactly(entryToAdd);
    }


    @Test
    public void getAuthorizedSubjectsForWritePermission() {
        final Permission permission = Permission.WRITE;
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_FOO, permission);
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_BAR, permission);
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_BAZ, permission);

        final Set<AuthorizationSubject> authorizationSubjects = underTest.getAuthorizedSubjectsFor(permission);

        assertThat(authorizationSubjects).containsOnly(KNOWN_AUTH_SUBJECT_FOO, KNOWN_AUTH_SUBJECT_BAR,
                KNOWN_AUTH_SUBJECT_BAZ);
    }


    @Test
    public void mergeAuthorizationSubjectWithMultiplePermissions() {
        final AuthorizationSubject authSubject = KNOWN_AUTH_SUBJECT_BAZ;
        underTest = underTest.merge(authSubject, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);

        assertThat(underTest).containsOnly(
                newAclEntry(authSubject, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE));
    }


    @Test
    public void mergeEntryCreatesDisjointAcl() {
        underTest = AccessControlList.newBuilder() //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.WRITE)) //
                .build();
        final AclEntry entryToSet = newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, Permission.ADMINISTRATE);
        final AccessControlList afterSet = underTest.merge(entryToSet);

        assertThat(underTest).hasSize(2).doesNotContain(entryToSet);
        assertThat(afterSet).hasSize(3).isNotSameAs(underTest).contains(entryToSet);
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetNullAclEntry() {
        underTest.setEntry(null);
    }


    @Test
    public void setEntryCreatesDisjointAcl() {
        underTest = AccessControlList.newBuilder() //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.WRITE)) //
                .build();
        final AclEntry entryToSet = newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.ADMINISTRATE);
        final AccessControlList afterSet = underTest.setEntry(entryToSet);

        assertThat(underTest).hasSize(2).doesNotContain(entryToSet);
        assertThat(afterSet).hasSize(2).isNotSameAs(underTest).contains(entryToSet);
    }


    @Test(expected = NullPointerException.class)
    public void tryToGetAclEntryForNullAuthoriztionSubject() {
        underTest.getEntryFor(null);
    }


    @Test
    public void getAclEntryForAuthorizationSubject() {
        final AuthorizationSubject authorizationSubject = KNOWN_AUTH_SUBJECT_FOO;
        underTest = underTest.merge(authorizationSubject, Permission.READ, Permission.WRITE);
        final AclEntry expectedAclEntry = newAclEntry(authorizationSubject, Permission.READ, Permission.WRITE);

        assertThat(underTest.getEntryFor(authorizationSubject)).contains(expectedAclEntry);
    }


    @Test(expected = NullPointerException.class)
    public void tryToGetPermissionsForNullAuthorizationSubject() {
        underTest.getPermissionsOf(null);
    }


    @Test
    public void getPermissionsForBarSubject() {
        final AuthorizationSubject authSubject = KNOWN_AUTH_SUBJECT_BAR;
        underTest = underTest.merge(authSubject, Permission.ADMINISTRATE, Permission.READ);
        final Set<Permission> permissions = underTest.getPermissionsOf(authSubject);

        assertThat(permissions).containsOnly(Permission.ADMINISTRATE, Permission.READ);
    }


    @Test
    public void checkPermissionsForAuthorizationSubject() {
        final AccessControlList underTest = ImmutableAccessControlList.of(TestConstants.Thing.ACL);

        assertThat(underTest.hasPermission(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.WRITE,
                Permission.ADMINISTRATE)).isFalse();
    }


    @Test
    public void mergeAclPermissionWithMultipleAuthorizationSubjects() {
        final Permission permission = Permission.READ;
        underTest = underTest.merge(permission, KNOWN_AUTH_SUBJECT_FOO, KNOWN_AUTH_SUBJECT_BAZ);

        assertThat(underTest).containsOnly(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, permission),
                newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, permission));
    }


    @Test
    public void getAuthorizedSubjectsForAdministratePermission() {
        final Permission permission = Permission.ADMINISTRATE;
        underTest = underTest.merge(permission, KNOWN_AUTH_SUBJECT_FOO, KNOWN_AUTH_SUBJECT_BAR, KNOWN_AUTH_SUBJECT_BAZ);
        final Set<AuthorizationSubject> authorizationSubjects = underTest.getAuthorizedSubjectsFor(permission);

        assertThat(authorizationSubjects).containsOnly(KNOWN_AUTH_SUBJECT_FOO, KNOWN_AUTH_SUBJECT_BAR,
                KNOWN_AUTH_SUBJECT_BAZ);
    }


    @Test
    public void getSizeReturnsExpected() {
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_FOO, Permission.READ);
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_BAR, Permission.READ, Permission.WRITE);
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_BAZ, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);

        final byte expectedSize = 3;

        assertThat(underTest.getSize()).isEqualTo(expectedSize);
    }


    @Test
    public void removeLastPermissionOfAuthorizationSubjectRemovesWholeAuthorizationSubject() {
        final AuthorizationSubject authorizationSubject = KNOWN_AUTH_SUBJECT_BAR;
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_FOO, Permission.READ, Permission.WRITE);
        underTest = underTest.merge(authorizationSubject, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);
        underTest = underTest.removePermission(authorizationSubject, Permission.READ);

        assertThat(underTest).contains(newAclEntry(authorizationSubject, Permission.WRITE, Permission.ADMINISTRATE));

        underTest = underTest.removePermission(authorizationSubject, Permission.WRITE, Permission.ADMINISTRATE);

        assertThat(underTest.contains(authorizationSubject)).isFalse();
        assertThat(underTest.contains(KNOWN_AUTH_SUBJECT_FOO)).isTrue();
    }


    @Test
    public void removeSoleEntryOfAcl() {
        final AclEntry entry = newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ);
        underTest = underTest.merge(entry);
        underTest = underTest.removeEntry(entry);

        assertThat(underTest).isEmpty();
    }


    @Test
    public void removeAllPermissionsOfBazSubject() {
        final AuthorizationSubject authSubject = KNOWN_AUTH_SUBJECT_BAZ;
        underTest = underTest.merge(authSubject, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);
        underTest = underTest.merge(KNOWN_AUTH_SUBJECT_FOO, Permission.WRITE);
        underTest = underTest.removeAllPermissionsOf(authSubject);

        assertThat(underTest).containsOnly(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.WRITE));
    }


    @Test
    public void getEntriesReturnsDisjointSet() {
        final Collection<AclEntry> expectedEntries = new HashSet<>();
        expectedEntries.add(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ, Permission.WRITE));
        expectedEntries.add(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.READ, Permission.WRITE));
        expectedEntries.add(newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, Permission.READ, Permission.ADMINISTRATE));

        for (final AclEntry entry : expectedEntries) {
            underTest = underTest.merge(entry);
        }

        final Set<AclEntry> actualEntries = underTest.getEntriesSet();

        assertThat(actualEntries).isEqualTo(expectedEntries);
        assertThat(underTest).hasSize(expectedEntries.size());

        final AclEntry entryToBeRemoved = newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, Permission.ADMINISTRATE);
        actualEntries.remove(entryToBeRemoved);

        assertThat(actualEntries).doesNotContain(entryToBeRemoved);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateAclFromNullJsonObject() {
        ImmutableAccessControlList.fromJson((JsonObject) null);
    }


    @Test
    public void createAccessControlListFromValidJson() {
      /*
       * Valid JSON string to be parsed:
       *
       *   {
       *      "ed420ff0-b1c96661e4-9070-3863bbc77d82": {
       *         "READ": true,
       *         "WRITE": true,
       *         "ADMINISTRATE": false
       *      },
       *      "edca04a0-b1c96661e4-9070-3863bbc77d82": {
       *         "READ": true,
       *         "WRITE": false,
       *         "ADMINISTRATE": false
       *      }
       *   }
       */
        final JsonObject jsonObject = JsonFactory.newObjectBuilder() //
                .set("ed420ff0-b1c96661e4-9070-3863bbc77d82",
                        JsonFactory.newObjectBuilder() //
                                .set(Permission.READ.toJsonKey(), true) //
                                .set(Permission.WRITE.toJsonKey(), true) //
                                .set(Permission.ADMINISTRATE.toJsonKey(), false) //
                                .build()) //
                .set("edca04a0-b1c96661e4-9070-3863bbc77d82",
                        JsonFactory.newObjectBuilder() //
                                .set(Permission.READ.toJsonKey(), true) //
                                .set(Permission.WRITE.toJsonKey(), false) //
                                .set(Permission.ADMINISTRATE.toJsonKey(), false) //
                                .build()) //
                .build();

        final AccessControlList underTest = ImmutableAccessControlList.fromJson(jsonObject);

        assertThat(underTest).containsOnly(
                toAclEntry("ed420ff0-b1c96661e4-9070-3863bbc77d82", Permission.READ, Permission.WRITE),
                toAclEntry("edca04a0-b1c96661e4-9070-3863bbc77d82", Permission.READ));
    }


    @Test
    public void toJsonReturnsExpectedJsonObject() throws JSONException {
        underTest = AccessControlList.newBuilder() //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.READ)) //
                .build();

        final JsonSchemaVersion schemaVersion = JsonSchemaVersion.V_1;
        final JsonObject actualJsonObject = underTest.toJson(schemaVersion, FieldType.notHidden());

        final JsonObject fooPermissions = JsonFactory.newObjectBuilder() //
                .set(Permission.READ.toJsonKey(), true) //
                .set(Permission.WRITE.toJsonKey(), true) //
                .set(Permission.ADMINISTRATE.toJsonKey(), true) //
                .build();

        final JsonObject barPermissions = JsonFactory.newObjectBuilder() //
                .set(Permission.READ.toJsonKey(), true) //
                .set(Permission.WRITE.toJsonKey(), false)//
                .set(Permission.ADMINISTRATE.toJsonKey(), false) //
                .build();

        final JsonObject expectedJsonObject = JsonFactory.newObjectBuilder() //
                .set(JsonFactory.newKey(KNOWN_AUTH_SUBJECT_FOO.getId()), fooPermissions) //
                .set(JsonFactory.newKey(KNOWN_AUTH_SUBJECT_BAR.getId()), barPermissions) //
                .build();

        JSONAssert.assertEquals(expectedJsonObject.toString(), actualJsonObject.toString(),
                JSONCompareMode.NON_EXTENSIBLE);
    }


    @Test(expected = NullPointerException.class)
    public void tryToSetNullPermissionsForAllAuthorizationSubjects() {
        underTest.setForAllAuthorizationSubjects(null);
    }


    @Test
    public void setEmptyPermissionsForAllAuthorizationSubjectsCreatesEmptyAcl() {
        underTest = AccessControlList.newBuilder() //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.READ)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, Permission.WRITE)) //
                .build();

        assertThat(underTest).hasSize(3);

        underTest = underTest.setForAllAuthorizationSubjects(ThingsModelFactory.noPermissions());

        assertThat(underTest).isEmpty();
    }


    @Test
    public void setPermissionsForAllAuthorizationSubjects() {
        underTest = AccessControlList.newBuilder() //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.READ)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, Permission.WRITE)) //
                .build();

        assertThat(underTest).hasSize(3);

        final Permissions newPermissions = ThingsModelFactory.newPermissions(Permission.READ);

        underTest = underTest.setForAllAuthorizationSubjects(newPermissions);

        assertThat(underTest) //
                .hasSize(3) //
                .containsOnly(toAclEntry(KNOWN_AUTH_SUBJECT_FOO.getId(), Permission.READ), //
                        toAclEntry(KNOWN_AUTH_SUBJECT_BAR.getId(), Permission.READ), //
                        toAclEntry(KNOWN_AUTH_SUBJECT_BAZ.getId(), Permission.READ));
    }


    @Test
    public void removeAclEntryFromAclWithoutThisEntry() {
        final AccessControlList acl = ImmutableAccessControlList.of(TestConstants.Authorization.ACL_ENTRY_OLDMAN);

        assertThat(acl.removeEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES)).isSameAs(acl);
    }


    @Test
    public void setPermissionsForAllAuthorizationSubjectsOnEmptyAcl() {
        final AccessControlList acl = ImmutableAccessControlList.empty();

        assertThat(acl.setForAllAuthorizationSubjects(ThingsModelFactory.allPermissions())).isSameAs(acl);
    }


    @Test
    public void ensureAclToBuilderWorks() {
        underTest = AccessControlList.newBuilder() //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_FOO, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAR, Permission.READ)) //
                .set(newAclEntry(KNOWN_AUTH_SUBJECT_BAZ, Permission.WRITE)) //
                .build();

        assertThat(underTest).isEqualTo(underTest.toBuilder().build());
    }

}
