/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.model.ParsedPlaceholder;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor;

/**
 * Creates Aggregation Bson of a predicate.
 */
public class CreateBsonAggregationPredicateVisitor implements PredicateVisitor<Function<String, Bson>> {

    private static CreateBsonAggregationPredicateVisitor instance;

    private static final String LEADING_WILDCARD = "^\\Q\\E.*";
    private static final String TRAILING_WILDCARD = ".*\\Q\\E$";

    private final List<PlaceholderResolver<?>> additionalPlaceholderResolvers;

    private CreateBsonAggregationPredicateVisitor(final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        this.additionalPlaceholderResolvers =
                Collections.unmodifiableList(new ArrayList<>(additionalPlaceholderResolvers));
    }

    /**
     * Gets the singleton instance of this {@link org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors.CreateBsonAggregationPredicateVisitor}.
     *
     * @return the singleton instance.
     */
    public static CreateBsonAggregationPredicateVisitor getInstance() {
        if (null == instance) {
            instance = new CreateBsonAggregationPredicateVisitor(Collections.emptyList());
        }
        return instance;
    }

    /**
     * Creates a new instance of {@code CreateBsonAggregationPredicateVisitor} with additional custom placeholder resolvers.
     *
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL predicates.
     * @return the created instance.
     * @since 3.6.0
     */
    public static CreateBsonAggregationPredicateVisitor createInstance(
            final PlaceholderResolver<?>... additionalPlaceholderResolvers) {
        return createInstance(Arrays.asList(additionalPlaceholderResolvers));
    }

    /**
     * Creates a new instance of {@code CreateBsonAggregationPredicateVisitor} with additional custom placeholder resolvers.
     *
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL predicates.
     * @return the created instance.
     * @since 3.6.0
     */
    public static CreateBsonAggregationPredicateVisitor createInstance(
            final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        return new CreateBsonAggregationPredicateVisitor(additionalPlaceholderResolvers);
    }

    /**
     * Creates a Bson from a predicate and its field name.
     *
     * @param predicate The predicate to generate the Bson from.
     * @param fieldName Name of the field where the predicate is applied to.
     * @return The created Bson.
     */
    public static Bson apply(final Predicate predicate, final String fieldName) {
        return predicate.accept(getInstance()).apply(fieldName);
    }

    @Override
    public Function<String, Bson> visitEq(@Nullable final Object value) {
            return fieldName ->  new BsonDocument("$eq", new BsonArray(List.of(new BsonString("$" + fieldName), resolveValue(value))));
    }

    @Override
    public Function<String, Bson> visitGe(@Nullable final Object value) {
        return fieldName -> new BsonDocument("$gte", new BsonArray(List.of(new BsonString("$" + fieldName), resolveValue(value))));
    }

    @Override
    public Function<String, Bson> visitGt(@Nullable final Object value) {
        return fieldName -> new BsonDocument("$gt", new BsonArray(List.of(new BsonString("$" + fieldName), resolveValue(value))));
    }

    @Override
    public Function<String, Bson> visitIn(final List<?> values) {
        final List<BsonValue> collect = values.stream().map(this::resolveValue).toList();
        return fieldName -> new Document("$in", Arrays.asList("$" + fieldName, collect));
    }

    @Override
    public Function<String, Bson> visitLe(@Nullable final Object value) {
        return fieldName -> new BsonDocument("$lte", new BsonArray(List.of(new BsonString("$" + fieldName), resolveValue(value))));
    }

    @Override
    public Function<String, Bson> visitLike(final String value) {
        // remove leading or trailing wildcard because queries like /^a/ are much faster than /^a.*$/ or /^a.*/
        // from mongodb docs:
        // "Additionally, while /^a/, /^a.*/, and /^a.*$/ match equivalent strings, they have different performance
        // characteristics. All of these expressions use an index if an appropriate index exists;
        // however, /^a.*/, and /^a.*$/ are slower. /^a/ can stop scanning after matching the prefix."
        final String valueWithoutLeadingOrTrailingWildcard = removeLeadingOrTrailingWildcard(value);
        return fieldName -> new Document("$regexMatch",
                new Document("input", "$" + fieldName)
                        .append("regex", valueWithoutLeadingOrTrailingWildcard));
    }

    @Override
    public Function<String, Bson> visitILike(final String value) {
        // remove leading or trailing wildcard because queries like /^a/ are much faster than /^a.*$/ or /^a.*/
        // from mongodb docs:
        // "Additionally, while /^a/, /^a.*/, and /^a.*$/ match equivalent strings, they have different performance
        // characteristics. All of these expressions use an index if an appropriate index exists;
        // however, /^a.*/, and /^a.*$/ are slower. /^a/ can stop scanning after matching the prefix."
        final String valueWithoutLeadingOrTrailingWildcard = removeLeadingOrTrailingWildcard(value);
        Pattern pattern = Pattern.compile(valueWithoutLeadingOrTrailingWildcard, Pattern.CASE_INSENSITIVE);
        return fieldName -> new Document("$regexMatch",
                new Document("input", "$" + fieldName)
                        .append("regex", pattern));
    }

    private static String removeLeadingOrTrailingWildcard(final String valueString) {
        String valueWithoutLeadingOrTrailingWildcard = valueString;
        if (valueString.startsWith(LEADING_WILDCARD)) {
            valueWithoutLeadingOrTrailingWildcard = valueWithoutLeadingOrTrailingWildcard
                    .substring(LEADING_WILDCARD.length());
        }
        if (valueString.endsWith(TRAILING_WILDCARD)) {
            final int endIndex = valueWithoutLeadingOrTrailingWildcard.length() - TRAILING_WILDCARD.length();
            if (endIndex > 0) {
                valueWithoutLeadingOrTrailingWildcard = valueWithoutLeadingOrTrailingWildcard.substring(0, endIndex);
            }
        }
        return valueWithoutLeadingOrTrailingWildcard;
    }

    @Override
    public Function<String, Bson> visitLt(final Object value) {
        return fieldName -> new BsonDocument("$lt", new BsonArray(List.of(new BsonString("$" + fieldName), resolveValue(value))));
    }

    @Override
    public Function<String, Bson> visitNe(final Object value) {
        return fieldName ->  new BsonDocument("$ne", new BsonArray(List.of(new BsonString("$" + fieldName), resolveValue(value))));
    }

    private BsonValue resolveValue(final Object value) {
        if (value instanceof ParsedPlaceholder) {
            final String prefix = ((ParsedPlaceholder) value).getPrefix();
            final String name = ((ParsedPlaceholder) value).getName();
            return additionalPlaceholderResolvers.stream()
                    .filter(pr -> prefix.equals(pr.getPrefix()))
                    .filter(pr -> pr.supports(name))
                    .flatMap(pr -> pr.resolveValues(name).stream())
                    .map(BsonString::new)
                    .findFirst()
                    .orElse(null);
        }
        if (value instanceof Long) {
            return new BsonInt64((Long) value);
        } else if (value instanceof Integer) {
            return new BsonInt64((Integer) value);
        } else if (value instanceof Double) {
            return new BsonDouble((Double) value);
        } else if (value instanceof String) {
            return new BsonString((String) value);
        } else if (value instanceof ArrayList) {
            return new BsonArray((ArrayList) value);
        } else if (value instanceof Boolean) {
            return new BsonBoolean((Boolean) value);
        } else  {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
    }
}
