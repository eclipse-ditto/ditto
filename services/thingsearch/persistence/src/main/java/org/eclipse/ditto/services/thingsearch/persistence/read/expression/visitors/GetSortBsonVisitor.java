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
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;

import com.mongodb.client.model.Sorts;

import org.eclipse.ditto.services.thingsearch.persistence.MongoSortKeyMappingFunction;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.SortFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;

/**
 * Creates a Bson sort option based on an expression.
 */
public final class GetSortBsonVisitor implements SortFieldExpressionVisitor<List<Bson>> {

    private final SortDirection sortDirection;

    /**
     * Constructs a {@link GetSortBsonVisitor}.
     * @param sortDirection the {@link SortDirection} needed to create a valid Bson for sorting.
     */
    private GetSortBsonVisitor(final SortDirection sortDirection) {
        this.sortDirection = requireNonNull(sortDirection);
    }

    /**
     * Creates a Bson sort option based on an expression.
     * @param expression the expression to generate the Bson from.
     * @param sortDirection the {@link SortDirection} needed to create a valid Bson for sorting.
     * @return the Bson.
     */
    public static List<Bson> apply(final SortFieldExpression expression, final SortDirection sortDirection) {
        return expression.acceptSortVisitor(new GetSortBsonVisitor(sortDirection));
    }

    @Override
    public List<Bson> visitAttribute(final String key) {
        final String sortKey = MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_ATTRIBUTES, key);
        return getSortBson(sortDirection, sortKey);
    }

    @Override
    public List<Bson> visitFeatureIdProperty(final String featureId, final String property) {
        final String sortKey =
                MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES, featureId, PersistenceConstants.FIELD_PROPERTIES,
                        property);
        return getSortBson(sortDirection, sortKey);
    }

    @Override
    public List<Bson> visitSimple(final String fieldName) {
        return getSortBson(sortDirection, fieldName);
    }

    /**
     * Builds singleton List (with always 1 entry) of a Bson document used for sorting based on the passed
     * {@code sortDirection} and {@code fieldName}.
     *
     * @param sortDirection the {@link SortDirection} to apply.
     * @param fieldName the name of the Field to use for sorting.
     * @return the singleton List of a Bson sort document.
     */
    private static List<Bson> getSortBson(final SortDirection sortDirection, final String fieldName) {
        requireNonNull(sortDirection);

        final Bson sort;
        switch (sortDirection) {
            case ASC:
                sort = Sorts.ascending(fieldName);
                break;
            case DESC:
                sort = Sorts.descending(fieldName);
                break;
            default:
                throw new IllegalStateException("Unknown SortDirection=" + sortDirection);
        }

        return Collections.singletonList(sort);
    }
}
