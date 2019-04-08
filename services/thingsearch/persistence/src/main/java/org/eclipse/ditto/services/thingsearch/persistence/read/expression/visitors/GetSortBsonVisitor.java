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
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import static org.eclipse.ditto.services.thingsearch.persistence.MongoSortKeyMappingFunction.mapSortKey;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PROPERTIES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_SORTING;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.SLASH;
import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.model.query.expression.visitors.SortFieldExpressionVisitor;

import com.mongodb.client.model.Sorts;

/**
 * Creates a Bson sort option based on an expression.
 */
public final class GetSortBsonVisitor implements SortFieldExpressionVisitor<List<Bson>> {

    private final SortDirection sortDirection;

    /**
     * Constructs a {@link GetSortBsonVisitor}.
     *
     * @param sortDirection the {@link SortDirection} needed to create a valid Bson for sorting.
     */
    private GetSortBsonVisitor(final SortDirection sortDirection) {
        this.sortDirection = requireNonNull(sortDirection);
    }

    /**
     * Creates a Bson sort option based on an expression.
     *
     * @param expression the expression to generate the Bson from.
     * @param sortDirection the {@link SortDirection} needed to create a valid Bson for sorting.
     * @return the Bson.
     */
    public static List<Bson> apply(final SortFieldExpression expression, final SortDirection sortDirection) {
        return expression.acceptSortVisitor(new GetSortBsonVisitor(sortDirection));
    }

    @Override
    public List<Bson> visitAttribute(final String key) {
        final String sortKey = mapSortKey(FIELD_SORTING, FIELD_ATTRIBUTES, key);
        return getSortBson(sortDirection, sortKey);
    }

    @Override
    public List<Bson> visitFeatureIdProperty(final String featureId, final String property) {
        final String fieldPath = mapSortKey(FIELD_SORTING, FIELD_FEATURES, featureId, FIELD_PROPERTIES, property);
        return getSortBson(sortDirection, fieldPath);
    }

    @Override
    public List<Bson> visitSimple(final String fieldName) {
        final String sortKey = fieldName.startsWith(SLASH)
                ? mapSortKey(FIELD_SORTING + fieldName)
                : fieldName;
        return getSortBson(sortDirection, sortKey);
    }

    /**
     * Builds singleton List (with always 1 entry) of a Bson document used for sorting based on the passed
     * {@code sortDirection} and {@code fieldName}.
     *
     * @param sortDirection the {@link SortDirection} to apply.
     * @param sortKey the sorting key.
     * @return the singleton List of a Bson sort document.
     */
    private static List<Bson> getSortBson(final SortDirection sortDirection, final String sortKey) {
        requireNonNull(sortDirection);

        final Bson sort;
        switch (sortDirection) {
            case ASC:
                sort = Sorts.ascending(sortKey);
                break;
            case DESC:
                sort = Sorts.descending(sortKey);
                break;
            default:
                throw new IllegalStateException("Unknown SortDirection=" + sortDirection);
        }

        return Collections.singletonList(sort);
    }
}
