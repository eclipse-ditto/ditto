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
package org.eclipse.ditto.model.query.things;

import java.util.function.Predicate;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Thing;
import org.junit.Test;

/**
 * Tests {@link ThingPredicateVisitor}.
 */
public class ThingPredicateVisitorTest {

    private static final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
    private static final ThingsFieldExpressionFactory fieldExpressionFactory =
            new ModelBasedThingsFieldExpressionFactory();
    private static final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
            new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);

    private static final String MATCHING_THING_ID = "org.eclipse.ditto:foo-matching";
    private static final int MATCHING_THING_INTEGER = 42;
    private static final long MATCHING_THING_LONG = 42456489489489L;
    private static final double MATCHING_THING_DOUBLE = 22.26;
    private static final boolean MATCHING_THING_BOOLEAN = true;
    private static final String MATCHING_THING_STRING = "ccc_string";
    private static final Thing MATCHING_THING = Thing.newBuilder().setId(MATCHING_THING_ID)
            .setAttribute(JsonPointer.of("anInteger"), JsonValue.of(MATCHING_THING_INTEGER))
            .setAttribute(JsonPointer.of("aLong"), JsonValue.of(MATCHING_THING_LONG))
            .setAttribute(JsonPointer.of("aDouble"), JsonValue.of(MATCHING_THING_DOUBLE))
            .setAttribute(JsonPointer.of("aBoolean"), JsonValue.of(MATCHING_THING_BOOLEAN))
            .setAttribute(JsonPointer.of("aString"), JsonValue.of(MATCHING_THING_STRING))
            .setFeature("foo", FeatureProperties.newBuilder()
                    .set(JsonPointer.of("anInteger"), JsonValue.of(MATCHING_THING_INTEGER))
                    .set(JsonPointer.of("aLong"), JsonValue.of(MATCHING_THING_LONG))
                    .set(JsonPointer.of("aDouble"), JsonValue.of(MATCHING_THING_DOUBLE))
                    .set(JsonPointer.of("aBoolean"), JsonValue.of(MATCHING_THING_BOOLEAN))
                    .set(JsonPointer.of("aString"), JsonValue.of(MATCHING_THING_STRING))
                    .build()
            )
            .build();

    private static final String NON_MATCHING_THING_LESSER_ID = "org.eclipse.ditto:foo-nonmatching-lesser";
    private static final int NON_MATCHING_THING_LESSER_INTEGER = MATCHING_THING_INTEGER / 2;
    private static final long NON_MATCHING_THING_LESSER_LONG = MATCHING_THING_LONG / 2;
    private static final double NON_MATCHING_THING_LESSER_DOUBLE = MATCHING_THING_DOUBLE / 2.0;
    private static final boolean NON_MATCHING_THING_LESSER_BOOLEAN = !MATCHING_THING_BOOLEAN;
    private static final String NON_MATCHING_THING_LESSER_STRING = "aaa_string";
    private static final Thing NON_MATCHING_THING_LESSER = Thing.newBuilder().setId(NON_MATCHING_THING_LESSER_ID)
            .setAttribute(JsonPointer.of("anInteger"), JsonValue.of(NON_MATCHING_THING_LESSER_INTEGER))
            .setAttribute(JsonPointer.of("aLong"), JsonValue.of(NON_MATCHING_THING_LESSER_LONG))
            .setAttribute(JsonPointer.of("aDouble"), JsonValue.of(NON_MATCHING_THING_LESSER_DOUBLE))
            .setAttribute(JsonPointer.of("aBoolean"), JsonValue.of(NON_MATCHING_THING_LESSER_BOOLEAN))
            .setAttribute(JsonPointer.of("aString"), JsonValue.of(NON_MATCHING_THING_LESSER_STRING))
            .setFeature("foo", FeatureProperties.newBuilder()
                    .set(JsonPointer.of("anInteger"), JsonValue.of(NON_MATCHING_THING_LESSER_INTEGER))
                    .set(JsonPointer.of("aLong"), JsonValue.of(NON_MATCHING_THING_LESSER_LONG))
                    .set(JsonPointer.of("aDouble"), JsonValue.of(NON_MATCHING_THING_LESSER_DOUBLE))
                    .set(JsonPointer.of("aBoolean"), JsonValue.of(NON_MATCHING_THING_LESSER_BOOLEAN))
                    .set(JsonPointer.of("aString"), JsonValue.of(NON_MATCHING_THING_LESSER_STRING))
                    .build()
            )
            .build();

    private static final String NON_MATCHING_THING_GREATER_ID = "org.eclipse.ditto:foo-nonmatching-greater";
    private static final int NON_MATCHING_THING_GREATER_INTEGER = MATCHING_THING_INTEGER * 2;
    private static final long NON_MATCHING_THING_GREATER_LONG = MATCHING_THING_LONG * 2;
    private static final double NON_MATCHING_THING_GREATER_DOUBLE = MATCHING_THING_DOUBLE * 2.0;
    private static final boolean NON_MATCHING_THING_GREATER_BOOLEAN = !MATCHING_THING_BOOLEAN;
    private static final String NON_MATCHING_THING_GREATER_STRING = "eee_string";
    private static final Thing NON_MATCHING_THING_GREATER = Thing.newBuilder().setId(NON_MATCHING_THING_GREATER_ID)
            .setAttribute(JsonPointer.of("anInteger"), JsonValue.of(NON_MATCHING_THING_GREATER_INTEGER))
            .setAttribute(JsonPointer.of("aLong"), JsonValue.of(NON_MATCHING_THING_GREATER_LONG))
            .setAttribute(JsonPointer.of("aDouble"), JsonValue.of(NON_MATCHING_THING_GREATER_DOUBLE))
            .setAttribute(JsonPointer.of("aBoolean"), JsonValue.of(NON_MATCHING_THING_GREATER_BOOLEAN))
            .setAttribute(JsonPointer.of("aString"), JsonValue.of(NON_MATCHING_THING_GREATER_STRING))
            .setFeature("foo", FeatureProperties.newBuilder()
                    .set(JsonPointer.of("anInteger"), JsonValue.of(NON_MATCHING_THING_GREATER_INTEGER))
                    .set(JsonPointer.of("aLong"), JsonValue.of(NON_MATCHING_THING_GREATER_LONG))
                    .set(JsonPointer.of("aDouble"), JsonValue.of(NON_MATCHING_THING_GREATER_DOUBLE))
                    .set(JsonPointer.of("aBoolean"), JsonValue.of(NON_MATCHING_THING_GREATER_BOOLEAN))
                    .set(JsonPointer.of("aString"), JsonValue.of(NON_MATCHING_THING_GREATER_STRING))
                    .build()
            )
            .build();

    private static Criteria createCriteria(final String filter) {
        return queryFilterCriteriaFactory.filterCriteria(filter, DittoHeaders.empty());
    }

    private static Predicate<Thing> createPredicate(final String filter) {
        return ThingPredicateVisitor.apply(createCriteria(filter));
    }

    private static void testPredicate(final Thing nonMatchingThing, final String predicate, final String target,
            final Object value) {
        testPredicate(nonMatchingThing, predicate, target, value.toString(), true, false);
    }

    private static void testPredicate(final Thing nonMatchingThing, final String predicate, final String target,
            final String value) {
        testPredicate(nonMatchingThing, predicate, target, value, true, true);
    }

    private static void testPredicateNeg(final Thing nonMatchingThing, final String predicate, final String target,
            final Object value) {
        testPredicate(nonMatchingThing, predicate, target, value.toString(), false, false);
    }

    private static void testPredicateNeg(final Thing nonMatchingThing, final String predicate, final String target,
            final String value) {
        testPredicate(nonMatchingThing, predicate, target, value, false, true);
    }

    private static void testPredicate(final Thing nonMatchingThing, final String predicate, final String target,
            final String value, final boolean expected, final boolean escapeStr) {
        final String stringValue = escapeStr ? "\"" + value + "\"" : value;
        final String filter = predicate + "(" + target + "," + stringValue + ")";
        final Predicate<Thing> thingPredicate = createPredicate(filter);

        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '" + filter + "' with value '" + stringValue + "' should be " +
                        expected)
                .isEqualTo(expected);

        if (nonMatchingThing != null) {
            Assertions.assertThat(thingPredicate.test(nonMatchingThing))
                    .as("Filtering '" + filter + "' with value '" + stringValue + "' should be " + false)
                    .isFalse();
        }
    }

    @Test
    public void testFilterThingIdWithStringEq() {
        testPredicate(NON_MATCHING_THING_LESSER, "eq", "thingId", MATCHING_THING_ID);
    }

    @Test
    public void testFilterNamespaceWithStringEq() {
        testPredicate(null, "eq", "_namespace", "org.eclipse.ditto");
    }

    @Test
    public void testFilterThingIdWithStringNe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ne", "thingId", NON_MATCHING_THING_LESSER_ID);
    }

    @Test
    public void testFilterAttributeWithIntegerEq() {
        testPredicate(NON_MATCHING_THING_LESSER, "eq", "attributes/anInteger", MATCHING_THING_INTEGER);
    }

    @Test
    public void testFilterAttributeWithIntegerNe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ne", "attributes/anInteger", NON_MATCHING_THING_LESSER_INTEGER);
    }

    @Test
    public void testFilterPropertyWithIntegerEq() {
        testPredicate(NON_MATCHING_THING_LESSER, "eq", "features/foo/properties/anInteger", MATCHING_THING_INTEGER);
    }

    @Test
    public void testFilterPropertyWithIntegerNe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ne", "features/foo/properties/anInteger",
                NON_MATCHING_THING_LESSER_INTEGER);
    }

    @Test
    public void testFilterAttributeWithIntegerGe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/anInteger", MATCHING_THING_INTEGER - 1);
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/anInteger", MATCHING_THING_INTEGER); // corner case
    }

    @Test
    public void testFilterAttributeWithIntegerGeFloatType() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/anInteger", MATCHING_THING_INTEGER * 0.9);
    }

    @Test
    public void testFilterAttributeWithIntegerGeDoubleType() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/anInteger", MATCHING_THING_INTEGER * 0.9d);
    }

    @Test
    public void testFilterAttributeWithIntegerGeStringType() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/anInteger", String.valueOf(MATCHING_THING_INTEGER-1));
    }

    @Test
    public void testFilterAttributeWithLongGe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aLong", MATCHING_THING_LONG - 1);
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aLong", MATCHING_THING_LONG); // corner case
    }

    @Test
    public void testFilterAttributeWithDoubleGe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aDouble", MATCHING_THING_DOUBLE - 1.0);
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aDouble", MATCHING_THING_DOUBLE); // corner case
    }

    @Test
    public void testFilterAttributeWithDoubleGeFloatType() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aDouble", (float) (MATCHING_THING_DOUBLE - 1.0));
    }

    @Test
    public void testFilterAttributeWithDoubleGeIntegerType() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aDouble", (int) MATCHING_THING_DOUBLE);
    }

    @Test
    public void testFilterAttributeWithDoubleGeStringType() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aDouble", String.valueOf(MATCHING_THING_DOUBLE-1.0));
    }

    @Test
    public void testFilterAttributeWithStringGe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aString", "b");
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aString", MATCHING_THING_STRING); // corner case
    }

    @Test
    public void testFilterAttributeWithBooleanGe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ge", "attributes/aBoolean", true); // corner case
    }

    @Test
    public void testFilterAttributeWithIntegerGt() {
        testPredicate(NON_MATCHING_THING_LESSER, "gt", "attributes/anInteger", MATCHING_THING_INTEGER - 1);
        testPredicateNeg(NON_MATCHING_THING_LESSER, "gt", "attributes/anInteger",
                MATCHING_THING_INTEGER); // corner case
    }

    @Test
    public void testFilterAttributeWithLongGt() {
        testPredicate(NON_MATCHING_THING_LESSER, "gt", "attributes/aLong", MATCHING_THING_LONG - 1);
        testPredicateNeg(NON_MATCHING_THING_LESSER, "gt", "attributes/aLong", MATCHING_THING_LONG); // corner case
    }

    @Test
    public void testFilterAttributeWithDoubleGt() {
        testPredicate(NON_MATCHING_THING_LESSER, "gt", "attributes/aDouble", MATCHING_THING_DOUBLE - 1.0);
        testPredicateNeg(NON_MATCHING_THING_LESSER, "gt", "attributes/aDouble", MATCHING_THING_DOUBLE); // corner case
    }

    @Test
    public void testFilterAttributeWithStringGt() {
        testPredicate(NON_MATCHING_THING_LESSER, "gt", "attributes/aString", "b");
        testPredicateNeg(NON_MATCHING_THING_LESSER, "gt", "attributes/aString", MATCHING_THING_STRING); // corner case
    }

    @Test
    public void testFilterAttributeWithBooleanGt() {
        testPredicateNeg(NON_MATCHING_THING_LESSER, "gt", "attributes/aBoolean", true); // corner case
    }

    @Test
    public void testFilterAttributeWithIntegerLe() {
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/anInteger", MATCHING_THING_INTEGER + 1);
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/anInteger", MATCHING_THING_INTEGER); // corner case
    }

    @Test
    public void testFilterAttributeWithLongLe() {
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/aLong", MATCHING_THING_LONG + 1);
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/aLong", MATCHING_THING_LONG); // corner case
    }

    @Test
    public void testFilterAttributeWithDoubleLe() {
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/aDouble", MATCHING_THING_DOUBLE + 1.0);
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/aDouble", MATCHING_THING_DOUBLE); // corner case
    }

    @Test
    public void testFilterAttributeWithStringLe() {
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/aString", "d");
        testPredicate(NON_MATCHING_THING_GREATER, "le", "attributes/aString", MATCHING_THING_STRING); // corner case
    }

    @Test
    public void testFilterAttributeWithIntegerLt() {
        testPredicate(NON_MATCHING_THING_GREATER, "lt", "attributes/anInteger", MATCHING_THING_INTEGER + 1);
        testPredicateNeg(NON_MATCHING_THING_GREATER, "lt", "attributes/anInteger",
                MATCHING_THING_INTEGER); // corner case
    }

    @Test
    public void testFilterAttributeWithLongLt() {
        testPredicate(NON_MATCHING_THING_GREATER, "lt", "attributes/aLong", MATCHING_THING_LONG + 1);
        testPredicateNeg(NON_MATCHING_THING_GREATER, "lt", "attributes/aLong", MATCHING_THING_LONG); // corner case
    }

    @Test
    public void testFilterAttributeWithDoubleLt() {
        testPredicate(NON_MATCHING_THING_GREATER, "lt", "attributes/aDouble", MATCHING_THING_DOUBLE + 1.0);
        testPredicateNeg(NON_MATCHING_THING_GREATER, "lt", "attributes/aDouble", MATCHING_THING_DOUBLE); // corner case
    }

    @Test
    public void testFilterAttributeWithStringLt() {
        testPredicate(NON_MATCHING_THING_GREATER, "lt", "attributes/aString", "d");
        testPredicateNeg(NON_MATCHING_THING_GREATER, "lt", "attributes/aString", MATCHING_THING_STRING); // corner case
    }

    @Test
    public void testFilterThingIdWithStringIn() {
        testPredicate(NON_MATCHING_THING_GREATER, "in", "thingId",
                "\"" + MATCHING_THING_ID + "\"," + "\"" + NON_MATCHING_THING_LESSER_ID + "\"", true, false);
        testPredicate(NON_MATCHING_THING_GREATER, "in", "thingId",
                "\"org.eclipse.ditto:unknown\"", false, false);
    }

    @Test
    public void testFilterAttributeWithIntegerIn() {
        testPredicate(NON_MATCHING_THING_GREATER, "in", "attributes/anInteger",
                "1,2,3," + MATCHING_THING_INTEGER, true, false);
        testPredicate(NON_MATCHING_THING_GREATER, "in", "attributes/anInteger",
                "1,2,3,4,5", false, false);
    }

    @Test
    public void testFilterAttributeWithDoubleIn() {
        testPredicate(NON_MATCHING_THING_GREATER, "in", "attributes/aDouble",
                "1.0,2.0,3.0," + MATCHING_THING_DOUBLE, true, false);
        testPredicate(NON_MATCHING_THING_GREATER, "in", "attributes/aDouble",
                "1.0,2.0,3.0", false, false);
    }

    @Test
    public void testFilterThingIdWithStringLike() {
        testPredicate(null, "like", "thingId", "org.eclipse.ditto*");
        testPredicate(null, "like", "thingId", "*matching*");
        testPredicate(null, "like", "thingId", MATCHING_THING_ID.replace('r', '?'));
    }

    @Test
    public void testFilterThingIdExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(thingId)");
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(thingId)' should be true")
                .isEqualTo(true);
    }

    @Test
    public void testFilterAttributeExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(attributes/aBoolean)");
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(attributes/aBoolean)' should be true")
                .isEqualTo(true);

        final Predicate<Thing> negativePredicate = createPredicate("exists(attributes/missing)");
        Assertions.assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering 'exists(attributes/missing)' should be false")
                .isEqualTo(false);
    }

    @Test
    public void testFilterFeatureExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(features/foo)");
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/foo)' should be true")
                .isEqualTo(true);

        final Predicate<Thing> negativePredicate = createPredicate("exists(features/bar)");
        Assertions.assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/bar)' should be false")
                .isEqualTo(false);
    }

    @Test
    public void testFilterFeaturePropertyExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(features/foo/properties/aString)");
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/foo/properties/aString)' should be true")
                .isEqualTo(true);

        final Predicate<Thing> negativePredicate = createPredicate("exists(features/foo/properties/missing)");
        Assertions.assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/foo/properties/missing)' should be false")
                .isEqualTo(false);
    }

    @Test
    public void testLogicalAndWith2queries() {
        final String filter = "and(exists(attributes/aBoolean),eq(thingId,\"" + MATCHING_THING_ID + "\"))";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isEqualTo(true);
    }

    @Test
    public void testLogicalAndWith3queries() {
        final String filter = "and(" +
                "exists(attributes/aBoolean)," +
                "eq(thingId,\"" + MATCHING_THING_ID + "\")," +
                "gt(attributes/aDouble," + (MATCHING_THING_DOUBLE-0.5) + ")" +
                ")";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isEqualTo(true);
    }

    @Test
    public void testLogicalOrWith2queries() {
        final String filter = "or(exists(attributes/missing),eq(thingId,\"" + MATCHING_THING_ID + "\"))";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isEqualTo(true);
    }

    @Test
    public void testLogicalOrWith3queries() {
        final String filter = "or(" +
                "exists(attributes/missing)," +
                "eq(thingId,\"" + MATCHING_THING_ID + "-foo" + "\")," +
                "gt(attributes/aDouble," + (MATCHING_THING_DOUBLE-0.5) + ")" +
                ")";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isEqualTo(true);
    }

    @Test
    public void testLogicalNotWithEqThingId() {
        final String filter = "not(eq(thingId,\"" + MATCHING_THING_ID + "\"))";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        Assertions.assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be false")
                .isEqualTo(false);

        final String negativeFilter = "not(eq(thingId,\"" + MATCHING_THING_ID + "-missing" + "\"))";
        final Predicate<Thing> negativePredicate = createPredicate(negativeFilter);
        Assertions.assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isEqualTo(true);
    }

}
