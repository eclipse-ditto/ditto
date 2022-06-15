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
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.scala.model.Filters;

public class GetFilterBsonVisitorTest {

    public static final int VALUE = 123;
    public static final BsonInt32 BSON_VALUE = new BsonInt32(VALUE);
    private FilterFieldExpressionVisitor<Bson> underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new GetFilterBsonVisitor(p -> Filters.eq(p, VALUE), List.of("subject1", "subject2"));
    }

    @Test
    public void testAttribute() {
        final Bson attributeFilter = underTest.visitAttribute("a1");
        final BsonDocument document = toBsonDocument(attributeFilter);
        final BsonValue valueFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(valueFilter).isEqualTo(new BsonDocument("t.attributes.a1", BSON_VALUE));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "attributes.a1", "attributes", "");
    }

    @Test
    public void testFeatureProperty() {
        final Bson attributeFilter = underTest.visitFeatureIdProperty("f1", "temperature");
        final BsonDocument document = toBsonDocument(attributeFilter);
        final BsonValue valueFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(valueFilter).isEqualTo(new BsonDocument("t.features.f1.properties.temperature", BSON_VALUE));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
                "features.f1.properties.temperature",
                "features.f1.properties",
                "features.f1",
                "features",
                "");
    }

    @Test
    public void testFeatureDesiredProperty() {
        final Bson attributeFilter = underTest.visitFeatureIdDesiredProperty("f1", "targetTemperature");
        final BsonDocument document = toBsonDocument(attributeFilter);
        final BsonValue valueFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(valueFilter).isEqualTo(new BsonDocument("t.features.f1.desiredProperties.targetTemperature", BSON_VALUE));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
                "features.f1.desiredProperties.targetTemperature",
                "features.f1.desiredProperties",
                "features.f1",
                "features",
                "");
    }
    @Test
    public void testWildcardFeatureProperty() {
        final Bson attributeFilter = underTest.visitFeatureIdProperty("*", "temperature");
        final BsonDocument document = toBsonDocument(attributeFilter);
        final BsonArray elemMatchAnd = document.get("f").asDocument().get(ELEM_MATCH).asDocument().get(AND).asArray();
        final BsonValue valueFilter = elemMatchAnd.get(0);
        final BsonValue authFilter = elemMatchAnd.get(1);
        assertThat(valueFilter).isEqualTo(new BsonDocument("properties.temperature", BSON_VALUE));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"),
                "properties.temperature",
                "properties",
                "id",
                "features",
                "");
    }

    @Test
    public void testSimplePropertyWithLeadingSlash() {
        final Bson attributeFilter = underTest.visitSimple(JsonPointer.of("_modified").toString());
        final BsonDocument document = toBsonDocument(attributeFilter);
        final BsonValue valueFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(valueFilter).isEqualTo(new BsonDocument("t._modified", BSON_VALUE));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "_modified", "");
    }

    @Test
    public void testSimplePropertyNoLeadingSlash() {
        final Bson attributeFilter = underTest.visitSimple("_id");
        final BsonDocument document = toBsonDocument(attributeFilter);
        assertThat(document).isEqualTo(new BsonDocument("_id", BSON_VALUE));
    }

    @Test
    public void testMetadata() {
        final Bson metadataFilter = underTest.visitMetadata("m1");
        final BsonDocument document = toBsonDocument(metadataFilter);
        final BsonValue valueFilter = document.getArray(AND).get(0);
        final BsonValue authFilter = document.getArray(AND).get(1);
        assertThat(valueFilter).isEqualTo(new BsonDocument("t._metadata.m1", BSON_VALUE));
        assertAuthFilter(authFilter, List.of("subject1", "subject2"), "_metadata.m1", "_metadata", "");
    }

}
