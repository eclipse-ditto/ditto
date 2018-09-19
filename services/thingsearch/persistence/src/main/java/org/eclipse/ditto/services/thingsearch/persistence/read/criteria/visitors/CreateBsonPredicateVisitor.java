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
package org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors;

import java.util.List;
import java.util.function.Function;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.criteria.visitors.PredicateVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates Bson of a predicate.
 */
public class CreateBsonPredicateVisitor implements PredicateVisitor<Function<String, Bson>> {

    private static CreateBsonPredicateVisitor instance;

    private static final String LEADING_WILDCARD = "^\\Q\\E.*";
    private static final String TRAILING_WILDCARD = ".*\\Q\\E$";

    private CreateBsonPredicateVisitor() {
        // only internally instantiable
    }

    /**
     * Gets the singleton instance of this {@link CreateBsonPredicateVisitor}.
     *
     * @return the singleton instance.
     */
    public static CreateBsonPredicateVisitor getInstance() {
        if (null == instance) {
            instance = new CreateBsonPredicateVisitor();
        }
        return instance;
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
    public Function<String, Bson> visitEq(final Object value) {
        return fieldName -> Filters.eq(fieldName, value);
    }

    @Override
    public Function<String, Bson> visitGe(final Object value) {
        return fieldName -> Filters.gte(fieldName, value);
    }

    @Override
    public Function<String, Bson> visitGt(final Object value) {
        return fieldName -> Filters.gt(fieldName, value);
    }

    @Override
    public Function<String, Bson> visitIn(final List<?> values) {
        return fieldName -> Filters.in(fieldName, values);
    }

    @Override
    public Function<String, Bson> visitLe(final Object value) {
        return fieldName -> Filters.lte(fieldName, value);
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
        return fieldName -> Filters.lt(fieldName, value);
    }

    @Override
    public Function<String, Bson> visitNe(final Object value) {
        return fieldName -> Filters.ne(fieldName, value);
    }
}
