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

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.eclipse.ditto.services.thingsearch.persistence.MongoSortKeyMappingFunction;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.common.model.ResultListImpl;
import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.AttributeExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FeatureIdPropertyExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;

/**
 * Tests for sorting functionality of search persistence.
 */
public final class MongoSearchPersistenceSortingTest extends AbstractReadPersistenceTestBase {

    private static final List<String> THING_IDS = Arrays.asList("thingId1a", "thingId1b", "thingId1c");
    private static final List<String> ATTRIBUTE_SORT_STRING_VALUES =
            Arrays.asList("valA", "valB", "valC", "valD", "valE");
    private static final List<Integer> ATTRIBUTE_SORT_INTEGER_VALUES = Arrays.asList(1, 2, 3, 4, 5);
    private static final String ATTRIBUTE_SORT_KEY = "myAttr1";
    private static final String ATTRIBUTE_SORT_KEY_WITH_DOTS = "myAttr.with.dots";
    private static final String FEATURE_ID_WITH_DOTS = "myFeatureId.with.dots";
    private static final String PROPERTY_SORT_KEY_WITH_DOTS = "myProperty.with.dots";
    private static final ThingsFieldExpressionFactory EFT = new ThingsFieldExpressionFactoryImpl();
    private final CriteriaFactory cf = new CriteriaFactoryImpl();

