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
package org.eclipse.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import org.bson.Document;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Test;

import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class MongoSearchPersistenceCountTest extends AbstractReadPersistenceTestBase {

    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";

    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final ThingsFieldExpressionFactory ef = new ThingsFieldExpressionFactoryImpl();

    /** */
    @Test
    public void countAny() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;
        final String countThingId = ":countThing";

        for (int i = 0; i < expectedCount; i++) {
            insertDocForCount(countThingId + i, KNOWN_STRING_VALUE);
        }

        final Long actualCount = count(cf.any());

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
            insertDocForCount(countThingId + i, attributeValue);
        }

        final Long actualCount =
                count(cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY_1), cf.eq(attributeValue)));

        assertThat(actualCount).isNotNull().isEqualTo(expectedCount);
    }

    private void insertDocForCount(final String thingId, final String attributeValue) {
        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, attributeValue)
                .set(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE)
                .build();

        final Document thingDocument = ThingDocumentBuilder.create(thingId)
                .attributes(attributes)
                .build();

        insertDocs(Collections.singletonList(thingDocument));
    }

}
