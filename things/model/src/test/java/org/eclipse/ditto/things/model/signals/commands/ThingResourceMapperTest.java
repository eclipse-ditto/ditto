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
package org.eclipse.ditto.things.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.signals.commands.exceptions.PathUnknownException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests {@link ThingResourceMapper}.
 */
class ThingResourceMapperTest {

    private static final ThingId THING_ID = ThingId.generateRandom();
    private ThingResourceMapper<ThingId, ThingResource> underTest;

    @BeforeEach
    void setUp() {
        underTest = ThingResourceMapper.from(new TestVisitor());
    }

    @ParameterizedTest
    @EnumSource(ThingResourceTestCase.class)
    void map(final ThingResourceTestCase testCase) {
        assertThat(underTest.map(testCase.getPath(), THING_ID)).isEqualTo(testCase.getExpectedResource());
    }

    /**
     * Dummy implementation of {@link ThingResourceVisitor}.
     */
    private static class TestVisitor implements ThingResourceVisitor<ThingId, ThingResource> {

        @Override
        public ThingResource visitThing(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.THING;
        }

        @Override
        public ThingResource visitThingDefinition(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.DEFINITION;
        }

        @Override
        public ThingResource visitPolicyId(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.POLICY_ID;
        }

        @Override
        public ThingResource visitAttributes(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.ATTRIBUTES;
        }

        @Override
        public ThingResource visitAttribute(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.ATTRIBUTE;
        }

        @Override
        public ThingResource visitFeature(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURE;
        }

        @Override
        public ThingResource visitFeatures(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURES;
        }

        @Override
        public ThingResource visitFeatureDefinition(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURE_DEFINITION;
        }

        @Override
        public ThingResource visitFeatureProperties(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURE_PROPERTIES;
        }

        @Override
        public ThingResource visitFeatureProperty(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURE_PROPERTY;
        }

        @Override
        public ThingResource visitFeatureDesiredProperties(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURE_DESIRED_PROPERTIES;
        }

        @Override
        public ThingResource visitFeatureDesiredProperty(final JsonPointer path, @Nullable final ThingId param) {
            assertThat((Object) param).isSameAs(THING_ID);
            return ThingResource.FEATURE_DESIRED_PROPERTY;
        }

        @Override
        public DittoRuntimeException getUnknownPathException(final JsonPointer path) {
            return PathUnknownException.newBuilder(path).build();
        }
    }
}
