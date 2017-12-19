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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.common.model.ResultListImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.AttributeExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FeatureIdPropertyExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for sorting functionality of search persistence.
 */
@RunWith(Parameterized.class)
public final class SortingIT extends AbstractVersionedThingSearchPersistenceITBase {

    private static final List<SortDirection> SORT_DIRECTIONS = Arrays.asList(SortDirection.values());
    private static final Random RANDOM = new Random();

    private static final ThingsFieldExpressionFactory EFT = new ThingsFieldExpressionFactoryImpl();

    private static final List<String> THING_IDS =
            Arrays.asList("thingsearch.read:thingId1a", "thingsearch.read:thingId1b", "thingsearch.read:thingId1c");
    private static final List<String> ATTRIBUTE_SORT_STRING_VALUES =
            Arrays.asList("valA", "valB", "valC", "valD", "valE");
    private static final List<Long> ATTRIBUTE_SORT_LONG_VALUES = Arrays.asList(1L, 2L, 3L, 4L, 5L);
    private static final String ATTRIBUTE_SORT_KEY = "myAttr1";
    private static final String ATTRIBUTE_SORT_KEY_WITH_DOTS = "myAttr.with.dots";
    private static final String FEATURE_ID_WITH_DOTS = "myFeatureId.with.dots";
    private static final String PROPERTY_SORT_KEY_WITH_DOTS = "myProperty.with.dots";

    @Parameterized.Parameters(name = "v{0} - {1} - {2}")
    public static List<Object[]> parameters() {
        final List<Object[]> versionsWithQueryClass = versionAndQueryClassParameters();
        final Object[] sortDirections = SORT_DIRECTIONS.toArray();
        final List<Object[]> parameters = new ArrayList<>();
        for (final Object[] versionAndQueryClass : versionsWithQueryClass) {
            for (final Object sortDirection : sortDirections) {
                final List<Object> singleParamList = new ArrayList<>(Arrays.asList(versionAndQueryClass));
                singleParamList.add(sortDirection);
                parameters.add(singleParamList.toArray());
            }
        }
        return parameters;
    }

    private final CriteriaFactory cf = new CriteriaFactoryImpl();

    @Parameterized.Parameter(2)
    public SortDirection testedSortDirection;


    @Override
    void createTestDataV1() {
        // test data is created in the tests
    }

    @Override
    void createTestDataV2() {
        // test data is created in the tests
    }

    /** */
    @Test
    public void sortPerDefaultByThingIdAsc() {
        final SortOption DEFAULT_SORT_OPTION = new SortOption(EFT.sortByThingId(), SortDirection.ASC);
        final List<Thing> things = createAndPersistThings(THING_IDS, this::createThing);

        // find without any ordering
        final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();
        final ResultList<String> result = findAll(aggregation);

        final Comparator<Thing> ascendingComparator =
                Comparator.comparing(extractStringField(DEFAULT_SORT_OPTION.getSortExpression()));

        final List<String> expectedResult = createExpectedResult(things, DEFAULT_SORT_OPTION, ascendingComparator);

        assertThat(result).isEqualTo(expectedResult);
    }

    /** */
    @Test
    public void sortByThingId() {
        runTestWithStringValues(
                new SortOption(EFT.sortByThingId(), testedSortDirection), this::createThing, THING_IDS);
    }

