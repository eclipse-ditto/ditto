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
package org.eclipse.ditto.services.utils.distributedcache.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link BaseCacheEntry}.
 */
public final class BaseCacheEntryTest {

    private static final String POLICY_ID = "CareyMahoni";
    private static final long REVISION = 23;
    private static final boolean DELETED = false;
    private static final JsonSchemaVersion JSON_SCHEMA_VERSION = JsonSchemaVersion.V_2;

    private static JsonObject fullJsonRepresentation;

    private BaseCacheEntry underTest;

    @BeforeClass
    public static void createJsonRepresentation() {
        fullJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(BaseCacheEntry.JsonFields.POLICY_ID, POLICY_ID)
                .set(BaseCacheEntry.JsonFields.REVISION, REVISION)
                .set(BaseCacheEntry.JsonFields.DELETED, DELETED)
                .set(BaseCacheEntry.JsonFields.JSON_SCHEMA_VERSION, JSON_SCHEMA_VERSION.toInt())
                .build();
    }

    @Before
    public void setUp() {
        underTest = BaseCacheEntry.newInstance(POLICY_ID, REVISION, DELETED, JSON_SCHEMA_VERSION);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(BaseCacheEntry.class,
                areImmutable(),
                provided(JsonSchemaVersion.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(BaseCacheEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonWithAllPropertiesReturnsExpected() {
        final JsonObject actualJsonObject = underTest.toJson();

        assertThat(actualJsonObject).isEqualTo(fullJsonRepresentation);
    }

    @Test
    public void toJsonWithoutPolicyIdReturnsExpected() {
        final JsonObject expectedJsonObject =
                fullJsonRepresentation.remove(BaseCacheEntry.JsonFields.POLICY_ID.getPointer());

        final BaseCacheEntry underTest = BaseCacheEntry.newInstance(null, REVISION, DELETED, JSON_SCHEMA_VERSION);
        final JsonObject actualJsonObject = underTest.toJson();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void toJsonWithoutJsonSchemaVersionReturnsExpected() {
        final JsonObject expectedJsonObject =
                fullJsonRepresentation.remove(BaseCacheEntry.JsonFields.JSON_SCHEMA_VERSION.getPointer());

        final BaseCacheEntry underTest = BaseCacheEntry.newInstance(POLICY_ID, REVISION, DELETED, null);
        final JsonObject actualJsonObject = underTest.toJson();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void getPolicyIdReturnsExpected() {
        final Optional<String> policyIdOptional = underTest.getPolicyId();

        assertThat(policyIdOptional).contains(POLICY_ID);
    }

    @Test
    public void getRevisionReturnsExpected() {
        final long revision = underTest.getRevision();

        assertThat(revision).isEqualTo(REVISION);
    }

    @Test
    public void isDeletedReturnsExpected() {
        final boolean deleted = underTest.isDeleted();

        assertThat(deleted).isEqualTo(DELETED);
    }

    @Test
    public void getJsonSchemaVersionReturnsExpected() {
        final Optional<JsonSchemaVersion> jsonSchemaVersionOptional = underTest.getJsonSchemaVersion();

        assertThat(jsonSchemaVersionOptional).contains(JSON_SCHEMA_VERSION);
    }

    @Test
    public void getInstanceFromFullJsonReturnsExpected() {
        final BaseCacheEntry expected = underTest;

        underTest = BaseCacheEntry.fromJson(fullJsonRepresentation);

        assertThat(underTest).isEqualTo(expected);
    }

    @Test
    public void getInstanceFromJsonWithoutPolicyIdReturnsExpected() {
        final BaseCacheEntry expected = BaseCacheEntry.newInstance(null, REVISION, DELETED, JSON_SCHEMA_VERSION);
        final JsonObject jsonWithoutPolicyId =
                fullJsonRepresentation.remove(BaseCacheEntry.JsonFields.POLICY_ID.getPointer());

        underTest = BaseCacheEntry.fromJson(jsonWithoutPolicyId);

        assertThat(underTest).isEqualTo(expected);
    }

    @Test
    public void getInstanceFromJsonWithoutJsonSchemaVersionReturnsExpected() {
        final BaseCacheEntry expected = BaseCacheEntry.newInstance(POLICY_ID, REVISION, DELETED, null);
        final JsonObject jsonWithoutJsonSchemaVersion =
                fullJsonRepresentation.remove(BaseCacheEntry.JsonFields.JSON_SCHEMA_VERSION.getPointer());

        underTest = BaseCacheEntry.fromJson(jsonWithoutJsonSchemaVersion);

        assertThat(underTest).isEqualTo(expected);
    }

    @Test
    public void tryToGetInstanceFromJsonWithoutRevision() {
        final JsonPointer revisionPointer = BaseCacheEntry.JsonFields.REVISION.getPointer();
        final JsonObject jsonWithoutRevision = fullJsonRepresentation.remove(revisionPointer);

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> BaseCacheEntry.fromJson(jsonWithoutRevision))
                .withMessage("JSON did not include required <%s> field!", revisionPointer)
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceFromJsonWithoutDeleted() {
        final JsonPointer deletedPointer = BaseCacheEntry.JsonFields.DELETED.getPointer();
        final JsonObject jsonWithoutDeleted = fullJsonRepresentation.remove(deletedPointer);

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> BaseCacheEntry.fromJson(jsonWithoutDeleted))
                .withMessage("JSON did not include required <%s> field!", deletedPointer)
                .withNoCause();
    }

    @Test
    public void asDeletedReturnsExpected() {
        final long newRevision = REVISION + 1;
        final CacheEntry deletedCacheEntry = underTest.asDeleted(newRevision);

        assertThat(deletedCacheEntry.getPolicyId()).isEmpty();
        assertThat(deletedCacheEntry.getJsonSchemaVersion()).isEmpty();
        assertThat(deletedCacheEntry.getRevision()).isEqualTo(newRevision);
        assertThat(deletedCacheEntry.isDeleted()).isTrue();
    }

}
