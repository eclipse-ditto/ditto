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

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.PredicateVisitor;

/**
 * Creates Bson of a predicate.
 */
public class CreateBsonPredicateVisitor implements PredicateVisitor<Function<String, Bson>> {

    private static CreateBsonPredicateVisitor instance;

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
        return fieldName -> Filters.regex(fieldName, value, "");
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