    /** */
    @Test
    public void sortByStringAttribute() {
        runTestWithStringValues(
                new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), testedSortDirection),
                getStringAttributesThingBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES);
    }


    /** */
    @Test
    public void sortByStringAttributeWithDots() {
        runTestWithStringValues(
                new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY_WITH_DOTS), testedSortDirection),
                getStringAttributesWithDotsThingBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES);
    }

    /** */
    @Test
    public void sortByLongAttribute() {
        runTestWithLongValues(
                new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), testedSortDirection),
                getLongAttributesThingBuilder()
        );
    }


    /** */
    @Test
    public void sortByLongPropertyWithDots() {
        runTestWithLongValues(
                new SortOption(
                        EFT.sortByFeatureProperty(FEATURE_ID_WITH_DOTS, PROPERTY_SORT_KEY_WITH_DOTS),
                        testedSortDirection),
                getLongPropertyWithDotsThingBuilder()
        );
    }

    private void runTestWithLongValues(final SortOption sortOption,
            final Function<Long, Thing> thingBuilder) {
        final List<Thing> things = createAndPersistThings(ATTRIBUTE_SORT_LONG_VALUES, thingBuilder);

        final Comparator<Thing> ascendingComparator =
                Comparator.comparing(extractLongField(sortOption.getSortExpression()));

        runInternallyWithThings(sortOption, things, ascendingComparator);
    }

    private void runTestWithStringValues(final SortOption sortOption,
            final Function<String, Thing> thingBuilder,
            final Collection<String> values) {

        final List<Thing> things = createAndPersistThings(values, thingBuilder);
        final Comparator<Thing> ascendingComparator =
                Comparator.comparing(extractStringField(sortOption.getSortExpression()));

        runInternallyWithThings(sortOption, things, ascendingComparator);
    }

    private void runInternallyWithThings(final SortOption sortOption,
            final Collection<Thing> things,
            final Comparator<Thing> ascendingComparator) {

        final List<String> expectedResult = createExpectedResult(things, sortOption, ascendingComparator);

        final ResultList<String> result;
        if (queryClass.equals(Query.class.getSimpleName())) {
            final Query query = qbf.newBuilder(cf.any()).sort(Collections.singletonList(sortOption)).build();
            result = findAll(query);
        } else if (queryClass.equals(PolicyRestrictedSearchAggregation.class.getSimpleName())) {
            final PolicyRestrictedSearchAggregation aggregation = abf.newBuilder(cf.any())
                    .authorizationSubjects(KNOWN_SUBJECTS)
                    .sortOptions(Collections.singletonList(sortOption))
                    .build();
            result = findAll(aggregation);
        } else {
            throw new IllegalStateException("should never end up here");
        }
        assertThat(result).isEqualTo(expectedResult);
    }


    private Function<String, Thing> getStringAttributesThingBuilder() {
        return (value) ->
                createThing(randomThingId())
                        .setAttribute(ATTRIBUTE_SORT_KEY, value)
                        .setAttribute("myAttr2", RANDOM.nextInt(10));
    }

    private Function<Long, Thing> getLongAttributesThingBuilder() {
        return (value) -> createThing(randomThingId())
                .setAttribute(ATTRIBUTE_SORT_KEY, value)
                .setAttribute("myAttr2", RANDOM.nextInt(10));
    }


    private Function<String, Thing> getStringAttributesWithDotsThingBuilder() {
        return (value) -> createThing(randomThingId())
                .setAttribute(ATTRIBUTE_SORT_KEY_WITH_DOTS, value)
                .setAttribute("myAttr2", RANDOM.nextInt(10));
    }

    private Function<Long, Thing> getLongPropertyWithDotsThingBuilder() {
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

            return createThing(randomThingId())
                    .setFeatures(features)
                    .setAttribute("myAttr2", RANDOM.nextInt(10));
        };
    }

    private <E> List<Thing> createAndPersistThings(final Collection<E> values, final Function<E, Thing>
            builderFunction) {

        final List<Thing> things = values.stream()
                .map(builderFunction)
                .collect(Collectors.toList());
        // shuffle the documents for more realistic testing
        Collections.shuffle(things);
        things.forEach(this::persistThing);
        return things;
    }


    private Function<Thing, String> extractStringField(final FieldExpression sortField) {
        return (thing) -> {
            if (sortField instanceof SimpleFieldExpressionImpl) {
                return thing.getId().orElseThrow(IllegalStateException::new);
            } else {
                return thing.getAttributes()
                        .orElseThrow(IllegalStateException::new)
                        .getValue(((AttributeExpressionImpl) sortField).getKey())
                        .orElseThrow(IllegalStateException::new)
                        .asString();
            }
        };
    }

    private Function<Thing, Long> extractLongField(final FieldExpression sortField) {
        return (thing) -> {
            if (sortField instanceof AttributeExpressionImpl) {
                return thing.getAttributes()
                        .orElseThrow(IllegalStateException::new)
                        .getValue(((AttributeExpressionImpl) sortField).getKey())
                        .orElseThrow(IllegalStateException::new)
                        .asLong();
            } else if (sortField instanceof FeatureIdPropertyExpressionImpl) {
                return thing.getFeatures()
                        .orElseThrow(IllegalStateException::new)
                        .getFeature(((FeatureIdPropertyExpressionImpl) sortField).getFeatureId())
                        .orElseThrow(IllegalStateException::new)
                        .getProperty(((FeatureIdPropertyExpressionImpl) sortField).getProperty())
                        .orElseThrow(IllegalStateException::new)
                        .asLong();
            } else {
                throw new UnsupportedOperationException(sortField.getClass().getName() + " not supported");
            }
        };
    }

    private List<String> createExpectedResult(final Collection<Thing> things,
            final SortOption sortOption, final Comparator<Thing> ascendingComparator) {
        final Comparator<Thing> comparator;
        if (SortDirection.ASC == sortOption.getSortDirection()) {
            comparator = ascendingComparator;
        } else {
            comparator = ascendingComparator.reversed();
        }

        final List<String> simpleList = things.stream()
                .sorted(comparator)
                .map(thing -> thing.getId().orElseThrow(IllegalStateException::new))
                .collect(Collectors.toList());
        return new ResultListImpl<>(simpleList, ResultList.NO_NEXT_PAGE);
    }

    private static String randomThingId() {
        return "thingsearch.read:" + UUID.randomUUID().toString();
    }

}
