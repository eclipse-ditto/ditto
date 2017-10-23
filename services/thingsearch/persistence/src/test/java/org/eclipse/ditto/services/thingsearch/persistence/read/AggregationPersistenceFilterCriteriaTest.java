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

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;

/**
 * Tests for search persistence.
 */
public final class AggregationPersistenceFilterCriteriaTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final String KNOWN_NUMBER_ATTR = "numberAttr";
    private static final String KNOWN_STRING_ATTR = "stringAttr";
    private static final String KNOWN_BOOL_ATTR = "boolAttr";
    private static final String KNOWN_REGEX_ATTR = "stringRegex";

    private static final String THING1_ID = "thing1";
    private static final String THING1_KNOWN_STR_ATTR_VALUE = "a";
    private static final int THING1_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING1_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING2_ID = "thing2";
    private static final String THING2_KNOWN_STR_ATTR_VALUE = "b";
    private static final int THING2_KNOWN_NUM_ATTR_VALUE = 2;
    private static final boolean THING2_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING3_ID = "thing3";
    private static final String THING3_KNOWN_STR_ATTR_VALUE = "c";
    private static final double THING3_KNOWN_NUM_ATTR_VALUE = 3.1;
    private static final boolean THING3_KNOWN_BOOL_ATTR_VALUE = false;
    private static final String THING4_ID = "thing4";
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

    @Override
    void createTestDataV1() {
        insertDocsForAttributeCriteriaV1();
    }

    @Override
    void createTestDataV2() {
        insertDocsForAttributeCriteriaV2();
    }

    /** */
    @Test
    public void findAllByEqString() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void findAllByEqNumber() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.eq(THING2_KNOWN_NUM_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void findAllByEqBoolean() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq(THING3_KNOWN_BOOL_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByNeString() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.ne(THING1_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING2_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByNeNumber() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.ne(THING2_KNOWN_NUM_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByNeBoolean() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.ne(THING3_KNOWN_BOOL_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void neIsEqualToNotEq() {
        final PolicyRestrictedSearchAggregation aggregation = abf.
                newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.ne(THING1_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> resultNe = findAll(aggregation);

        final PolicyRestrictedSearchAggregation aggregation2 = abf.newBuilder(cf.nor(
                Collections.singletonList(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR),
                                cf.eq(THING1_KNOWN_STR_ATTR_VALUE)
                        ))))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> resultNotEq = findAll(aggregation2);

        assertThat(resultNe).isNotEmpty();
        assertThat(resultNe).isEqualTo(resultNotEq);
    }

    /** */
    @Test
    public void findAllByGtString() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.gt(THING2_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByGtNumber() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.gt(THING3_KNOWN_NUM_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING4_ID);
    }

    /** */
    @Test
    public void findAllByGtBoolean() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.gt(THING3_KNOWN_BOOL_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void findAllByGeString() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.ge(THING2_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING2_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByGeNumber() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.ge(THING3_KNOWN_NUM_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByGeBoolean() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.ge(THING3_KNOWN_BOOL_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID, THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByLtString() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.lt(THING2_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void findAllByLtNumber() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.lt(THING3_KNOWN_NUM_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void findAllByLtBoolean() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.lt(THING2_KNOWN_BOOL_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING3_ID, THING4_ID);
    }

    /** */
    @Test
    public void findAllByLeString() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_STRING_ATTR), cf.le(THING2_KNOWN_STR_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void findAllByLeNumber() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.le(THING3_KNOWN_NUM_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID, THING3_ID);
    }

    /** */
    @Test
    public void findAllByLeBoolean() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.fieldCriteria(ef.filterByAttribute(KNOWN_BOOL_ATTR), cf.le(THING2_KNOWN_BOOL_ATTR_VALUE)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID, THING3_ID, THING4_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString1() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR),
                        cf.like(THING1_KNOWN_STR_REGEX_VALUE_TEST)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString2() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR),
                        cf.like(THING2_KNOWN_STR_REGEX_VALUE_TEST)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING2_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString3() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR),
                        cf.like(THING3_KNOWN_STR_REGEX_VALUE_TEST)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING3_ID);
    }

    /**
     * Test to check starts with functionality
     */
    @Test
    public void queryByLikeString4() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.fieldCriteria(ef.filterByAttribute(KNOWN_REGEX_ATTR),
                        cf.like(THING4_KNOWN_STR_REGEX_VALUE_TEST)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING4_ID);
    }

    /**
     * Test whether the aggregation returns any result when filtered by ThingID.
     */
    @Test
    public void queryByThingId() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.fieldCriteria(new SimpleFieldExpressionImpl("_id"), cf.eq(THING1_ID)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    private void insertDocsForAttributeCriteriaV1() {
        final Attributes attributes1 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING1_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING1_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING1_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING1_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes2 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING2_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING2_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING2_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING2_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes3 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING3_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING3_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING3_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING3_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes4 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING4_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING4_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING4_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING4_KNOWN_STR_REGEX_VALUE)
                .build();

        insertDocs(buildDocV1WithAcl(THING1_ID).attributes(attributes1).build(),
                buildDocV1WithAcl(THING2_ID).attributes(attributes2).build(),
                buildDocV1WithAcl(THING3_ID).attributes(attributes3).build(),
                buildDocV1WithAcl(THING4_ID).attributes(attributes4).build());
    }

    private void insertDocsForAttributeCriteriaV2() {
        final Attributes attributes1 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING1_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING1_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING1_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING1_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes2 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING2_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING2_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING2_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING2_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes3 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING3_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING3_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING3_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING3_KNOWN_STR_REGEX_VALUE)
                .build();
        final Attributes attributes4 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_STRING_ATTR, THING4_KNOWN_STR_ATTR_VALUE)
                .set(KNOWN_NUMBER_ATTR, THING4_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR, THING4_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR, THING4_KNOWN_STR_REGEX_VALUE)
                .build();

        insertDocs(buildDocV2WithGlobalReads(THING1_ID).attributes(attributes1).build(),
                buildDocV2WithGlobalReads(THING2_ID).attributes(attributes2).build(),
                buildDocV2WithGlobalReads(THING3_ID).attributes(attributes3).build(),
                buildDocV2WithGlobalReads(THING4_ID).attributes(attributes4).build());

        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" + KNOWN_STRING_ATTR);
        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" + KNOWN_NUMBER_ATTR);
        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_BOOL_ATTR, "attribute/" + KNOWN_BOOL_ATTR);
        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_REGEX_ATTR, "attribute/" + KNOWN_REGEX_ATTR);

        insertPolicyEntry(THING2_ID + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" + KNOWN_STRING_ATTR);
        insertPolicyEntry(THING2_ID + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" + KNOWN_NUMBER_ATTR);
        insertPolicyEntry(THING2_ID + ":attribute/" + KNOWN_BOOL_ATTR, "attribute/" + KNOWN_BOOL_ATTR);
        insertPolicyEntry(THING2_ID + ":attribute/" + KNOWN_REGEX_ATTR, "attribute/" + KNOWN_REGEX_ATTR);

        insertPolicyEntry(THING3_ID + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" + KNOWN_STRING_ATTR);
        insertPolicyEntry(THING3_ID + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" + KNOWN_NUMBER_ATTR);
        insertPolicyEntry(THING3_ID + ":attribute/" + KNOWN_BOOL_ATTR, "attribute/" + KNOWN_BOOL_ATTR);
        insertPolicyEntry(THING3_ID + ":attribute/" + KNOWN_REGEX_ATTR, "attribute/" + KNOWN_REGEX_ATTR);

        insertPolicyEntry(THING4_ID + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" + KNOWN_STRING_ATTR);
        insertPolicyEntry(THING4_ID + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" + KNOWN_NUMBER_ATTR);
        insertPolicyEntry(THING4_ID + ":attribute/" + KNOWN_BOOL_ATTR, "attribute/" + KNOWN_BOOL_ATTR);
        insertPolicyEntry(THING4_ID + ":attribute/" + KNOWN_REGEX_ATTR, "attribute/" + KNOWN_REGEX_ATTR);
    }

}
