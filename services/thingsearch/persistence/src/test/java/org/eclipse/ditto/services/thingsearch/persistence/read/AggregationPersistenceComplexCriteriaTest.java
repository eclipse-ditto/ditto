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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class AggregationPersistenceComplexCriteriaTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final String OTHER_STRING_VALUE = "otherValue";

    private static final String THING_ID_WITH_KEY_1_AND_2 = "with1and2";
    private static final String THING_ID_WITH_KEY_1_ONLY = "with1Only";
    private static final String THING_ID_WITH_KEY_2_ONLY = "with2Only";
    private static final String THING_ID_WITH_OTHER_VALUE_1 = "withOtherValue1";
    private static final String THING_ID_WITH_OTHER_VALUE_2 = "withOtherValue2";
    private static final String THING_ID_WITH_NO_KEY = "withNoKey";

    @Override
    void createTestDataV1() {
        insertDocsForComplexCriteriaV1();
    }

    @Override
    void createTestDataV2() {
        insertDocsForComplexCriteriaV2();
    }

    /** */
    @Test
    public void findAllWithAndCriteria() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf.newBuilder(
                AbstractThingSearchPersistenceTestBase.cf.and(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2);
    }

    /** */
    @Test
    public void findAllWithNoredAndCriteria() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf.newBuilder(
                AbstractThingSearchPersistenceTestBase.cf.nor(AbstractThingSearchPersistenceTestBase.cf.and(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1, KNOWN_STRING_VALUE),
                        searchForKey(KNOWN_ATTRIBUTE_KEY_1, OTHER_STRING_VALUE)))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY,
                THING_ID_WITH_OTHER_VALUE_1, THING_ID_WITH_NO_KEY, THING_ID_WITH_KEY_2_ONLY,
                THING_ID_WITH_OTHER_VALUE_2);
    }


    /** */
    @Test
    public void findAllWithOrCriteria() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf.newBuilder(
                AbstractThingSearchPersistenceTestBase.cf.or(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY, THING_ID_WITH_KEY_2_ONLY);
    }

    /** */
    @Test
    public void findAllWithNoredOrCriteria() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf.newBuilder(
                AbstractThingSearchPersistenceTestBase.cf.nor(AbstractThingSearchPersistenceTestBase.cf.or(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2)))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_ID_WITH_NO_KEY, THING_ID_WITH_OTHER_VALUE_1, THING_ID_WITH_OTHER_VALUE_2);
    }


    /** */
    @Test
    public void findAllWithNorCriteria() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf.newBuilder(
                AbstractThingSearchPersistenceTestBase.cf.nor(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);

        assertThat(result).containsOnly(THING_ID_WITH_NO_KEY, THING_ID_WITH_OTHER_VALUE_1, THING_ID_WITH_OTHER_VALUE_2);
    }

    /** */
    @Test
    public void findAllWithNoredNorCriteria() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf.newBuilder(
                AbstractThingSearchPersistenceTestBase.cf.nor(AbstractThingSearchPersistenceTestBase.cf.nor(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2)))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY, THING_ID_WITH_KEY_2_ONLY);
    }

    private Criteria searchForKey(final String key, final String value) {
        return AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(AbstractThingSearchPersistenceTestBase.fef.filterByAttribute(key), AbstractThingSearchPersistenceTestBase.cf.eq(value));
    }

    private Criteria searchForKey(final String key) {
        return AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(AbstractThingSearchPersistenceTestBase.fef.filterByAttribute(key), AbstractThingSearchPersistenceTestBase.cf.eq(KNOWN_STRING_VALUE));
    }

    private void insertDocsForComplexCriteriaV1() {
        final Attributes attributes1 = ThingsModelFactory.newAttributesBuilder()
                .set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_1), KNOWN_STRING_VALUE)
                .set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_2), KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes2 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes3 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes4 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, OTHER_STRING_VALUE)
                .build();

        final Attributes attributes5 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, OTHER_STRING_VALUE)
                .build();

        insertDocs(buildDocV1WithAcl(THING_ID_WITH_KEY_1_AND_2).attributes(attributes1).build(),
                buildDocV1WithAcl(THING_ID_WITH_KEY_1_ONLY).attributes(attributes2).build(),
                buildDocV1WithAcl(THING_ID_WITH_KEY_2_ONLY).attributes(attributes3).build(),
                buildDocV1WithAcl(THING_ID_WITH_OTHER_VALUE_1).attributes(attributes4).build(),
                buildDocV1WithAcl(THING_ID_WITH_OTHER_VALUE_2).attributes(attributes5).build(),
                buildDocV1WithAcl(THING_ID_WITH_NO_KEY).build());
    }

    private void insertDocsForComplexCriteriaV2() {
        final Attributes attributes1 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, KNOWN_STRING_VALUE)
                .set(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes2 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes3 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes4 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, OTHER_STRING_VALUE)
                .build();

        final Attributes attributes5 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, OTHER_STRING_VALUE)
                .build();

        insertDocs(buildDocV2WithGlobalReads(THING_ID_WITH_KEY_1_AND_2).attributes(attributes1).build(),
                buildDocV2WithGlobalReads(THING_ID_WITH_KEY_1_ONLY).attributes(attributes2).build(),
                buildDocV2WithGlobalReads(THING_ID_WITH_KEY_2_ONLY).attributes(attributes3).build(),
                buildDocV2WithGlobalReads(THING_ID_WITH_OTHER_VALUE_1).attributes(attributes4).build(),
                buildDocV2WithGlobalReads(THING_ID_WITH_OTHER_VALUE_2).attributes(attributes5).build(),
                buildDocV2WithGlobalReads(THING_ID_WITH_NO_KEY).build());

        insertPolicyEntry(THING_ID_WITH_KEY_1_AND_2 + ":attribute/" + KNOWN_ATTRIBUTE_KEY_1, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_1);
        insertPolicyEntry(THING_ID_WITH_KEY_1_AND_2 + ":attribute/" + KNOWN_ATTRIBUTE_KEY_2, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_2);
        insertPolicyEntry(THING_ID_WITH_KEY_1_ONLY + ":attribute/" + KNOWN_ATTRIBUTE_KEY_1, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_1);
        insertPolicyEntry(THING_ID_WITH_KEY_2_ONLY + ":attribute/" + KNOWN_ATTRIBUTE_KEY_2, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_2);
        insertPolicyEntry(THING_ID_WITH_OTHER_VALUE_1 + ":attribute/" + KNOWN_ATTRIBUTE_KEY_1, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_1);
        insertPolicyEntry(THING_ID_WITH_OTHER_VALUE_2 + ":attribute/" + KNOWN_ATTRIBUTE_KEY_2, "attribute/" +
                KNOWN_ATTRIBUTE_KEY_2);
    }

}
