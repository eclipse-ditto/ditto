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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceITBase;
import org.eclipse.ditto.services.thingsearch.persistence.util.TestStringGenerator;
import org.junit.Test;

/**
 * Test for the index limitation applied by the search updater persistence .
 */
public final class MongoThingsSearchUpdaterPersistenceIndexLimitationIT
        extends AbstractThingSearchPersistenceITBase {

    private static final String MUCH_TOO_LONG_STRING = TestStringGenerator.createString(10_000);
    private static final JsonValue MUCH_TOO_LONG_JSON_VALUE = JsonValue.of(MUCH_TOO_LONG_STRING);
    private static final String VERY_SHORTENED_STRING = MUCH_TOO_LONG_STRING.substring(0, 5);

    @Test
    public void insertOrUpdateIsLimited() {
        // GIVEN
        final String thingId = ":thingToSave";
        final String attributeKey = "test";
        final JsonPointer pointer = JsonPointer.of(attributeKey);
        final Thing toSave = Thing.newBuilder().setId(thingId).setAttribute(pointer, MUCH_TOO_LONG_JSON_VALUE).build();
        final long revision = 13L;
        final long policyRevision = 78L;

        // WHEN / THEN
        assertThat(runBlockingWithReturn(writePersistence.insertOrUpdate(toSave, revision, policyRevision))).isTrue();

        // thing should not be found by the full attribute value
        final ResultList<String> foundWithFullValue =
                findAllWithAttributeHavingPrefix(attributeKey, MUCH_TOO_LONG_STRING);
        assertThat(foundWithFullValue).isEmpty();

        // thing should be found by shortened attribute value
        final ResultList<String> foundWithShortenedValue =
                findAllWithAttributeHavingPrefix(attributeKey, VERY_SHORTENED_STRING);
        assertThat(foundWithShortenedValue).containsOnly(thingId);
    }

    private ResultList<String> findAllWithAttributeHavingPrefix(final String attributeKey, final String prefix) {
        return findAll(qbf.newBuilder(
                cf.fieldCriteria(fef.filterByAttribute(attributeKey), cf.like(prefix + "*"))
        ).build());
    }

}