/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.LOCATION_ATTRIBUTE;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link EntityTagCalculator}.
 */
class EntityTagCalculatorTest {

    private EntityTagCalculator underTest;

    @BeforeEach
    void setUp() {
        underTest = EntityTagCalculator.getInstance();
    }

    @Test
    void testVisitThing() {
        assertThat(underTest.visitThing(JsonPointer.empty(), TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(TestConstants.Thing.THING_V2));
    }

    @Test
    void testVisitThingDefinition() {
        assertThat(
                underTest.visitThingDefinition(Thing.JsonFields.DEFINITION.getPointer(), TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(TestConstants.Thing.THING_V2));
    }

    @Test
    void testVisitPolicyId() {
        assertThat(underTest.visitPolicyId(Thing.JsonFields.POLICY_ID.getPointer(), TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(TestConstants.Thing.THING_V2));
    }

    @Test
    void testVisitAttributes() {
        assertThat(underTest.visitAttributes(Thing.JsonFields.ATTRIBUTES.getPointer(), TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(TestConstants.Thing.THING_V2.getAttributes().orElseThrow()));
    }

    @Test
    void testVisitAttribute() {
        final JsonPointer attributePath = JsonPointer.of("attributes/location");
        assertThat(underTest.visitAttribute(attributePath, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(LOCATION_ATTRIBUTE));
    }

    @Test
    void testVisitFeatures() {
        assertThat(underTest.visitFeatures(Thing.JsonFields.FEATURES.getPointer(), TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(TestConstants.Feature.FEATURES));
    }

    @Test
    void testVisitFeature() {
        final JsonPointer pointer = JsonPointer.of("features/" + FLUX_CAPACITOR_ID);
        assertThat(underTest.visitFeature(pointer, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(FLUX_CAPACITOR));
    }

    @Test
    void testVisitFeatureProperties() {
        final JsonPointer pointer = JsonPointer.of("features/" + FLUX_CAPACITOR_ID + "/properties");
        assertThat(underTest.visitFeatureProperties(pointer, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(FLUX_CAPACITOR_PROPERTIES));
    }

    @Test
    void testVisitFeatureProperty() {
        final JsonPointer pointer = JsonPointer.of("features/" + FLUX_CAPACITOR_ID + "/property/target_year_1");
        assertThat(underTest.visitFeatureProperty(pointer, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(FLUX_CAPACITOR_PROPERTIES.getValue("target_year_1").orElseThrow()));
    }

    @Test
    void testVisitFeatureDesiredProperties() {
        final JsonPointer pointer = JsonPointer.of("features/" + FLUX_CAPACITOR_ID + "/desiredProperties");
        assertThat(underTest.visitFeatureDesiredProperties(pointer, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(FLUX_CAPACITOR_PROPERTIES));
    }

    @Test
    void testVisitFeatureDesiredProperty() {
        final JsonPointer pointer = JsonPointer.of("features/" + FLUX_CAPACITOR_ID + "/desiredProperty/target_year_1");
        assertThat(underTest.visitFeatureDesiredProperty(pointer, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(FLUX_CAPACITOR_PROPERTIES.getValue("target_year_1").orElseThrow()));
    }

    @Test
    void testVisitFeatureDefinition() {
        final JsonPointer pointer = JsonPointer.of("features/" + FLUX_CAPACITOR_ID + "/definition");
        assertThat(underTest.visitFeatureDefinition(pointer, TestConstants.Thing.THING_V2))
                .isEqualTo(EntityTag.fromEntity(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION));
    }
}
