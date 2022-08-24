/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.assertAuthFilter;
import static org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.FilterAssertions.toBsonDocument;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.junit.Before;
import org.junit.Test;

public class GetExistsBsonVisitorTest {

    public static final BsonDocument EXISTS = new BsonDocument("$exists",
            new BsonBoolean(true));
    private ExistsFieldExpressionVisitor<Bson> underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new GetExistsBsonVisitor(List.of("subject1", "subject2"));
    }

    @Test
    public void testAttribute() {
        final Bson filterBson = underTest.visitAttribute("a1");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t.attributes.a1", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "attributes.a1", "attributes", "");
    }

    @Test
    public void testFeature() {
        final Bson filterBson = underTest.visitFeature("f1");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t.features.f1", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "features.f1", "features", "");
    }

    @Test
    public void testFeatureProperties() {
        final Bson filterBson = underTest.visitFeatureProperties("f1");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t.features.f1.properties", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "features.f1.properties","features.f1",
                "features", "");
    }

    @Test
    public void testFeatureDesiredProperties() {
        final Bson filterBson = underTest.visitFeatureDesiredProperties("f1");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t.features.f1.desiredProperties", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "features.f1.desiredProperties","features.f1",
                "features", "");
    }

    @Test
    public void testFeatureProperty() {
        final Bson filterBson = underTest.visitFeatureIdProperty("f1", "temperature");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t.features.f1.properties.temperature", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
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
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t.features.f1.desiredProperties.targetTemperature", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
                "features.f1.desiredProperties.targetTemperature",
                "features.f1.desiredProperties",
                "features.f1",
                "features",
                "");
    }
    @Test
    public void testWildcardFeatureProperty() {
        final Bson filterBson = underTest.visitFeatureIdProperty("*", "temperature");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonArray elemMatchAnd = document.get("f").asDocument().get(ELEM_MATCH).asDocument().get(AND).asArray();
        final BsonValue existsFilter = elemMatchAnd.get(0);
        final BsonValue authFilter = elemMatchAnd.get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("properties.temperature", EXISTS));
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
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t._modified", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
                "_modified",
                "");
    }

    @Test
    public void testSimplePropertyNoLoadingSlash() {
        final Bson filterBson = underTest.visitSimple("_id");
        final BsonDocument document = toBsonDocument(filterBson);
        assertThat(document).isEqualTo(new BsonDocument("_id", EXISTS));
    }

    @Test
    public void testMetadata() {
        final Bson filterBson = underTest.visitMetadata("m1");
        final BsonDocument document = toBsonDocument(filterBson);
        final BsonValue existsFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(existsFilter).isEqualTo(new BsonDocument("t._metadata.m1", EXISTS));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "_metadata.m1", "_metadata", "");
    }

}
