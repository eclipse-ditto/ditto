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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.junit.Before;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;

/**
 * Tests the correct authorization checking on searches by policies.
 */
public final class AggregationPolicyAuthTest extends AbstractReadPersistenceTestBase {

    private static final String KNOWN_NUMBER_ATTR = "numberAttr";
    private static final String KNOWN_STRING_ATTR = "stringAttr";
    private static final String KNOWN_BOOL_ATTR = "boolAttr";
    private static final String KNOWN_REGEX_ATTR = "stringRegex";

    private static final String KNOWN_NUMBER_ATTR2 = "numberAttr2";
    private static final String KNOWN_BOOL_ATTR2 = "boolAttr2";
    private static final String KNOWN_REGEX_ATTR2 = "stringRegex2";


    private static final String THING1_ID = "thing1";
    private static final String THING1_KNOWN_STR_ATTR_VALUE = "a";
    private static final int THING1_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING1_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING2_ID = "thing2";
    private static final String THING2_KNOWN_STR_ATTR_VALUE = "b";
    private static final int THING2_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING2_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING3_ID = "thing3";
    private static final String THING3_KNOWN_STR_ATTR_VALUE = "c";
    private static final double THING3_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING3_KNOWN_BOOL_ATTR_VALUE = true;
    private static final String THING4_ID = "thing4";
    private static final String THING5_ID = "thing5";
    private static final String THING4_KNOWN_STR_ATTR_VALUE = "d";
    private static final int THING4_KNOWN_NUM_ATTR_VALUE = 1;
    private static final boolean THING4_KNOWN_BOOL_ATTR_VALUE = false;

    private static final String THING1_KNOWN_STR_REGEX_VALUE = "Das ist ein X belibiger String"; // starts with
    private static final String THING2_KNOWN_STR_REGEX_VALUE = "Der zweite Das String"; // contains
    private static final String THING3_KNOWN_STR_REGEX_VALUE = "Teststring nummer drei"; // wildcard
    private static final String THING4_KNOWN_STR_REGEX_VALUE = "Der vierte und letzte Teststring"; // ends with

    private static final List<String> SUBJECTS_USER_1 = Collections.singletonList("user1");
    private static final List<String> SUBJECTS_USER_2 = Collections.singletonList("user2");
    private static final List<String> SUBJECTS_USER_1_AND_2 = Arrays.asList("user1", "user2");

    @Before
    @Override
    public void before() {
        super.before();
        insertDocsForAttributeCriteriaV2();
    }

    @Test
    public void grantedAccessWithAnd() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.and(Arrays.asList(
                        cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq(THING1_KNOWN_STR_ATTR_VALUE)),
                        cf.fieldCriteria(fef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.lt(200)))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void notGrantedAccessWithAnd() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.and(Arrays.asList(cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq
                        (THING1_KNOWN_STR_ATTR_VALUE)), cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq
                        (THING1_KNOWN_BOOL_ATTR_VALUE)))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    @Test
    public void grantedAccessWithOr() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.or(Arrays.asList(cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq
                        (THING1_KNOWN_STR_ATTR_VALUE)), cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq
                        (THING1_KNOWN_BOOL_ATTR_VALUE)))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void notGrantedAccessWithOr() {
        // user1 has access to KNOWN_STRING_ATTR but no access to KNOWN_BOOL_ATTR.
        // KNOWN_STRING_ATTR does not match the search criteria but KNOWN_BOOL_ATTR does.
        // user1 should get an empty result because the matching attribute isn't visible.
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.or(Arrays.asList(
                        cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq("does_not_match")),
                        cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.eq(THING1_KNOWN_BOOL_ATTR_VALUE)))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    @Test
    public void grantedAccessWithNotOr() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.nor(cf.or(Arrays.asList(
                        cf.fieldCriteria(fef.filterByAttribute(KNOWN_STRING_ATTR), cf.eq("does_not_match")),
                        cf.fieldCriteria(fef.filterByAttribute(KNOWN_BOOL_ATTR), cf.ne(THING1_KNOWN_BOOL_ATTR_VALUE))))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING5_ID);
    }

    @Test
    public void grantedAccessWithExists() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByAttribute(KNOWN_STRING_ATTR)))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void notGrantedAccessWithExists() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByAttribute(KNOWN_BOOL_ATTR)))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    @Test
    public void grantedAccessWithNotExists() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.nor(cf.existsCriteria(fef.existsByAttribute(KNOWN_STRING_ATTR))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);

        assertThat(result).containsOnly(THING5_ID);
    }

    @Test
    public void notGrantedAccessWithNotExists() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.nor(Collections.singletonList(cf.existsCriteria(fef.existsByAttribute(KNOWN_BOOL_ATTR)))))
                .authorizationSubjects(SUBJECTS_USER_1)
                .build();

        final Collection<String> result = findAll(aggregation);

        assertThat(result).containsOnly(THING5_ID);
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
        final Attributes attributes5 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_NUMBER_ATTR2, THING4_KNOWN_NUM_ATTR_VALUE)
                .set(KNOWN_BOOL_ATTR2, THING4_KNOWN_BOOL_ATTR_VALUE)
                .set(KNOWN_REGEX_ATTR2, THING4_KNOWN_STR_REGEX_VALUE)
                .build();

        insertDocs(buildDocV2WithGlobalReads(THING1_ID, SUBJECTS_USER_1_AND_2).attributes(attributes1).build(),
                buildDocV2WithGlobalReads(THING2_ID).attributes(attributes2).build(),
                buildDocV2WithGlobalReads(THING3_ID).attributes(attributes3).build(),
                buildDocV2WithGlobalReads(THING4_ID).attributes(attributes4).build(),
                buildDocV2WithGlobalReads(THING5_ID, SUBJECTS_USER_1_AND_2).attributes(attributes5).build());

        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" + KNOWN_STRING_ATTR,
                SUBJECTS_USER_1);
        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" + KNOWN_NUMBER_ATTR,
                SUBJECTS_USER_1);
        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_BOOL_ATTR, "attribute/" + KNOWN_BOOL_ATTR, SUBJECTS_USER_2);
        insertPolicyEntry(THING1_ID + ":attribute/" + KNOWN_REGEX_ATTR, "attribute/" + KNOWN_REGEX_ATTR,
                SUBJECTS_USER_2);
        insertPolicyEntry(THING5_ID + ":attribute/" + KNOWN_BOOL_ATTR2, "attribute/" + KNOWN_BOOL_ATTR2,
                SUBJECTS_USER_1);

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

        insertPolicyEntry(THING5_ID + ":attribute/" + KNOWN_NUMBER_ATTR2, "attribute/" + KNOWN_NUMBER_ATTR);
        insertPolicyEntry(THING5_ID + ":attribute/" + KNOWN_BOOL_ATTR2, "attribute/" + KNOWN_BOOL_ATTR);
        insertPolicyEntry(THING5_ID + ":attribute/" + KNOWN_REGEX_ATTR2, "attribute/" + KNOWN_REGEX_ATTR);
    }

}
