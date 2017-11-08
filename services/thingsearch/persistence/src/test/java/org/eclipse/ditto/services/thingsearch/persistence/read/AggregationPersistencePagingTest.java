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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.common.model.ResultList;

import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryConstants;

/**
 * Tests for the paging functionality of search persistence.
 */
public final class AggregationPersistencePagingTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final int KNOWN_LIMIT = 2;
    private static final String THING_ID3 = "thingId3";
    private static final String THING_ID2 = "thingId2";
    private static final String THING_ID1 = "thingId1";
    private static final String THING_ID4 = "thingId4";
    private static final String THING_ID5 = "thingId5";
    private static final String THING_ID6 = "thingId6";
    private static final List<String> THING_IDS = Arrays.asList(THING_ID1, THING_ID2, THING_ID3, THING_ID4, THING_ID5,
            THING_ID6);

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
    public void pageWithItemsCountLessThanLimit() {
        // prepare
        final List<String> oneThingList = Collections.singletonList(THING_ID1);
        insertDocIds(oneThingList);

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .limit(KNOWN_LIMIT)
                .build();

        // test
        final ResultList<String> result = findAll(aggregation);

        // verify
        assertPaging(result, oneThingList, ResultList.NO_NEXT_PAGE);
    }

    /** */
    @Test
    public void pageWithItemsCountEqualToLimit() {
        // prepare
        insertDocIds(THING_IDS);
        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .limit(KNOWN_LIMIT)
                .build();

        // test
        final ResultList<String> result = findAll(aggregation);

        // verify
        final List<String> expectedList = Arrays.asList(THING_ID1, THING_ID2);
        assertPaging(result, expectedList, KNOWN_LIMIT);
    }

    /** */
    @Test
    public void pageWithSkipAndLimitLessThanTotalItems() {
        // prepare
        insertDocIds(THING_IDS);
        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .limit(KNOWN_LIMIT)
                .skip(KNOWN_LIMIT)
                .build();

        // test
        final ResultList<String> result = findAll(aggregation);

        // verify
        final List<String> expectedList = Arrays.asList(THING_ID3, THING_ID4);
        assertPaging(result, expectedList, KNOWN_LIMIT * 2);
    }

    /** */
    @Test
    public void lastPageWithItemsCountLessThanLimit() {
        // prepare
        final List<String> docIds = Arrays.asList(THING_ID1, THING_ID2, THING_ID3, THING_ID4, THING_ID5);
        insertDocIds(docIds);

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .limit(KNOWN_LIMIT)
                .skip(KNOWN_LIMIT * 2)
                .build();

        // test
        final ResultList<String> result = findAll(aggregation);

        // verify
        final List<String> expectedList = Collections.singletonList(THING_ID5);
        assertPaging(result, expectedList, ResultList.NO_NEXT_PAGE);
    }

    /** */
    @Test
    public void lastPageWithItemsCountEqualToLimit() {
        // prepare
        insertDocIds(THING_IDS);

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .limit(KNOWN_LIMIT)
                .skip(KNOWN_LIMIT * 2)
                .build();

        // test
        final ResultList<String> result = findAll(aggregation);

        // verify
        final List<String> expectedList = Arrays.asList(THING_ID5, THING_ID6);
        assertPaging(result, expectedList, ResultList.NO_NEXT_PAGE);
    }

    /** */
    @Test
    public void defaultSkipValue() {
        pageWithItemsCountEqualToLimit();
    }

    /** */
    @Test
    public void defaultLimitValue() {
        // prepare
        final int moreThanLimit = 30;
        final long totalDocsCount = QueryConstants.DEFAULT_LIMIT + moreThanLimit;
        final List<String> allDocs = new ArrayList<>((int) totalDocsCount);
        for (int i = 0; i < totalDocsCount; i++) {
            final String thingId = "thingId" + String.format("%03d", i);
            insertDocId(thingId);
            allDocs.add(thingId);
        }

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        // test
        final ResultList<String> result = findAll(aggregation);

        // verify
        final List<String> expectedList = allDocs.subList(0, QueryConstants.DEFAULT_LIMIT);
        assertPaging(result, expectedList, QueryConstants.DEFAULT_LIMIT);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void limitValueExceedsMaximum() {
        abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .limit(QueryConstants.MAX_LIMIT + 1)
                .build();
    }

    private static void assertPaging(final ResultList<String> actualResult, final List<String> expectedList,
            final long expectedNextPageOffset) {

        assertThat(actualResult).containsOnly(expectedList.toArray(new String[expectedList.size()]));
        assertThat(actualResult.nextPageOffset()).isEqualTo(expectedNextPageOffset);
    }

    private void insertDocIds(final List<String> documents) {
        documents.forEach(this::insertDocId);
    }

    private void insertDocId(final String thingId) {
        final ThingDocumentBuilder builder = buildDocWithAclOrPolicy(thingId);

        final Document doc = builder.build();
        insertDoc(doc);
    }

}