    private static List<String> createExpectedResult(final Collection<Document> docs,
            final Comparator<Document> comparator) {

        final List<String> simpleList = docs.stream()
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
                return attributes.getString(KeyEscapeUtil.escape(sortFieldName));
            }
        };
    }

    private static Function<Document, Integer> extractIntegerField(final FieldExpression sortField) {
        return (doc) -> {
            if (sortField instanceof AttributeExpressionImpl) {
                final String sortFieldName = ((AttributeExpressionImpl) sortField).getKey();
                final Document attributes = (Document) doc.get(PersistenceConstants.FIELD_ATTRIBUTES);
                return attributes.getInteger(sortFieldName);
            } else if (sortField instanceof FeatureIdPropertyExpressionImpl) {
                final String featureId = ((FeatureIdPropertyExpressionImpl) sortField).getFeatureId();
                final String propertyId = ((FeatureIdPropertyExpressionImpl) sortField).getProperty();
                final Document features = (Document) doc.get(PersistenceConstants.FIELD_FEATURES);
                final Document feature = ((Document) features.get(MongoSortKeyMappingFunction.mapSortKey(featureId)));
                final Document properties = ((Document) feature.get(PersistenceConstants.FIELD_PROPERTIES));
                return properties.getLong(MongoSortKeyMappingFunction.mapSortKey(propertyId)).intValue();
            } else {
                throw new UnsupportedOperationException(sortField.getClass().getName() + " not supported");
            }
        };
    }

    private static String randomThingId() {
        return UUID.randomUUID().toString();
    }

    /** */
    @Test
    public void sortByThingIdAsc() {
        runTestWithStringValues(EFT.sortByThingId(), getThingIdDocBuilder(), THING_IDS, SortDirection.ASC);
    }

    /** */
    @Test
    public void sortByThingIdDesc() {
        runTestWithStringValues(EFT.sortByThingId(), getThingIdDocBuilder(), THING_IDS, SortDirection.DESC);
    }

    /** */
    @Test
    public void sortByStringAttributeAsc() {
        runTestWithStringValues(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), getStringAttributesDocBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES, SortDirection.ASC);
    }

    /** */
    @Test
    public void sortByStringAttributeDesc() {
        runTestWithStringValues(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), getStringAttributesDocBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES, SortDirection.DESC);
    }

    /** */
    @Test
    public void sortByStringAttributeWithDotsDesc() {
        runTestWithStringValues(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY_WITH_DOTS),
                getStringAttributesWithDotsDocBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES, SortDirection.DESC);
    }

    /** */
    @Test
    public void sortByIntegerAttributeAsc() {
        runTestWithIntegerValues(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), getIntegerAttributesDocBuilder(),
                ATTRIBUTE_SORT_INTEGER_VALUES, SortDirection.ASC);
    }

    /** */
    @Test
    public void sortByIntegerAttributeDesc() {
        runTestWithIntegerValues(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), getIntegerAttributesDocBuilder(),
                ATTRIBUTE_SORT_INTEGER_VALUES, SortDirection.DESC);
    }

    /** */
    @Test
    public void sortByIntegerPropertyWithDotsAsc() {
        runTestWithIntegerValues(EFT.sortByFeatureProperty(FEATURE_ID_WITH_DOTS, PROPERTY_SORT_KEY_WITH_DOTS),
                getIntegerPropertyWithDotsDocBuilder(),
                ATTRIBUTE_SORT_INTEGER_VALUES, SortDirection.ASC);
    }

    private void runTestWithIntegerValues(final SortFieldExpression sortField,
            final Function<Integer, Document> docBuilder,
            final Collection<Integer> values,
            final SortDirection sortDirection) {

        final List<Document> docs = createDocsWithInteger(values, docBuilder);
        final Comparator<Document> ascendingComparator = Comparator.comparing(extractIntegerField(sortField));

        runInternally(sortField, sortDirection, docs, ascendingComparator);
    }

    private void runTestWithStringValues(final SortFieldExpression sortField,
            final Function<String, Document> docBuilder,
            final Collection<String> values,
            final SortDirection sortDirection) {

        final List<Document> docs = createDocsWithStrings(values, docBuilder);
        final Comparator<Document> ascendingComparator = Comparator.comparing(extractStringField(sortField));

        runInternally(sortField, sortDirection, docs, ascendingComparator);
    }

    private void runInternally(final SortFieldExpression sortField,
            final SortDirection sortDirection,
            final Collection<Document> docs,
            final Comparator<Document> ascendingComparator) {

        final Comparator<Document> comparator;
        if (SortDirection.ASC == sortDirection) {
            comparator = ascendingComparator;
        } else {
            comparator = ascendingComparator.reversed();
        }

        final List<String> expectedResult = createExpectedResult(docs, comparator);

        final List<SortOption> sortOptions = Collections.singletonList(new SortOption(sortField, sortDirection));
        final Query query = AbstractThingSearchPersistenceTestBase.qbf.newBuilder(cf.any()).sort(sortOptions).build();

        final ResultList<String> result = findAll(query);

        assertThat(result).isEqualTo(expectedResult);
    }

    private static Function<String, Document> getThingIdDocBuilder() {
        return (value) -> ThingDocumentBuilder.create(value).build();
    }

    private static Function<String, Document> getStringAttributesDocBuilder() {
        return (value) -> ThingDocumentBuilder.create(randomThingId())
                .attribute(ATTRIBUTE_SORT_KEY, value)
                .attribute("myAttr2", new Random().nextInt(10))
                .build();
    }

    private static Function<String, Document> getStringAttributesWithDotsDocBuilder() {
        return (value) -> ThingDocumentBuilder.create(randomThingId())
                .attribute(ATTRIBUTE_SORT_KEY_WITH_DOTS, value)
                .attribute("myAttr2", new Random().nextInt(10))
                .build();
    }

    private static Function<Integer, Document> getIntegerAttributesDocBuilder() {
        return (value) -> ThingDocumentBuilder.create(randomThingId())
                .attribute(ATTRIBUTE_SORT_KEY, value)
                .attribute("myAttr2", new Random().nextInt(10))
                .build();
    }

    private static Function<Integer, Document> getIntegerPropertyWithDotsDocBuilder() {
        return (value) -> {
            final Features features = Features.newBuilder()
                    .set(Feature.newBuilder()
                            .properties(
                                    FeatureProperties.newBuilder()
                                            .set(PROPERTY_SORT_KEY_WITH_DOTS, value)
                                            .build())
                            .withId(FEATURE_ID_WITH_DOTS)
                            .build())
                    .build();

            return ThingDocumentBuilder.create(randomThingId())
                    .features(features)
                    .attribute("myAttr2", new Random().nextInt(10))
                    .build();
        };
    }

    private List<Document> createDocsWithInteger(final Collection<Integer> values,
            final Function<Integer, Document> builderFunction) {

        final List<Document> docs = values.stream()
                .map(builderFunction)
                .collect(Collectors.toList());
        // shuffle the documents for more realistic testing
        Collections.shuffle(docs);
        insertDocs(docs);
        return docs;
    }

    private List<Document> createDocsWithStrings(final Collection<String> values,
            final Function<String, Document> builderFunction) {

        final List<Document> docs = values.stream()
                .map(builderFunction)
                .collect(Collectors.toList());
        // shuffle the documents for more realistic testing
        Collections.shuffle(docs);
        insertDocs(docs);
        return docs;
    }

}
