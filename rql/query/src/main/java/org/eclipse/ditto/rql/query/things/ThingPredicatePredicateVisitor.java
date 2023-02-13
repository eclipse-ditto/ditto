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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.Expression;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.model.ParsedPlaceholder;
import org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor;
import org.eclipse.ditto.things.model.Thing;

/**
 * A Java {@link Predicate} based PredicateVisitor for evaluating whether {@link Thing}s or additionally added
 * {@code PlaceholderResolver}s matches a given filter.
 */
public final class ThingPredicatePredicateVisitor implements PredicateVisitor<Function<String, Predicate<Thing>>> {

    private static final Object NULL_LITERAL = new Object();

    private static ThingPredicatePredicateVisitor instance;

    private final List<PlaceholderResolver<?>> additionalPlaceholderResolvers;

    private ThingPredicatePredicateVisitor(final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        this.additionalPlaceholderResolvers = Collections.unmodifiableList(
                new ArrayList<>(additionalPlaceholderResolvers));
    }

    /**
     * Gets the singleton instance of this {@code ThingPredicatePredicateVisitor}.
     *
     * @return the singleton instance.
     */
    public static ThingPredicatePredicateVisitor getInstance() {
        if (null == instance) {
            instance = new ThingPredicatePredicateVisitor(Collections.emptyList());
        }
        return instance;
    }

    /**
     * Creates a new instance of {@code ThingPredicatePredicateVisitor} with additional custom placeholder resolvers.
     *
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL predicates.
     * @return the created instance.
     * @since 2.2.0
     */
    public static ThingPredicatePredicateVisitor createInstance(
            final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        return new ThingPredicatePredicateVisitor(additionalPlaceholderResolvers);
    }

    /**
     * Creates a new instance of {@code ThingPredicatePredicateVisitor} with additional custom placeholder resolvers.
     *
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL predicates.
     * @return the created instance.
     * @since 2.2.0
     */
    public static ThingPredicatePredicateVisitor createInstance(
            final PlaceholderResolver<?>... additionalPlaceholderResolvers) {
        return createInstance(Arrays.asList(additionalPlaceholderResolvers));
    }

    /**
     * Creates a Java {@link Predicate} from a Ditto query
     * {@link org.eclipse.ditto.rql.query.criteria.Predicate Predicate} and its field name.
     *
     * @param predicate The Ditto Predicate to generate the Predicate from.
     * @param fieldName Name of the field where the predicate is applied to.
     * @return The created Predicate.
     */
    public static Predicate<Thing> apply(final org.eclipse.ditto.rql.query.criteria.Predicate predicate,
            final String fieldName) {
        return predicate.accept(getInstance()).apply(fieldName);
    }

