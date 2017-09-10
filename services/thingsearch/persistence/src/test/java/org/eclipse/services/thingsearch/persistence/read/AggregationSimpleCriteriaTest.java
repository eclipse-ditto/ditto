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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.eclipse.services.thingsearch.persistence.PersistenceConstants;
import org.junit.Test;

import org.eclipse.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;

/**
 * Tests for search persistence.
 */
public final class AggregationSimpleCriteriaTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final String KNOWN_ATTRIBUTE_KEY = "attributeKey";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final int KNOWN_NUMBER_VALUE = 4711;
    private static final boolean KNOWN_BOOLEAN_VALUE = false;

    private static final String THING_ID_WITH_KNOWN_STR_ATTR_VALUE = "withKnownStrAttrValue";
    private static final String THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE = "withKnownNumberAttrValue";
    private static final String THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE = "withKnownBooleanAttrValue";

    private List<String> persistedDocIds;

    @Override
    void createTestDataV1() {
        persistedDocIds = extractIds(insertDocsForAttributeCriteriaV1());
    }

    @Override
    void createTestDataV2() {
        persistedDocIds = extractIds(insertDocsForAttributeCriteriaV2());
    }

    private static List<String> extractIds(final List<Document> documents) {
        return documents.stream()
                .map(doc -> doc.getString(PersistenceConstants.FIELD_ID))
                .sorted().collect(Collectors.toList());
    }

    /** */
    @Test
    public void findAllByAnyAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.any())
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsExactlyElementsOf(persistedDocIds);
    }

    /** */
    @Test
    public void findAllByStringAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY),
                        AbstractThingSearchPersistenceTestBase.cf.eq(KNOWN_STRING_VALUE)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_STR_ATTR_VALUE);
    }

    /** */
    @Test
    public void findAllByNumberAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY),
                        AbstractThingSearchPersistenceTestBase.cf.eq(KNOWN_NUMBER_VALUE)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE);
    }

    /** */
    @Test
    public void findAllByBooleanAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY),
                        AbstractThingSearchPersistenceTestBase.cf.eq(KNOWN_BOOLEAN_VALUE)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByStringAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY),
                        AbstractThingSearchPersistenceTestBase.cf.ne(KNOWN_STRING_VALUE)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_STR_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByNumberAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY),
                        AbstractThingSearchPersistenceTestBase.cf.ne(KNOWN_NUMBER_VALUE)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByBooleanAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY),
                        AbstractThingSearchPersistenceTestBase.cf.ne(KNOWN_BOOLEAN_VALUE)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE);
    }

    private List<Document> insertDocsForAttributeCriteriaV1() {
        final List<Document> documents =
                Arrays.asList(
                        buildDocV1WithAcl(THING_ID_WITH_KNOWN_STR_ATTR_VALUE).attribute(KNOWN_ATTRIBUTE_KEY,
                                KNOWN_STRING_VALUE)
                                .build(),
                        buildDocV1WithAcl(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE)
                                .attribute(KNOWN_ATTRIBUTE_KEY, KNOWN_NUMBER_VALUE).build(),
                        buildDocV1WithAcl(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE).attribute(KNOWN_ATTRIBUTE_KEY,
                                KNOWN_BOOLEAN_VALUE)
                                .build(),
                        buildDocV1WithAcl("withOtherAttrValue").attribute(KNOWN_ATTRIBUTE_KEY, "otherValue").build(),
                        buildDocV1WithAcl("withOtherIntValue").attribute(KNOWN_ATTRIBUTE_KEY, 4712).build(),
                        buildDocV1WithAcl("withOtherBooleanValue").attribute(KNOWN_ATTRIBUTE_KEY, true).build(),
                        buildDocV1WithAcl("withNullValue")
                                .attribute(KNOWN_ATTRIBUTE_KEY, (String) null).build());
        insertDocs(documents);

        return documents;
    }

    private List<Document> insertDocsForAttributeCriteriaV2() {
        final List<Document> documents =
                Arrays.asList(
                        buildDocV2WithGlobalReads(THING_ID_WITH_KNOWN_STR_ATTR_VALUE).attribute(KNOWN_ATTRIBUTE_KEY,
                                KNOWN_STRING_VALUE)
                                .build(),
                        buildDocV2WithGlobalReads(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE)
                                .attribute(KNOWN_ATTRIBUTE_KEY, KNOWN_NUMBER_VALUE).build(),
                        buildDocV2WithGlobalReads(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE)
                                .attribute(KNOWN_ATTRIBUTE_KEY,
                                        KNOWN_BOOLEAN_VALUE)
                                .build(),
                        buildDocV2WithGlobalReads("withOtherAttrValue").attribute(KNOWN_ATTRIBUTE_KEY, "otherValue")
                                .build(),
                        buildDocV2WithGlobalReads("withOtherIntValue").attribute(KNOWN_ATTRIBUTE_KEY, 4712)
                                .build(),
                        buildDocV2WithGlobalReads("withOtherBooleanValue").attribute(KNOWN_ATTRIBUTE_KEY, true)
                                .build(),
                        buildDocV2WithGlobalReads("withNullValue")
                                .attribute(KNOWN_ATTRIBUTE_KEY, (String) null).build());
        insertDocs(documents);

        insertPolicyEntry(THING_ID_WITH_KNOWN_STR_ATTR_VALUE + ":attribute/" + KNOWN_ATTRIBUTE_KEY, "attribute/" +
                KNOWN_ATTRIBUTE_KEY);
        insertPolicyEntry(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE + ":attribute/" + KNOWN_ATTRIBUTE_KEY, "attribute/" +
                KNOWN_ATTRIBUTE_KEY);
        insertPolicyEntry(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE + ":attribute/" + KNOWN_ATTRIBUTE_KEY, "attribute/" +
                KNOWN_ATTRIBUTE_KEY);

        return documents;
    }

}
