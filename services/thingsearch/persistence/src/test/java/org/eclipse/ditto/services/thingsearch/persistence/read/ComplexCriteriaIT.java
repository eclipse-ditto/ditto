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
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.Thing.NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.thingId;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.junit.Test;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class ComplexCriteriaIT extends
        AbstractVersionedThingSearchPersistenceITBase {

    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final String OTHER_STRING_VALUE = "otherValue";

    private static final String THING_ID_WITH_KEY_1_AND_2 = thingId(NAMESPACE, "with1and2");
    private static final String THING_ID_WITH_KEY_1_ONLY = thingId(NAMESPACE, "with1Only");
    private static final String THING_ID_WITH_KEY_2_ONLY = thingId(NAMESPACE,"with2Only");
    private static final String THING_ID_WITH_OTHER_VALUE_1 = thingId(NAMESPACE,"withOtherValue1");
    private static final String THING_ID_WITH_OTHER_VALUE_2 = thingId(NAMESPACE,"withOtherValue2");
    private static final String THING_ID_WITH_NO_KEY = thingId(NAMESPACE, "withNoKey");

    @Override
    void createTestDataV1() {
        insertThings();
    }

    @Override
    void createTestDataV2() {
        insertThings();
    }

    /** */
    @Test
    public void findAllWithAndCriteria() {
        final Criteria crit = cf.and(Arrays.asList(
                searchForValue(KNOWN_ATTRIBUTE_KEY_1),
                searchForValue(KNOWN_ATTRIBUTE_KEY_2)));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2);
    }

    /** */
    @Test
    public void findAllWithNoredAndCriteria() {
        final Criteria crit = cf.nor(cf.and(
                Arrays.asList(
                        searchForKnownKey(KNOWN_STRING_VALUE),
                        searchForKnownKey(OTHER_STRING_VALUE))));

        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY,
                THING_ID_WITH_OTHER_VALUE_1, THING_ID_WITH_NO_KEY, THING_ID_WITH_KEY_2_ONLY,
                THING_ID_WITH_OTHER_VALUE_2);
    }

    /** */
    @Test
    public void findAllWithOrCriteria() {
        final Criteria crit = cf.or(Arrays.asList(
                searchForValue(KNOWN_ATTRIBUTE_KEY_1),
                searchForValue(KNOWN_ATTRIBUTE_KEY_2)));

        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY, THING_ID_WITH_KEY_2_ONLY);
    }

    /** */
    @Test
    public void findAllWithNoredOrCriteria() {
        final Criteria crit = cf.nor(cf.or(Arrays.asList(
                searchForValue(KNOWN_ATTRIBUTE_KEY_1),
                searchForValue(KNOWN_ATTRIBUTE_KEY_2))));

        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_NO_KEY, THING_ID_WITH_OTHER_VALUE_1, THING_ID_WITH_OTHER_VALUE_2);
    }

    /** */
    @Test
    public void findAllWithNorCriteria() {
        final Criteria crit = cf.nor(Arrays.asList(
                searchForValue(KNOWN_ATTRIBUTE_KEY_1),
                searchForValue(KNOWN_ATTRIBUTE_KEY_2)));

        final Collection<String> result = executeVersionedQuery(crit);

        assertThat(result).containsOnly(THING_ID_WITH_NO_KEY, THING_ID_WITH_OTHER_VALUE_1, THING_ID_WITH_OTHER_VALUE_2);
    }

    /** */
    @Test
    public void findAllWithNoredNorCriteria() {
        final Criteria crit = cf.nor(cf.nor(Arrays.asList(
                searchForValue(KNOWN_ATTRIBUTE_KEY_1),
                searchForValue(KNOWN_ATTRIBUTE_KEY_2))));

        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY, THING_ID_WITH_KEY_2_ONLY);
    }

    private Criteria searchForKnownKey(final String value) {
        return cf.fieldCriteria(
                fef.filterByAttribute(KNOWN_ATTRIBUTE_KEY_1),
                cf.eq(value));
    }

    private Criteria searchForValue(final String key) {
        return cf.fieldCriteria(
                fef.filterByAttribute(key),
                cf.eq(KNOWN_STRING_VALUE));
    }

    private void insertThings() {
        final Attributes attributes1 = Attributes.newBuilder()
                .set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_1), KNOWN_STRING_VALUE)
                .set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_2), KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes2 = Attributes.newBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes3 = Attributes.newBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes4 = Attributes.newBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, OTHER_STRING_VALUE)
                .build();

        final Attributes attributes5 = Attributes.newBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, OTHER_STRING_VALUE)
                .build();

        persistThing(createThing(THING_ID_WITH_KEY_1_AND_2).setAttributes(attributes1));
        persistThing(createThing(THING_ID_WITH_KEY_1_ONLY).setAttributes(attributes2));
        persistThing(createThing(THING_ID_WITH_KEY_2_ONLY).setAttributes(attributes3));
        persistThing(createThing(THING_ID_WITH_OTHER_VALUE_1).setAttributes(attributes4));
        persistThing(createThing(THING_ID_WITH_OTHER_VALUE_2).setAttributes(attributes5));
        persistThing(createThing(THING_ID_WITH_NO_KEY));
    }
}