    @Override
    public Function<String, Predicate<Thing>> visitEq(@Nullable final Object value) {
        @Nullable final Object resolvedValue = resolveValue(value);
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(obj -> {
                            // special NULL handling
                            if (NULL_LITERAL == obj && null == resolvedValue) {
                                return true;
                            } else if (obj instanceof Comparable && resolvedValue instanceof Comparable) {
                                return compare((Comparable<?>) resolvedValue, (Comparable<?>) obj) == 0;
                            }
                            return false;
                        })
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitNe(@Nullable final Object value) {
        @Nullable final Object resolvedValue = resolveValue(value);
        return fieldName ->
                thing -> !getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(obj -> {
                            // special NULL handling
                            if (NULL_LITERAL == obj && null == resolvedValue) {
                                return true;
                            } else if (obj instanceof Comparable && resolvedValue instanceof Comparable) {
                                return compare((Comparable<?>) resolvedValue, (Comparable<?>) obj) == 0;
                            }
                            return false;
                        })
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitGe(@Nullable final Object value) {
        @Nullable final Object resolvedValue = resolveValue(value);
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(obj -> obj instanceof Comparable && resolvedValue instanceof Comparable)
                        .map(Comparable.class::cast)
                        .filter(obj -> compare((Comparable<?>) resolvedValue, obj) >= 0)
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitGt(@Nullable final Object value) {
        @Nullable final Object resolvedValue = resolveValue(value);
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(obj -> obj instanceof Comparable && resolvedValue instanceof Comparable)
                        .map(Comparable.class::cast)
                        .filter(obj -> compare((Comparable<?>) resolvedValue, obj) > 0)
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitLe(@Nullable final Object value) {
        @Nullable final Object resolvedValue = resolveValue(value);
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(obj -> obj instanceof Comparable && resolvedValue instanceof Comparable)
                        .map(Comparable.class::cast)
                        .filter(obj -> compare((Comparable<?>) resolvedValue, obj) <= 0)
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitLt(@Nullable final Object value) {
        @Nullable final Object resolvedValue = resolveValue(value);
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(obj -> obj instanceof Comparable && resolvedValue instanceof Comparable)
                        .map(Comparable.class::cast)
                        .filter(obj -> compare((Comparable<?>) resolvedValue, obj) < 0)
                        .isPresent();
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    private static int compare(final Comparable value, final Comparable obj) {
        final Comparable comparableObj = asNumber(obj);
        final Comparable comparableValue = asNumber(value);
        // best effort try to convert both values to a BigDecimal in order to compare them:
        if (comparableValue instanceof String && comparableObj instanceof BigDecimal) {
            try {
                return comparableObj.compareTo(new BigDecimal((String) comparableValue));
            } catch (final NumberFormatException e) {
                // continue trying
            }
        } else if (comparableValue instanceof BigDecimal && comparableObj instanceof String) {
            try {
                return new BigDecimal((String) comparableObj).compareTo((BigDecimal) comparableValue);
            } catch (final NumberFormatException e) {
                // continue trying
            }
        }

        if (comparableValue.getClass().equals(comparableObj.getClass())) {
            // only compare same classes:
            return comparableObj.compareTo(comparableValue);
        } else {
            // as a fallback, for different types, compare by their string representation:
            final String comparableObjString = comparableObj.toString();
            final String comparableValueString = comparableValue.toString();
            return comparableObjString.compareTo(comparableValueString);
        }
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    private static Comparable asNumber(final Comparable<?> comparable) {
        return comparable instanceof Number ? new BigDecimal(comparable.toString()) : comparable;
    }

    @Override
    public Function<String, Predicate<Thing>> visitIn(final List<?> values) {
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .flatMap(ThingPredicatePredicateVisitor::mapJsonValueToJava)
                        .filter(Comparable.class::isInstance)
                        .map(Comparable.class::cast)
                        .filter(obj -> values.stream().map(this::resolveValue)
                                .anyMatch(v -> compare((Comparable<?>) v, obj) == 0))
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitLike(@Nullable final String value) {
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .filter(str -> null != value && Pattern.compile(value).matcher(str).matches())
                        .isPresent();
    }

    @Override
    public Function<String, Predicate<Thing>> visitILike(@Nullable final String value) {
        return fieldName ->
                thing -> getThingField(fieldName, thing)
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .filter(str -> null != value && Pattern.compile(value, Pattern.CASE_INSENSITIVE).matcher(str).matches())
                        .isPresent();
    }
    
    @Nullable
    private Object resolveValue(@Nullable final Object value) {
        if (value instanceof ParsedPlaceholder) {
            final String prefix = ((ParsedPlaceholder) value).getPrefix();
            final String name = ((ParsedPlaceholder) value).getName();
            return additionalPlaceholderResolvers.stream()
                    .filter(pr -> prefix.equals(pr.getPrefix()))
                    .filter(pr -> pr.supports(name))
                    .flatMap(pr -> pr.resolveValues(name).stream())
                    .findFirst()
                    .orElse(null);
        }
        return value;
    }

    private Optional<JsonValue> getThingField(final CharSequence fieldName, final Thing thing) {
        return Optional.ofNullable(
                thing.toJson(p -> true)
                        .getValue(fieldName) // first, try resolving via the thing
                        .orElseGet(() -> {// if that returns nothing, try resolving using the placeholder resolvers:
                            final String[] fieldNameSplit = fieldName.toString().split(Expression.SEPARATOR, 2);
                            if (fieldNameSplit.length > 1) {
                                final String placeholderPrefix = fieldNameSplit[0];
                                final String placeholderName = fieldNameSplit[1];
                                return additionalPlaceholderResolvers.stream()
                                        .filter(pr -> placeholderPrefix.equals(pr.getPrefix()))
                                        .filter(pr -> pr.supports(placeholderName))
                                        .flatMap(pr -> pr.resolveValues(placeholderName).stream())
                                        .map(JsonValue::of)
                                        .findFirst()
                                        .orElse(null);
                            } else {
                                return null;
                            }
                        })
        );
    }

    private static Optional<Object> mapJsonValueToJava(final JsonValue jsonValue) {
        final Optional<Object> result;

        if (jsonValue.isString()) {
            result = Optional.of(jsonValue.asString());
        } else if (jsonValue.isBoolean()) {
            result = Optional.of(jsonValue.asBoolean());
        } else if (jsonValue.isNull()) {
            result = Optional.of(NULL_LITERAL);
        } else if (jsonValue.isNumber()) {
            if (jsonValue.isInt()) {
                result = Optional.of(jsonValue.asInt());
            } else if (jsonValue.isLong()) {
                result = Optional.of(jsonValue.asLong());
            } else {
                result = Optional.of(jsonValue.asDouble());
            }
        } else if (jsonValue.isArray()) {
            result = Optional.empty(); // filtering arrays is not supported
        } else if (jsonValue.isObject()) {
            result = Optional.empty(); // filtering objects is not supported
        } else {
            result = Optional.empty();
        }

        return result;
    }

}
