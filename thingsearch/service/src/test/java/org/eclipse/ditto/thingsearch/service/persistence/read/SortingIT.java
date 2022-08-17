/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.AttributeExpression;
import org.eclipse.ditto.rql.query.expression.FeatureIdDesiredPropertyExpression;
import org.eclipse.ditto.rql.query.expression.FeatureIdPropertyExpression;
import org.eclipse.ditto.rql.query.expression.FieldExpression;
import org.eclipse.ditto.rql.query.expression.SimpleFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.ResultListImpl;
import org.eclipse.ditto.thingsearch.service.persistence.AbstractThingSearchPersistenceITBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for sorting functionality of search persistence.
 */
@RunWith(Parameterized.class)
public final class SortingIT extends AbstractReadPersistenceITBase {

    private static final List<SortDirection> SORT_DIRECTIONS = Arrays.asList(SortDirection.values());
    private static final Random RANDOM = new Random();

    private static final ThingsFieldExpressionFactory EFT = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

    public static final String NAMESPACE = "thingsearch.read";
    private static final List<ThingId> THING_IDS = Arrays.asList(
            ThingId.of(NAMESPACE, "thingId1a"),
            ThingId.of(NAMESPACE, "thingId1b"),
            ThingId.of(NAMESPACE, "thingId1c"));
    private static final List<String> ATTRIBUTE_SORT_STRING_VALUES =
            Arrays.asList("valA", "valB", "valC", "valD", "valE");
    private static final List<Long> ATTRIBUTE_SORT_LONG_VALUES = Arrays.asList(1L, 2L, 3L, 4L, 5L);
    private static final String ATTRIBUTE_SORT_KEY = "myAttr1";
    private static final String ATTRIBUTE_SORT_KEY_WITH_DOTS = "$myAttr.with.dots";
    private static final String FEATURE_ID_WITH_DOTS = "~myFeatureId.with.dots";
    private static final String PROPERTY_SORT_KEY_WITH_DOTS = "myProperty.with.dots";
    private static final String DESIRED_PROPERTY_SORT_KEY_WITH_DOTS = "myDesiredProperty.with.dots";

    @Parameterized.Parameters(name = "direction={0}")
    public static List<Object> parameters() {
        return Arrays.asList(SORT_DIRECTIONS.toArray());
    }

    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    @Parameterized.Parameter(0)
    public SortDirection testedSortDirection;

