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
package org.eclipse.ditto.rql.query.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Unit test for {@link ThingPredicateVisitor}.
 */
public final class ThingPredicateVisitorTest {

    private static final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
            QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance());

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    private static final String KNOWN_PLACEHOLDER_VALUE = "LoreM";

    private static final PlaceholderResolver<String> PLACEHOLDER_RESOLVER = PlaceholderFactory.newPlaceholderResolver(
            new ThingPredicateTestPlaceholder(), KNOWN_PLACEHOLDER_VALUE);

    private static final QueryFilterCriteriaFactory queryFilterCriteriaFactoryWithPredicateResolver =
            QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance(), PLACEHOLDER_RESOLVER);

    private static final ThingId MATCHING_THING_ID = ThingId.of("org.eclipse.ditto", "foo-matching");
    private static final int MATCHING_THING_INTEGER = 42;
    private static final long MATCHING_THING_LONG = 42456489489489L;
    private static final double MATCHING_THING_DOUBLE = 22.26;
    private static final boolean MATCHING_THING_BOOLEAN = true;
    private static final String MATCHING_THING_STRING = "ccc_string";
    private static final Metadata MATCHING_THING_METADATA = Metadata.newBuilder()
            .set("/attributes/sensorType", "self-pushing")
            .set("/features/Car/properties/status", "running")
            .build();
    private static final Instant MATCHING_THING_MODIFIED = Instant.now();
    private static final Thing MATCHING_THING = Thing.newBuilder().setId(MATCHING_THING_ID)
            .setModified(MATCHING_THING_MODIFIED)
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
            .setMetadata(MATCHING_THING_METADATA)
            .build();

    private static final ThingId NON_MATCHING_THING_LESSER_ID =
            ThingId.of("org.eclipse.ditto", "foo-nonmatching-lesser");
    private static final int NON_MATCHING_THING_LESSER_INTEGER = MATCHING_THING_INTEGER / 2;
    private static final long NON_MATCHING_THING_LESSER_LONG = MATCHING_THING_LONG / 2;
    private static final double NON_MATCHING_THING_LESSER_DOUBLE = MATCHING_THING_DOUBLE / 2.0;
    private static final boolean NON_MATCHING_THING_LESSER_BOOLEAN = !MATCHING_THING_BOOLEAN;
    private static final String NON_MATCHING_THING_LESSER_STRING = "aaa_string";
    private static final Instant NON_MATCHING_THING_LESSER_MODIFIED = Instant.now().minus(1, ChronoUnit.MINUTES);
    private static final Thing NON_MATCHING_THING_LESSER = Thing.newBuilder().setId(NON_MATCHING_THING_LESSER_ID)
            .setModified(NON_MATCHING_THING_LESSER_MODIFIED)
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

    private static final ThingId NON_MATCHING_THING_GREATER_ID =
            ThingId.of("org.eclipse.ditto", "foo-nonmatching-greater");
    private static final int NON_MATCHING_THING_GREATER_INTEGER = MATCHING_THING_INTEGER * 2;
    private static final long NON_MATCHING_THING_GREATER_LONG = MATCHING_THING_LONG * 2;
    private static final double NON_MATCHING_THING_GREATER_DOUBLE = MATCHING_THING_DOUBLE * 2.0;
    private static final boolean NON_MATCHING_THING_GREATER_BOOLEAN = !MATCHING_THING_BOOLEAN;
    private static final String NON_MATCHING_THING_GREATER_STRING = "eee_string";
    private static final Instant NON_MATCHING_THING_GREATER_MODIFIED = Instant.now().plus(1, ChronoUnit.MINUTES);
    private static final Thing NON_MATCHING_THING_GREATER = Thing.newBuilder().setId(NON_MATCHING_THING_GREATER_ID)
            .setModified(NON_MATCHING_THING_GREATER_MODIFIED)
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

    private static Predicate<Thing> createPredicate(final String filter) {
        return ThingPredicateVisitor.apply(queryFilterCriteriaFactory.filterCriteria(filter, DittoHeaders.empty()),
                PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()));
    }

    private static Predicate<Thing> createPredicateWithPlaceholderResolver(final String filter) {
        return ThingPredicateVisitor.apply(
                queryFilterCriteriaFactoryWithPredicateResolver.filterCriteria(filter, DittoHeaders.empty()),
                PLACEHOLDER_RESOLVER);
    }

    private static void testPredicate(final Thing nonMatchingThing,
            final String predicate,
            final String target,
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

    private static void testPredicate(final Thing nonMatchingThing,
            final String predicate,
            final String target,
            final String value,
            final boolean expected,
            final boolean escapeStr) {

        final String stringValue = escapeStr ? "\"" + value + "\"" : value;
        final String filter = predicate + "(" + target + "," + stringValue + ")";
        final Predicate<Thing> thingPredicate = createPredicate(filter);

        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '%s' with value '%s' should be %s", filter, stringValue, expected)
                .isEqualTo(expected);

        if (nonMatchingThing != null) {
            assertThat(thingPredicate.test(nonMatchingThing))
                    .as("Filtering '%s' with value '%s' should be %s", filter, stringValue, false)
                    .isFalse();
        }
    }

    private static void testPredicateWithPlaceholder(final String predicate,
            final String placeholder,
            final String value,
            final boolean expected,
            final boolean escapeStr) {

        final String stringValue = escapeStr ? "\"" + value + "\"" : value;
        final String filter = predicate + "(" + placeholder + "," + stringValue + ")";
        final Predicate<Thing> thingPredicate = createPredicateWithPlaceholderResolver(filter);

        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '%s' with value '%s' should be %s", filter, stringValue, expected)
                .isEqualTo(expected);
    }

    @Test
    public void testFilterThingIdWithStringEq() {
        testPredicate(NON_MATCHING_THING_LESSER, "eq", "thingId", MATCHING_THING_ID.toString());
    }

    @Test
    public void testFilterNamespaceWithStringEq() {
        testPredicate(null, "eq", "_namespace", "org.eclipse.ditto");
    }

    @Test
    public void testFilterThingIdWithStringNe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ne", "thingId", NON_MATCHING_THING_LESSER_ID.toString());
    }

    @Test
    public void testFilterAttributeWithIntegerEq() {
        testPredicate(NON_MATCHING_THING_LESSER, "eq", "attributes/anInteger", MATCHING_THING_INTEGER);
    }

    @Test
    public void testFilterAttributeWithIntegerNe() {
        testPredicate(NON_MATCHING_THING_LESSER, "ne", "attributes/anInteger",
                NON_MATCHING_THING_LESSER_INTEGER);
    }

    @Test
    public void testFilterPropertyWithIntegerEq() {
        testPredicate(NON_MATCHING_THING_LESSER, "eq", "features/foo/properties/anInteger",
                MATCHING_THING_INTEGER);
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
    public void testFilterModifiedWithTimePlaceholderGt() {
        testPredicate(NON_MATCHING_THING_LESSER, "gt", "_modified", "time:now", false, false);
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
    public void testFilterModifiedWithTimePlaceholderLt() {
        testPredicate(NON_MATCHING_THING_GREATER, "lt", "_modified", "time:now", true, false);
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
        testPredicate(null, "like", "thingId", MATCHING_THING_ID.toString().replace('r', '?'));
    }

    @Test
    public void testFilterMetadataWithStringEq() {
        testPredicate(null, "eq", "_metadata/attributes/sensorType", "self-pushing");
    }

    @Test
    public void testFilterMetadataExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(_metadata/attributes)");
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(_metadata/attributes)' should be true")
                .isTrue();
    }

    @Test
    public void testFilterThingIdExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(thingId)");
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(thingId)' should be true")
                .isTrue();
    }

    @Test
    public void testFilterAttributeExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(attributes/aBoolean)");
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(attributes/aBoolean)' should be true")
                .isTrue();

        final Predicate<Thing> negativePredicate = createPredicate("exists(attributes/missing)");
        assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering 'exists(attributes/missing)' should be false")
                .isFalse();
    }

    @Test
    public void testFilterFeatureExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(features/foo)");
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/foo)' should be true")
                .isTrue();

        final Predicate<Thing> negativePredicate = createPredicate("exists(features/bar)");
        assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/bar)' should be false")
                .isFalse();
    }

    @Test
    public void testFilterFeaturePropertyExists() {
        final Predicate<Thing> thingPredicate = createPredicate("exists(features/foo/properties/aString)");
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/foo/properties/aString)' should be true")
                .isTrue();

        final Predicate<Thing> negativePredicate = createPredicate("exists(features/foo/properties/missing)");
        assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering 'exists(features/foo/properties/missing)' should be false")
                .isFalse();
    }

    @Test
    public void testLogicalAndWith2queries() {
        final String filter = "and(exists(attributes/aBoolean),eq(thingId,\"" + MATCHING_THING_ID + "\"))";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isTrue();
    }

    @Test
    public void testLogicalAndWith3queries() {
        final String filter = "and(" +
                "exists(attributes/aBoolean)," +
                "eq(thingId,\"" + MATCHING_THING_ID + "\")," +
                "gt(attributes/aDouble," + (MATCHING_THING_DOUBLE-0.5) + ")" +
                ")";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isTrue();
    }

    @Test
    public void testLogicalOrWith2queries() {
        final String filter = "or(exists(attributes/missing),eq(thingId,\"" + MATCHING_THING_ID + "\"))";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isTrue();
    }

    @Test
    public void testLogicalOrWith3queries() {
        final String filter = "or(" +
                "exists(attributes/missing)," +
                "eq(thingId,\"" + MATCHING_THING_ID + "-foo" + "\")," +
                "gt(attributes/aDouble," + (MATCHING_THING_DOUBLE-0.5) + ")" +
                ")";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isTrue();
    }

    @Test
    public void testLogicalNotWithEqThingId() {
        final String filter = "not(eq(thingId,\"" + MATCHING_THING_ID + "\"))";
        final Predicate<Thing> thingPredicate = createPredicate(filter);
        assertThat(thingPredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be false")
                .isFalse();

        final String negativeFilter = "not(eq(thingId,\"" + MATCHING_THING_ID + "-missing" + "\"))";
        final Predicate<Thing> negativePredicate = createPredicate(negativeFilter);
        assertThat(negativePredicate.test(MATCHING_THING))
                .as("Filtering '"+ filter + "' should be true")
                .isTrue();
    }

    @Test
    public void testKnownPlaceholderExists() {
        final Predicate<Thing> thingPredicate = createPredicateWithPlaceholderResolver("exists(test:lower)");
        assertThat(thingPredicate.test(Thing.newBuilder().build()))
                .as("Filtering 'exists(test:lower)' should be true")
                .isTrue();
    }

    @Test
    public void testUnknownPlaceholderLeadsToInvalidRqlExpression() {
        assertThatThrownBy(() -> createPredicateWithPlaceholderResolver("exists(test:unknown)"))
                .as("Creating predicate 'exists(test:unknown)' should fail with an invalid RQL expression exception")
                .isInstanceOf(InvalidRqlExpressionException.class);
    }

    @Test
    public void testFilterWithPlaceholderStringEq() {
        testPredicateWithPlaceholder( "eq", "test:lower", KNOWN_PLACEHOLDER_VALUE.toLowerCase(), true, true);
    }

    @Test
    public void testFilterWithPlaceholderStringNotEq() {
        testPredicateWithPlaceholder( "eq", "test:lower", KNOWN_PLACEHOLDER_VALUE, false, true);
    }

    @Test
    public void testFilterWithPlaceholderStringNe() {
        testPredicateWithPlaceholder( "ne", "test:upper", KNOWN_PLACEHOLDER_VALUE.toLowerCase(), true, true);
    }

    @Test
    public void testFilterWithPlaceholderStringNotNe() {
        testPredicateWithPlaceholder( "ne", "test:upper", KNOWN_PLACEHOLDER_VALUE.toUpperCase(), false, true);
    }

    @Test
    public void testFilterWithPlaceholderStringIn() {
        testPredicateWithPlaceholder("in", "test:lower",
                "\"" + KNOWN_PLACEHOLDER_VALUE.toLowerCase() + "\"," + "\"foo\"", true, false);
        testPredicateWithPlaceholder("in", "test:lower",
                "\"" + KNOWN_PLACEHOLDER_VALUE.toUpperCase() + "\"," + "\"foo\"", false, false);
    }

    @Test
    public void testFilterWithPlaceholderStringLike() {
        testPredicateWithPlaceholder("like", "test:upper", "LO*", true, true);
        testPredicateWithPlaceholder("like", "test:upper", "l*", false, true);
        testPredicateWithPlaceholder("like", "test:lower", "*rem", true, true);
    }

}
