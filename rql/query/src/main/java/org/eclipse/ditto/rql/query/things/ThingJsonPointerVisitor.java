/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.FieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;

/**
 * FieldExpressionVisitor determining the {@link JsonPointer} of the field a {@link FieldExpression} addresses within the
 * JSON representation of a {@link Thing}, e.g. {@code /features/{featureId}/properties/{property}} for a feature
 * property. These are the same paths {@link FilterThingPredicateVisitor} evaluates a filter on.
 *
 * @since 3.9.9
 */
public final class ThingJsonPointerVisitor implements FieldExpressionVisitor<JsonPointer> {

    private static final ThingJsonPointerVisitor INSTANCE = new ThingJsonPointerVisitor();

    private ThingJsonPointerVisitor() {
        super();
    }

    /**
     * Determines the JSON pointer of the field the passed {@code expression} addresses within a Thing.
     *
     * @param expression the field expression.
     * @return the JSON pointer of the addressed field.
     */
    public static JsonPointer apply(final FieldExpression expression) {
        return expression.accept(INSTANCE);
    }

    @Override
    public JsonPointer visitAttribute(final String key) {
        return Thing.JsonFields.ATTRIBUTES.getPointer().append(JsonPointer.of(key));
    }

    @Override
    public JsonPointer visitFeature(final String featureId) {
        return Thing.JsonFields.FEATURES.getPointer().addLeaf(JsonKey.of(featureId));
    }

    @Override
    public JsonPointer visitFeatureDefinition(final String featureId) {
        return visitFeature(featureId).append(Feature.JsonFields.DEFINITION.getPointer());
    }

    @Override
    public JsonPointer visitFeatureProperties(final CharSequence featureId) {
        return visitFeature(featureId.toString()).append(Feature.JsonFields.PROPERTIES.getPointer());
    }

    @Override
    public JsonPointer visitFeatureDesiredProperties(final CharSequence featureId) {
        return visitFeature(featureId.toString()).append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer());
    }

    @Override
    public JsonPointer visitFeatureIdProperty(final String featureId, final String property) {
        return visitFeatureProperties(featureId).append(JsonPointer.of(property));
    }

    @Override
    public JsonPointer visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence property) {
        return visitFeatureDesiredProperties(featureId).append(JsonPointer.of(property));
    }

    @Override
    public JsonPointer visitSimple(final String fieldName) {
        // mapped simple field names already are slash-delimited paths, e.g. "/thingId"
        return JsonPointer.of(fieldName);
    }

    @Override
    public JsonPointer visitMetadata(final String key) {
        return Thing.JsonFields.METADATA.getPointer().append(JsonPointer.of(key));
    }

}
