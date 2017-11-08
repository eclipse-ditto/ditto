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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.common.model.ResultListImpl;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.AttributeExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;

/**
 * Tests for sorting functionality of search persistence.
 */
public final class AggregationSortingTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final List<String> THING_IDS = Arrays.asList("thingId1a", "thingId1b", "thingId1c");
    private static final List<String> ATTRIBUTE_SORT_STRING_VALUES =
            Arrays.asList("valA", "valB", "valC", "valD", "valE");
    private static final List<Integer> ATTRIBUTE_SORT_INTEGER_VALUES = Arrays.asList(1, 2, 3, 4, 5);
    private static final String ATTRIBUTE_SORT_KEY = "myAttr1";
    private static final ThingsFieldExpressionFactory EFT = new ThingsFieldExpressionFactoryImpl();

    private static final SortOption DEFAULT_SORT_OPTION = new SortOption(EFT.sortByThingId(), SortDirection.ASC);

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
    public void sortPerDefaultByThingIdAsc() {
        final List<Document> docs = createDocs(THING_IDS, getThingIdDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();
        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedStringSortedResult(DEFAULT_SORT_OPTION, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByThingIdAsc() {
        final SortOption sortOption = new SortOption(EFT.sortByThingId(), SortDirection.ASC);
        final List<Document> docs = createDocs(THING_IDS, getThingIdDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .sortOptions(Collections.singletonList(sortOption))
                .build();
        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedStringSortedResult(sortOption, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByThingIdDesc() {
        final SortOption sortOption = new SortOption(EFT.sortByThingId(), SortDirection.DESC);
        final List<Document> docs = createDocs(THING_IDS, getThingIdDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .sortOptions(Collections.singletonList(sortOption))
                .build();
        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedStringSortedResult(sortOption, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByStringAttributeAsc() {
        final SortOption sortOption = new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), SortDirection.ASC);
        final List<Document> docs = createDocs(ATTRIBUTE_SORT_STRING_VALUES, getStringAttributesDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .sortOptions(Collections.singletonList(sortOption))
                .build();
        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedStringSortedResult(sortOption, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByStringAttributeDesc() {
        final SortOption sortOption = new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), SortDirection.DESC);
        final List<Document> docs = createDocs(ATTRIBUTE_SORT_STRING_VALUES, getStringAttributesDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .sortOptions(Collections.singletonList(sortOption))
                .build();
        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedStringSortedResult(sortOption, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByIntegerAttributeAsc() {
        final SortOption sortOption = new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), SortDirection.ASC);
        final List<Document> docs = createDocs(ATTRIBUTE_SORT_INTEGER_VALUES,
                getIntegerAttributesDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .sortOptions(Collections.singletonList(sortOption))
                .build();
        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedIntegerSortedResult(sortOption, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByIntegerAttributeDesc() {
        final SortOption sortOption = new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), SortDirection.DESC);
        final List<Document> docs = createDocs(ATTRIBUTE_SORT_INTEGER_VALUES,
                getIntegerAttributesDocBuilder());

        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .sortOptions(Collections.singletonList(sortOption))
                .build();

        final ResultList<String> result = findAll(aggregation);

        final List<String> expectedResult = createExpectedIntegerSortedResult(sortOption, docs);
        assertThat(result).isEqualTo(expectedResult);
    }

    private ThingDocumentBuilder buildDoc(final String thingId, final String key, final String value) {
        final ThingDocumentBuilder builder = buildDocWithAclOrPolicy(thingId);
        return builder
                .attribute(key, value)
                .attribute("myAttr2", new Random().nextInt(10));
    }

    private ThingDocumentBuilder buildDoc(final String thingId, final String key, final Integer value) {
        final ThingDocumentBuilder builder = buildDocWithAclOrPolicy(thingId);
        return builder
                .attribute(key, value)
                .attribute("myAttr2", new Random().nextInt(10));
    }

    private Function<String, Document> getThingIdDocBuilder() {
        return (value) -> {
            // note that we don't need policy entries for the attributes, because sorting is supported without
            // permissions on the attributes
            final ThingDocumentBuilder builder = buildDocWithAclOrPolicy(value);
            return builder.build();
        };
    }

    private Function<String, Document> getStringAttributesDocBuilder() {
        return (value) -> buildDoc(randomThingId(), ATTRIBUTE_SORT_KEY, value).build();
    }

    private Function<Integer, Document> getIntegerAttributesDocBuilder() {
        return (value) -> buildDoc(randomThingId(), ATTRIBUTE_SORT_KEY, value).build();
    }

    private <T> List<Document> createDocs(final List<T> values,
            final Function<T, Document> builderFunction) {
        final List<Document> docs = values.stream().map(builderFunction).collect(Collectors.toList());
        // shuffle the documents for more realistic testing
        Collections.shuffle(docs);
        insertDocs(docs);
        return docs;
    }

    private List<String> createExpectedStringSortedResult(final SortOption sortOption, final List<Document> docs) {
        final Comparator<Document> ascendingComparator = Comparator
                .comparing(extractStringField(sortOption.getSortExpression()));
        return createExpectedResult(docs, ascendingComparator, sortOption);
    }

    private List<String> createExpectedIntegerSortedResult(final SortOption sortOption, final List<Document> docs) {
        final Comparator<Document> ascendingComparator = Comparator
                .comparing(extractIntegerField(sortOption.getSortExpression()));
        return createExpectedResult(docs, ascendingComparator, sortOption);
    }

    private static List<String> createExpectedResult(final List<Document> docs, final Comparator<Document>
            ascendingComparator, final SortOption sortOption) {
        final Comparator<Document> comparator = sortOption.getSortDirection() == SortDirection.ASC ?
                ascendingComparator : ascendingComparator.reversed();
        final List<String> simpleList =
                docs.stream()
                        .sorted(comparator)
                        .map(extractStringField(EFT.sortByThingId()))
                        .collect(Collectors.toList());
        return new ResultListImpl<>(simpleList, ResultList.NO_NEXT_PAGE);
    }

    private static Function<Document, String> extractStringField(final SortFieldExpression sortField) {
        return (doc) -> {
            final String sortFieldName;
            if (sortField instanceof SimpleFieldExpressionImpl) {
                sortFieldName = ((SimpleFieldExpressionImpl) sortField).getFieldName();
                return doc.getString(sortFieldName);
            } else {
                sortFieldName = ((AttributeExpressionImpl) sortField).getKey();
                final Document attributes = (Document) doc.get(PersistenceConstants.FIELD_ATTRIBUTES);
                return attributes.getString(sortFieldName);
            }
        };
    }

    private static Function<Document, Integer> extractIntegerField(final FieldExpression sortField) {
        return (doc) -> {
            final String sortFieldName = ((AttributeExpressionImpl) sortField).getKey();
            final Document attributes = (Document) doc.get(PersistenceConstants.FIELD_ATTRIBUTES);
            return attributes.getInteger(sortFieldName);
        };
    }

    private static String randomThingId() {
        return UUID.randomUUID().toString();
    }

}
