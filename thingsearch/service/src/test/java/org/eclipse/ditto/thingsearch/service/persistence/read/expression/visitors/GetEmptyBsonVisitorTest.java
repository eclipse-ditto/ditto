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
package org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.AND;
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.ELEM_MATCH;
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.OR;
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.assertAuthFilter;
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.toBsonDocument;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link GetEmptyBsonVisitor}.
 */
public class GetEmptyBsonVisitorTest {

    private ExistsFieldExpressionVisitor<Bson> underTest;

    @Before
    public void setUp() {
        underTest = new GetEmptyBsonVisitor(List.of("subject1", "subject2"));
    }

    @Test
    public void testAttribute() {
        final Bson filterBson = underTest.visitAttribute("a1");
        final BsonDocument document = toBsonDocument(filterBson);

        // Should be $and with [emptyBson, authFilter]
        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();
        final BsonValue authFilter = andArray.get(1);

        // emptyFilter should be $or with 4 conditions
        assertEmptyOrFilter(emptyFilter, "t.attributes.a1");
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "attributes.a1", "attributes", "");
    }

    @Test
    public void testFeature() {
        final Bson filterBson = underTest.visitFeature("f1");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t.features.f1");
    }

    @Test
    public void testFeatureProperties() {
        final Bson filterBson = underTest.visitFeatureProperties("f1");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t.features.f1.properties");
        assertAuthFilter(andArray.get(1), List.of("subject1", "subject2"),
                "features.f1.properties", "features.f1", "features", "");
    }

    @Test
    public void testFeatureDesiredProperties() {
        final Bson filterBson = underTest.visitFeatureDesiredProperties("f1");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t.features.f1.desiredProperties");
    }

    @Test
    public void testFeatureProperty() {
        final Bson filterBson = underTest.visitFeatureIdProperty("f1", "temperature");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t.features.f1.properties.temperature");
        assertAuthFilter(andArray.get(1), List.of("subject1", "subject2"),
                "features.f1.properties.temperature",
                "features.f1.properties",
                "features.f1",
                "features",
                "");
    }

    @Test
    public void testFeatureDesiredProperty() {
        final Bson filterBson = underTest.visitFeatureIdDesiredProperty("f1", "targetTemperature");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t.features.f1.desiredProperties.targetTemperature");
    }

    @Test
    public void testWildcardFeatureProperty() {
        final Bson filterBson = underTest.visitFeatureIdProperty("*", "temperature");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray elemMatchAnd = document.get("f").asDocument().get(ELEM_MATCH).asDocument().get(AND).asArray();
        final BsonDocument emptyFilter = elemMatchAnd.get(0).asDocument();
        final BsonValue authFilter = elemMatchAnd.get(1);

        assertEmptyOrFilter(emptyFilter, "properties.temperature");
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
                "properties.temperature",
                "properties",
                "id",
                "features",
                "");
    }

    @Test
    public void testSimplePropertyWithLeadingSlash() {
        final Bson filterBson = underTest.visitSimple("/_modified");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t._modified");
    }

    @Test
    public void testSimplePropertyNoLeadingSlash() {
        final Bson filterBson = underTest.visitSimple("_id");
        final BsonDocument document = toBsonDocument(filterBson);

        // Root-level field: no auth filter, just the empty $or
        assertEmptyOrFilter(document, "_id");
    }

    @Test
    public void testMetadata() {
        final Bson filterBson = underTest.visitMetadata("m1");
        final BsonDocument document = toBsonDocument(filterBson);

        final BsonArray andArray = document.getArray(AND);
        final BsonDocument emptyFilter = andArray.get(0).asDocument();

        assertEmptyOrFilter(emptyFilter, "t._metadata.m1");
        assertAuthFilter(andArray.get(1), List.of("subject1", "subject2"),
                "_metadata.m1", "_metadata", "");
    }

    @Test
    public void testWithoutAuthorizationSubjects() {
        final ExistsFieldExpressionVisitor<Bson> noAuthVisitor = new GetEmptyBsonVisitor(null);
        final Bson filterBson = noAuthVisitor.visitAttribute("a1");
        final BsonDocument document = toBsonDocument(filterBson);

        // Without auth, should be just the $or (no $and wrapping)
        assertEmptyOrFilter(document, "t.attributes.a1");
    }

    /**
     * Asserts that the given BSON document is a 4-way {@code $or} filter for "empty" semantics:
     * null (covers absent), empty array, empty object, empty string.
     */
    private static void assertEmptyOrFilter(final BsonDocument document, final String fieldPath) {
        final BsonArray orArray = document.getArray(OR);
        assertThat(orArray).hasSize(4);

        // Condition 1: $eq: null (also matches absent fields in MongoDB)
        assertThat(orArray.get(0).asDocument())
                .isEqualTo(new BsonDocument(fieldPath, BsonNull.VALUE));

        // Condition 2: $eq: [] (empty array)
        assertThat(orArray.get(1).asDocument())
                .isEqualTo(new BsonDocument(fieldPath, new BsonArray()));

        // Condition 3: $eq: {} (empty object)
        assertThat(orArray.get(2).asDocument())
                .isEqualTo(new BsonDocument(fieldPath, new BsonDocument()));

        // Condition 4: $eq: "" (empty string)
        assertThat(orArray.get(3).asDocument())
                .isEqualTo(new BsonDocument(fieldPath, new BsonString("")));
    }

}