    @Test
    public void sortPerDefaultByThingIdAsc() {
        final SortOption DEFAULT_SORT_OPTION = new SortOption(EFT.sortByThingId(), SortDirection.ASC);
        final List<Thing> things = createAndPersistThings(THING_IDS, this::createThing);

        // find without any ordering
        final Query query = AbstractThingSearchPersistenceITBase.qbf.newBuilder(cf.any()).build();
        final ResultList<ThingId> result = findAll(query);

        final Comparator<Thing> ascendingComparator =
                Comparator.comparing(extractStringField(DEFAULT_SORT_OPTION.getSortExpression()));

        final List<ThingId> expectedResult = createExpectedResult(things, DEFAULT_SORT_OPTION, ascendingComparator);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void sortByThingId() {
        runTestWithStringValues(
                new SortOption(EFT.sortByThingId(), testedSortDirection), this::createThing,
                THING_IDS.stream().map(String::valueOf).collect(Collectors.toList()));
    }

    @Test
    public void sortByStringAttribute() {
        runTestWithStringValues(
                new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), testedSortDirection),
                getStringAttributesThingBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES);
    }

    @Test
    public void sortByStringAttributeWithDots() {
        runTestWithStringValues(
                new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY_WITH_DOTS), testedSortDirection),
                getStringAttributesWithDotsThingBuilder(),
                ATTRIBUTE_SORT_STRING_VALUES);
    }

    @Test
    public void sortByLongAttribute() {
        runTestWithLongValues(
                new SortOption(EFT.sortByAttribute(ATTRIBUTE_SORT_KEY), testedSortDirection),
                getLongAttributesThingBuilder()
        );
    }

    @Test
    public void sortByLongPropertyWithDots() {
        runTestWithLongValues(
                new SortOption(
                        EFT.sortByFeatureProperty(FEATURE_ID_WITH_DOTS, PROPERTY_SORT_KEY_WITH_DOTS),
                        testedSortDirection),
                getLongPropertyWithDotsThingBuilder()
        );
    }

    @Test
    public void sortByLongDesiredPropertyWithDots() {
        runTestWithLongValues(
                new SortOption(
                        EFT.sortByFeatureDesiredProperty(FEATURE_ID_WITH_DOTS, DESIRED_PROPERTY_SORT_KEY_WITH_DOTS),
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

        final List<ThingId> expectedResult = createExpectedResult(things, sortOption, ascendingComparator);

        final Query query = AbstractThingSearchPersistenceITBase.qbf.newBuilder(cf.any())
                .sort(Collections.singletonList(sortOption)).build();
        final ResultList<ThingId> result = findAll(query);

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
                            .desiredProperties(
                                    FeatureProperties.newBuilder()
                                            .set(DESIRED_PROPERTY_SORT_KEY_WITH_DOTS, value)
                                            .build()
                            )
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
                .collect(Collectors.toList()); // do not replace with .toList()
        // shuffle the documents for more realistic testing
        Collections.shuffle(things);
        things.forEach(this::persistThing);
        return things;
    }


    private Function<Thing, String> extractStringField(final FieldExpression sortField) {
        return (thing) -> {
            if (sortField instanceof SimpleFieldExpression) {
                return thing.getEntityId().orElseThrow(IllegalStateException::new).toString();
            } else {
                return thing.getAttributes()
                        .orElseThrow(IllegalStateException::new)
                        .getValue(((AttributeExpression) sortField).getKey())
                        .orElseThrow(IllegalStateException::new)
                        .asString();
            }
        };
    }

    private Function<Thing, Long> extractLongField(final FieldExpression sortField) {
        return (thing) -> {
            if (sortField instanceof AttributeExpression) {
                return thing.getAttributes()
                        .orElseThrow(IllegalStateException::new)
                        .getValue(((AttributeExpression) sortField).getKey())
                        .orElseThrow(IllegalStateException::new)
                        .asLong();
            } else if (sortField instanceof FeatureIdPropertyExpression) {
                return thing.getFeatures()
                        .orElseThrow(IllegalStateException::new)
                        .getFeature(((FeatureIdPropertyExpression) sortField).getFeatureId())
                        .orElseThrow(IllegalStateException::new)
                        .getProperty(((FeatureIdPropertyExpression) sortField).getProperty())
                        .orElseThrow(IllegalStateException::new)
                        .asLong();
            } else if (sortField instanceof FeatureIdDesiredPropertyExpression) {
                return thing.getFeatures()
                        .orElseThrow(IllegalStateException::new)
                        .getFeature(((FeatureIdDesiredPropertyExpression) sortField).getFeatureId())
                        .orElseThrow(IllegalStateException::new)
                        .getDesiredProperty(((FeatureIdDesiredPropertyExpression) sortField).getDesiredProperty())
                        .orElseThrow(IllegalStateException::new)
                        .asLong();
            } else {
                throw new UnsupportedOperationException(sortField.getClass().getName() + " not supported");
            }
        };
    }

    private List<ThingId> createExpectedResult(final Collection<Thing> things,
            final SortOption sortOption, final Comparator<Thing> ascendingComparator) {
        final Comparator<Thing> comparator;
        if (SortDirection.ASC == sortOption.getSortDirection()) {
            comparator = ascendingComparator;
        } else {
            comparator = ascendingComparator.reversed();
        }

        final List<ThingId> simpleList = things.stream()
                .sorted(comparator)
                .map(thing -> thing.getEntityId().orElseThrow(IllegalStateException::new))
                .toList();

        return new ResultListImpl<>(simpleList, ResultList.NO_NEXT_PAGE);
    }

    private static ThingId randomThingId() {
        return ThingId.of(NAMESPACE, UUID.randomUUID().toString());
    }

}
