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
package org.eclipse.ditto.services.models.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingCacheEntry}.
 */
public final class ThingCacheEntryTest {

    private static final JsonSchemaVersion KNOWN_SCHEMA_VERSION = JsonSchemaVersion.V_2;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(BaseCacheEntry.JsonFields.JSON_SCHEMA_VERSION, KNOWN_SCHEMA_VERSION.toInt())
            .set(BaseCacheEntry.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID)
            .set(BaseCacheEntry.JsonFields.DELETED, false)
            .set(BaseCacheEntry.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .build();

    private ThingCacheEntry underTest;

    /** */
    @Before
    public void setUp() {
        underTest = ThingCacheEntry.of(KNOWN_SCHEMA_VERSION, TestConstants.Thing.POLICY_ID,
                TestConstants.Thing.REVISION_NUMBER);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingCacheEntry.class,
                areImmutable(),
                provided(CacheEntry.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingCacheEntry.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final JsonValue actualJsonValue = underTest.toJson();

        assertThat(actualJsonValue).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final ThingCacheEntry thingCacheEntryFromJson = ThingCacheEntry.fromJson(KNOWN_JSON);

        assertThat(thingCacheEntryFromJson).isEqualTo(underTest);
    }

    /** */
    @Test
    public void createInstanceFromJsonWithoutPolicyIdWorks() {
        final JsonPointer policyIdPointer = BaseCacheEntry.JsonFields.POLICY_ID.getPointer();
        final JsonObject jsonWithoutPolicyId = KNOWN_JSON.remove(policyIdPointer);
        final ThingCacheEntry expected =
                ThingCacheEntry.of(KNOWN_SCHEMA_VERSION, null, TestConstants.Thing.REVISION_NUMBER);

        underTest = ThingCacheEntry.fromJson(jsonWithoutPolicyId);

        assertThat(underTest).isEqualTo(expected);
    }

    /** */
    @Test
    public void getPolicyIdReturnsExpected() {
        assertThat(underTest.getPolicyId()).contains(TestConstants.Thing.POLICY_ID);
    }

    /** */
    @Test
    public void isDeletedReturnsFalse() {
        assertThat(underTest.isDeleted()).isFalse();
    }

    /** */
    @Test
    public void getJsonSchemaVersionReturnsExpected() {
        assertThat(underTest.getJsonSchemaVersion()).contains(KNOWN_SCHEMA_VERSION);
    }

    /** */
    @Test
    public void getRevisionReturnsExpected() {
        assertThat(underTest.getRevision()).isEqualTo(TestConstants.Thing.REVISION_NUMBER);
    }

    /** */
    @Test
    public void asDeletedReturnsExpected() {
        final CacheEntry deletedCacheEntry = underTest.asDeleted(TestConstants.Thing.REVISION_NUMBER);

        assertThat(deletedCacheEntry.getPolicyId()).isNotPresent();
        assertThat(deletedCacheEntry.getJsonSchemaVersion()).isNotPresent();
        assertThat(deletedCacheEntry.getRevision()).isEqualTo(TestConstants.Thing.REVISION_NUMBER);
        assertThat(deletedCacheEntry.isDeleted()).isTrue();
    }

}
