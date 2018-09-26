/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.Thing.NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.thingId;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.model.things.Attributes;
import org.junit.Test;

/**
 * Tests for search persistence.
 */
public final class FilterCriteriaIT extends
        AbstractVersionedThingSearchPersistenceITBase {

    private static final String KNOWN_NUMBER_ATTR = "numberAttr";
    private static final String KNOWN_STRING_ATTR = "stringAttr";
    private static final String KNOWN_BOOL_ATTR = "boolAttr";
    private static final String KNOWN_REGEX_ATTR = "stringRegex";

    private static final String THING1_ID = thingId(NAMESPACE, "thing1");
    private static final String THING1_KNOWN_STR_ATTR_VALUE = "a";
    private static final int THING1_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING1_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING2_ID = thingId(NAMESPACE, "thing2");
    private static final String THING2_KNOWN_STR_ATTR_VALUE = "b";
    private static final int THING2_KNOWN_NUM_ATTR_VALUE = 2;
    private static final boolean THING2_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING3_ID = thingId(NAMESPACE, "thing3");
    private static final String THING3_KNOWN_STR_ATTR_VALUE = "c";
    private static final double THING3_KNOWN_NUM_ATTR_VALUE = 3.1;
    private static final boolean THING3_KNOWN_BOOL_ATTR_VALUE = false;
    private static final String THING4_ID = thingId(NAMESPACE, "thing4");
    private static final String THING4_KNOWN_STR_ATTR_VALUE = "d";
    private static final int THING4_KNOWN_NUM_ATTR_VALUE = 4;
    private static final boolean THING4_KNOWN_BOOL_ATTR_VALUE = false;
    private static final String THING1_KNOWN_STR_REGEX_VALUE_TEST = "Das*"; // starts with
    private static final String THING2_KNOWN_STR_REGEX_VALUE_TEST = "*zweite*"; // contains
    private static final String THING3_KNOWN_STR_REGEX_VALUE_TEST = "Teststr?ng numm?r dr?i"; // wildcard
    private static final String THING4_KNOWN_STR_REGEX_VALUE_TEST = "*Teststring"; // ends with

    private static final String THING1_KNOWN_STR_REGEX_VALUE = "Das ist ein X belibiger String"; // starts with
    private static final String THING2_KNOWN_STR_REGEX_VALUE = "Der zweite Das String"; // contains
    private static final String THING3_KNOWN_STR_REGEX_VALUE = "Teststring nummer drei"; // wildcard
    private static final String THING4_KNOWN_STR_REGEX_VALUE = "Der vierte und letzte Teststring"; // ends with

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
    public void findAllByEqString() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void findAllByEqNumber() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.eq(THING2_KNOWN_NUM_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void findAllByEqBoolean() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq(THING3_KNOWN_BOOL_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByNeString() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.ne(THING1_KNOWN_STR_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING2_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByNeNumber() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.ne(THING2_KNOWN_NUM_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByNeBoolean() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.ne(THING3_KNOWN_BOOL_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void neIsEqualToNotEq() {
        final Criteria critNe =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.ne(THING1_KNOWN_STR_ATTR_VALUE));
        final Collection<String> resultNe = executeVersionedQuery(critNe);

        final Criteria critNotEq = cf.nor(
                Collections.singletonList(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE))));
        final Collection<String> resultNotEq = executeVersionedQuery(critNotEq);

        assertThat(resultNe).isNotEmpty();
        assertThat(resultNe).isEqualTo(resultNotEq);
    }

    /** */
    @Test
    public void findAllByGtString() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.gt(THING2_KNOWN_STR_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByGtNumber() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.gt(THING3_KNOWN_NUM_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING4_ID);
    }

    /** */
    @Test
    public void findAllByGtBoolean() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.gt(THING3_KNOWN_BOOL_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void findAllByGeString() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.ge(THING2_KNOWN_STR_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING2_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByGeNumber() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.ge(THING3_KNOWN_NUM_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByGeBoolean() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.ge(THING3_KNOWN_BOOL_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByLtString() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.lt(THING2_KNOWN_STR_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void findAllByLtNumber() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.lt(THING3_KNOWN_NUM_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void findAllByLtBoolean() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.lt(THING2_KNOWN_BOOL_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByLeString() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.le(THING2_KNOWN_STR_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void findAllByLeNumber() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.le(THING3_KNOWN_NUM_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID, THING3_ID);
    }

    /** */
    @Test
    public void findAllByLeBoolean() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.le(THING2_KNOWN_BOOL_ATTR_VALUE));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID, THING3_ID, THING4_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString1() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR), cf.like(THING1_KNOWN_STR_REGEX_VALUE_TEST));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString2() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR), cf.like(THING2_KNOWN_STR_REGEX_VALUE_TEST));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING2_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString3() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR), cf.like(THING3_KNOWN_STR_REGEX_VALUE_TEST));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING3_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString4() {
        final Criteria crit =
                cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR), cf.like(THING4_KNOWN_STR_REGEX_VALUE_TEST));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING4_ID);
    }

    private void insertThings() {
        final Attributes attributes1 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING1_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING1_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING1_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING1_KNOWN_STR_REGEX_VALUE)
                .build();

        final Attributes attributes2 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING2_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING2_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING2_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING2_KNOWN_STR_REGEX_VALUE)
                .build();

        final Attributes attributes3 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING3_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING3_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING3_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING3_KNOWN_STR_REGEX_VALUE)
                .build();

        final Attributes attributes4 = Attributes.newBuilder()
                .set(KNOWN_STRING_ATTR, THING4_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING4_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING4_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING4_KNOWN_STR_REGEX_VALUE)
                .build();

        persistThing(createThing(THING1_ID).setAttributes(attributes1));
        persistThing(createThing(THING2_ID).setAttributes(attributes2));
        persistThing(createThing(THING3_ID).setAttributes(attributes3));
        persistThing(createThing(THING4_ID).setAttributes(attributes4));
    }
}
