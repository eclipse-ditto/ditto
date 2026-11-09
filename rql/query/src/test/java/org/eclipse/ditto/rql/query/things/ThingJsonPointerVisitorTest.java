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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Tests {@link ThingJsonPointerVisitor}.
 */
public final class ThingJsonPointerVisitorTest {

    private static final ModelBasedThingsFieldExpressionFactory FIELD_EXPRESSION_FACTORY =
            ModelBasedThingsFieldExpressionFactory.getInstance();

    @Test
    public void attribute() {
        assertFilterFieldPointer("attributes/a", "/attributes/a");
        assertFilterFieldPointer("attributes/a/b", "/attributes/a/b");
    }

    @Test
    public void featureDefinition() {
        assertFilterFieldPointer("features/f/definition", "/features/f/definition");
    }

    @Test
    public void featureProperty() {
        assertFilterFieldPointer("features/f/properties/p", "/features/f/properties/p");
        assertFilterFieldPointer("features/f/properties/p/q", "/features/f/properties/p/q");
    }

    @Test
    public void featureDesiredProperty() {
        assertFilterFieldPointer("features/f/desiredProperties/p", "/features/f/desiredProperties/p");
        assertFilterFieldPointer("features/f/desiredProperties/p/q", "/features/f/desiredProperties/p/q");
    }

    @Test
    public void metadata() {
        assertFilterFieldPointer("_metadata/attributes/a", "/_metadata/attributes/a");
    }

    @Test
    public void simpleField() {
        assertFilterFieldPointer("thingId", "/thingId");
        assertFilterFieldPointer("definition", "/definition");
        assertFilterFieldPointer("_modified", "/_modified");
    }

    @Test
    public void placeholder() {
        // placeholders are mapped to themselves, see ThingsFieldExpressionFactory#of(Map, Collection)
        final FilterFieldExpression expression = ThingsFieldExpressionFactory.of(
                Collections.singletonMap("topic:action", "topic:action")).filterBy("topic:action");

        assertFilterFieldPointer(expression, "/topic:action");
    }

    @Test
    public void feature() {
        assertExistsFieldPointer("features/f", "/features/f");
    }

    @Test
    public void featureProperties() {
        assertExistsFieldPointer("features/f/properties", "/features/f/properties");
    }

    @Test
    public void featureDesiredProperties() {
        assertExistsFieldPointer("features/f/desiredProperties", "/features/f/desiredProperties");
    }

    /**
     * Asserts the pointer of a filter field and that it addresses the same path {@link FilterThingPredicateVisitor}
     * evaluates the filter on.
     */
    private static void assertFilterFieldPointer(final String field, final String expectedPointer) {
        assertFilterFieldPointer(FIELD_EXPRESSION_FACTORY.filterBy(field), expectedPointer);
    }

    private static void assertFilterFieldPointer(final FilterFieldExpression expression,
            final String expectedPointer) {

        final AtomicReference<String> filteredPath = new AtomicReference<>();
        FilterThingPredicateVisitor.apply(expression, path -> {
            filteredPath.set(path);
            return thing -> true;
        });

        final JsonPointer pointer = ThingJsonPointerVisitor.apply(expression);

        assertThat((Object) pointer).isEqualTo(JsonPointer.of(expectedPointer));
        assertThat((Object) pointer).isEqualTo(JsonPointer.of(filteredPath.get()));
    }

    private static void assertExistsFieldPointer(final String field, final String expectedPointer) {
        assertThat((Object) ThingJsonPointerVisitor.apply(FIELD_EXPRESSION_FACTORY.existsBy(field)))
                .isEqualTo(JsonPointer.of(expectedPointer));
    }

}
