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
package org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.model.ParsedPlaceholder;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates Bson of a predicate.
 */
public class CreateBsonPredicateVisitor implements PredicateVisitor<Function<String, Bson>> {

    private static CreateBsonPredicateVisitor instance;

    private static final String LEADING_WILDCARD = "^\\Q\\E.*";
    private static final String TRAILING_WILDCARD = ".*\\Q\\E$";

    private final List<PlaceholderResolver<?>> additionalPlaceholderResolvers;

    private CreateBsonPredicateVisitor(final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        this.additionalPlaceholderResolvers =
                Collections.unmodifiableList(new ArrayList<>(additionalPlaceholderResolvers));
    }

    /**
     * Gets the singleton instance of this {@link CreateBsonPredicateVisitor}.
     *
     * @return the singleton instance.
     */
    public static CreateBsonPredicateVisitor getInstance() {
        if (null == instance) {
            instance = new CreateBsonPredicateVisitor(Collections.emptyList());
        }
        return instance;
    }

    /**
     * Creates a new instance of {@code CreateBsonPredicateVisitor} with additional custom placeholder resolvers.
     *
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL predicates.
     * @return the created instance.
     * @since 2.3.0
     */
    public static CreateBsonPredicateVisitor createInstance(
            final PlaceholderResolver<?>... additionalPlaceholderResolvers) {
        return createInstance(Arrays.asList(additionalPlaceholderResolvers));
    }

    /**
     * Creates a new instance of {@code CreateBsonPredicateVisitor} with additional custom placeholder resolvers.
     *
     * @param additionalPlaceholderResolvers the additional {@code PlaceholderResolver} to use for resolving
     * placeholders in RQL predicates.
     * @return the created instance.
     * @since 2.3.0
     */
    public static CreateBsonPredicateVisitor createInstance(
            final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        return new CreateBsonPredicateVisitor(additionalPlaceholderResolvers);
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
        if (value == null) {
            // add "exists" clause for eq(null) only to disable conflation of eq(null) and nonexistence by MongoDB
            return fieldName -> Filters.and(Filters.eq(fieldName, null), Filters.exists(fieldName));
        } else {
            return fieldName -> Filters.eq(fieldName, resolveValue(value));
        }
    }

    @Override
    public Function<String, Bson> visitGe(@Nullable final Object value) {
        return fieldName -> Filters.gte(fieldName, resolveValue(value));
    }

    @Override
    public Function<String, Bson> visitGt(@Nullable final Object value) {
        return fieldName -> Filters.gt(fieldName, resolveValue(value));
    }

    @Override
    public Function<String, Bson> visitIn(final List<?> values) {
        return fieldName -> Filters.in(fieldName, values.stream().map(this::resolveValue).collect(Collectors.toList()));
    }

    @Override
    public Function<String, Bson> visitLe(@Nullable final Object value) {
        return fieldName -> Filters.lte(fieldName, resolveValue(value));
    }

    @Override
    public Function<String, Bson> visitLike(final String value) {
        // remove leading or trailing wildcard because queries like /^a/ are much faster than /^a.*$/ or /^a.*/
        // from mongodb docs:
        // "Additionally, while /^a/, /^a.*/, and /^a.*$/ match equivalent strings, they have different performance
        // characteristics. All of these expressions use an index if an appropriate index exists;
        // however, /^a.*/, and /^a.*$/ are slower. /^a/ can stop scanning after matching the prefix."
        final String valueWithoutLeadingOrTrailingWildcard = removeLeadingOrTrailingWildcard(value);
        return fieldName -> Filters.regex(fieldName, valueWithoutLeadingOrTrailingWildcard, "");
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
        return fieldName -> Filters.regex(fieldName, pattern);
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
        return fieldName -> Filters.lt(fieldName, resolveValue(value));
    }

    @Override
    public Function<String, Bson> visitNe(final Object value) {
        return fieldName -> Filters.and(Filters.ne(fieldName, resolveValue(value)), Filters.exists(fieldName));
    }

    private Object resolveValue(final Object value) {
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
}
