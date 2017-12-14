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

import java.util.Collection;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.junit.Test;

/**
 * Tests for search persistence.
 */
public final class SimpleCriteriaIT extends
        AbstractVersionedThingSearchPersistenceITBase {

    private static final String THING_ID_WITH_KNOWN_STR_ATTR_VALUE =
            thingId(NAMESPACE, "withKnownStrAttrValue");
    private static final String THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE =
            thingId(NAMESPACE, "withKnownNumberAttrValue");
    private static final String THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE =
            thingId(NAMESPACE, "withKnownBooleanAttrValue");
    private static final String THING_ID_WITH_OTHER_ATTR_VALUE = thingId(NAMESPACE, "withOtherAttrValue");
    private static final String THING_ID_WITH_OTHER_INT_VALUE = thingId(NAMESPACE, "withOtherIntValue");
    private static final String THING_ID_WITH_OTHER_BOOLEAN_VALUE = thingId(NAMESPACE, "withOtherBooleanValue");
    private static final String THING_ID_WITH_NULL_VALUE = thingId(NAMESPACE, "withNullValue");

    private static final String KNOWN_ATTRIBUTE_KEY = "attributeKey";
    private static final String KNOWN_STRING_VALUE = "value";
    private static final int KNOWN_NUMBER_VALUE = 4711;
    private static final boolean KNOWN_BOOLEAN_VALUE = false;

    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final ThingsFieldExpressionFactory ef = new ThingsFieldExpressionFactoryImpl();

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
    public void findAllByAnyAttribute() {
        final Collection<String> result = executeVersionedQuery(cf.any());
        assertThat(result).contains(THING_ID_WITH_KNOWN_STR_ATTR_VALUE,
                THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE,
                THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE,
                THING_ID_WITH_OTHER_ATTR_VALUE,
                THING_ID_WITH_OTHER_INT_VALUE,
                THING_ID_WITH_OTHER_BOOLEAN_VALUE,
                THING_ID_WITH_NULL_VALUE);
    }

    /** */
    @Test
    public void findAllByStringAttribute() {
        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.eq(KNOWN_STRING_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_STR_ATTR_VALUE);
    }

    /** */
    @Test
    public void findAllByNumberAttribute() {
        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.eq(KNOWN_NUMBER_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE);
    }

    /** */
    @Test
    public void findAllByBooleanAttribute() {
        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.eq(KNOWN_BOOLEAN_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByStringAttribute() {
        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.ne(KNOWN_STRING_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_STR_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByNumberAttribute() {
        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.ne(KNOWN_NUMBER_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByBooleanAttribute() {
        final Collection<String> result = executeVersionedQuery(
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.ne(KNOWN_BOOLEAN_VALUE)));

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE);
    }


    private void insertThings() {
        Stream.of(createThing(THING_ID_WITH_KNOWN_STR_ATTR_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY,
                KNOWN_STRING_VALUE),
                createThing(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY,
                        KNOWN_NUMBER_VALUE),
                createThing(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY,
                        KNOWN_BOOLEAN_VALUE),
                createThing(THING_ID_WITH_OTHER_ATTR_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY, "otherValue"),
                createThing(THING_ID_WITH_OTHER_INT_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY, 4712),
                createThing(THING_ID_WITH_OTHER_BOOLEAN_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY, true),
                createThing(THING_ID_WITH_NULL_VALUE).setAttribute(KNOWN_ATTRIBUTE_KEY, JsonValue.of(null)))
                .forEach(this::persistThing);
    }

}
