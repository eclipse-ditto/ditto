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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.junit.Test;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class AggregationPersistenceCountTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";

    @Override
    void createTestDataV1() {
        // test-data are created in tests
    }

    @Override
    void createTestDataV2() {
        // test-data are created in tests
    }

    /** */
    @Test
    public void countAny() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;
        final String countThingId = ":countThing";

        for (int i = 0; i < expectedCount; i++) {
            insertDocsForCount(countThingId + i, KNOWN_STRING_VALUE,
                    AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS);
        }

        final long actualCount = aggregateCount(AbstractThingSearchPersistenceTestBase.cf.any());

        assertThat(actualCount).isNotNull().isEqualTo(expectedCount);
    }

    /** */
    @Test
    public void countAnyWithSudo() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;
        final String countThingId = ":countThing";

        for (int i = 0; i < expectedCount; i++) {
            insertDocsForCount(countThingId + i, KNOWN_STRING_VALUE, Collections.singletonList("somethingDifferent"));
        }

        final long actualCount = aggregationSudoCount(AbstractThingSearchPersistenceTestBase.cf.any());

        assertThat(actualCount).isNotNull().isEqualTo(expectedCount);
    }

    /** */
    @Test
    public void countEquals() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;
        final String countThingId = ":countThing";
        final String attributeValue = UUID.randomUUID().toString();

        for (int i = 0; i < expectedCount; i++) {
            insertDocsForCount(countThingId + i, attributeValue, AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS);
        }

        final long actualCount =
                aggregateCount(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY_1),
                        AbstractThingSearchPersistenceTestBase.cf.eq(attributeValue)));

        assertThat(actualCount).isNotNull().isEqualTo(expectedCount);
    }

    /** */
    @Test
    public void countWithNoMatchingResults() {
        final String nonExistingThingId = UUID.randomUUID().toString();

        final long actualCount = aggregateCount(
                AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByThingId(),
                        AbstractThingSearchPersistenceTestBase.cf.eq(nonExistingThingId)));

        assertThat(actualCount).isNotNull().isEqualTo(0);
    }

    private void insertDocsForCount(final String thingId, final String attributeValue, final List<String> auth) {
        if (testedApiVersion == JsonSchemaVersion.V_1) {
            insertDocsForCountV1(thingId, attributeValue, auth);
        } else if (testedApiVersion == JsonSchemaVersion.V_2) {
            insertDocsForCountV2(thingId, attributeValue, auth);
        } else {
            throw new IllegalStateException();
        }
    }

    private void insertDocsForCountV2(final String thingId, final String attributeValue, final List<String> auth) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        builder.set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_1), attributeValue);
        builder.set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_2), KNOWN_STRING_VALUE);
        final Attributes attributes1 = ThingsModelFactory.newAttributes(builder.build());

        insertDocs(Collections.singletonList(buildDocV2WithGlobalReads(thingId, auth).attributes(attributes1).build()));

        insertPolicyEntry(thingId + ":attribute/" + KNOWN_ATTRIBUTE_KEY_1, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_1);
        insertPolicyEntry(thingId + ":attribute/" + KNOWN_ATTRIBUTE_KEY_2, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_2);
    }

    private void insertDocsForCountV1(final String thingId, final String attributeValue, final List<String> auth) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        builder.set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_1), attributeValue);
        builder.set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_2), KNOWN_STRING_VALUE);
        final Attributes attributes1 = ThingsModelFactory.newAttributes(builder.build());

        insertDocs(Collections.singletonList(buildDocV1WithAcl(thingId, auth).attributes(attributes1).build()));
    }

}
