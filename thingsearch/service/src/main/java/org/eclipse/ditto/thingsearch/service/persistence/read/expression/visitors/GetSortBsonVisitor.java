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
package org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.SortFieldExpressionVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.MongoSortKeyMappingFunction;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

import com.mongodb.client.model.Sorts;

/**
 * Creates a Bson sort option based on an expression.
 */
public final class GetSortBsonVisitor implements SortFieldExpressionVisitor<String> {

    private GetSortBsonVisitor() {}

    /**
     * Creates a Bson sort option based on an expression.
     *
     * @param expression the expression to generate the Bson from.
     * @param sortDirection the {@link SortDirection} needed to create a valid Bson for sorting.
     * @return the Bson.
     */
    public static List<Bson> apply(final SortFieldExpression expression, final SortDirection sortDirection) {
        return getSortBson(sortDirection, path(expression));
    }

    /**
     * Translate sort options into projections for the sort keys.
     *
     * @param sortOptions sort options.
     * @return projection of the sort keys.
     */
    public static Document projections(final List<SortOption> sortOptions) {
        final Document document = new Document().append(PersistenceConstants.FIELD_PATH_MODIFIED, true);
        sortOptions.stream()
                .map(SortOption::getSortExpression)
                .map(GetSortBsonVisitor::path)
                .forEach(path -> document.append(path, true));
        return document;
    }

    /**
     * Project values of sort keys into an array.
     *
     * @param document document containing the sort keys.
     * @param sortOptions sort options.
     * @return values of sort keys as a JSON array.
     */
    public static JsonArray sortValuesAsArray(final Document document, final List<SortOption> sortOptions) {
        final JsonArrayBuilder builder = JsonFactory.newArrayBuilder();
        sortOptions.stream()
                .map(SortOption::getSortExpression)
                .map(GetSortBsonVisitor::path)
                .map(seekToPath(document))
                .forEach(value -> builder.add(toJsonValue(value)));
        return builder.build();
    }

    /**
     * Translate a sort field expression into a path of the Bson document.
     *
     * @param expression the sort field expression.
     * @return the path.
     */
    public static String path(final SortFieldExpression expression) {
        return expression.acceptSortVisitor(new GetSortBsonVisitor());
    }

    @Override
    public String visitAttribute(final String key) {
        return MongoSortKeyMappingFunction.mapSortKey(
                PersistenceConstants.FIELD_THING, PersistenceConstants.FIELD_ATTRIBUTES, key);
    }

    @Override
    public String visitFeatureDefinition(final String featureId) {
        return MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_THING,
                PersistenceConstants.FIELD_FEATURES, featureId, PersistenceConstants.FIELD_DEFINITION);
    }

    @Override
    public String visitFeatureIdProperty(final String featureId, final String property) {
        return MongoSortKeyMappingFunction.mapSortKey(
                PersistenceConstants.FIELD_THING, PersistenceConstants.FIELD_FEATURES, featureId,
                PersistenceConstants.FIELD_PROPERTIES, property);
    }

    @Override
    public String visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence desiredProperty) {
        return MongoSortKeyMappingFunction.mapSortKey(
                PersistenceConstants.FIELD_THING, PersistenceConstants.FIELD_FEATURES, featureId.toString(),
                PersistenceConstants.FIELD_DESIRED_PROPERTIES,
                desiredProperty.toString());
    }

    @Override
    public String visitSimple(final String fieldName) {
        return fieldName.startsWith(PersistenceConstants.SLASH)
                ? MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_THING + fieldName)
                : fieldName;
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

        final Bson sort = switch (sortDirection) {
            case ASC -> Sorts.ascending(sortKey);
            case DESC -> Sorts.descending(sortKey);
            default -> throw new IllegalStateException("Unknown SortDirection=" + sortDirection);
        };

        return Collections.singletonList(sort);
    }

    private static Function<String, Object> seekToPath(final Document document) {
        return path -> seekToPathImpl(document, path.split("\\."), 0);
    }

    private static Object seekToPathImpl(final Document document, final String[] segments, final int i) {
        if (document == null) {
            return null;
        } else if (i + 1 == segments.length) {
            return document.get(segments[i]);
        } else {
            return seekToPathImpl(document.get(segments[i], Document.class), segments, i + 1);
        }
    }

    private static JsonValue toJsonValue(final Object object) {
        if (object instanceof Document document) {
            return JsonFactory.readFrom((document).toJson());
        } else if (object instanceof BsonValue bsonValue) {
            return DittoBsonJson.getInstance().serialize(bsonValue);
        } else {
            return JsonValue.of(object);
        }
    }

}
