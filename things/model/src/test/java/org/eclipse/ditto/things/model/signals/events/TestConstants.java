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
package org.eclipse.ditto.things.model.signals.events;


import java.time.Instant;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Defines constants for testing.
 */
final class TestConstants {

    /**
     * A known correlation id for testing.
     */
    public static final String CORRELATION_ID = "a780b7b5-fdd2-4864-91fc-80df6bb0a636";

    /**
     * Known command headers.
     */
    public static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance("the_subject"),
                    AuthorizationSubject.newInstance("another_subject")))
            .build();

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /**
     * A known timestamp.
     */
    public static final Instant TIMESTAMP = Instant.EPOCH;

    /**
     * A known metadata.
     */
    public static final Metadata METADATA = Metadata.newBuilder()
            .set("creator", "The epic Ditto team")
            .build();

    private TestConstants() {
        throw new AssertionError();
    }


    /**
     * Thing-related test constants.
     */
    public static final class Thing {

        /**
         * A known Thing ID for testing.
         */
        public static final ThingId THING_ID = ThingId.of("example.com", "testThing");

        public static final PolicyId POLICY_ID = PolicyId.of("example.com:testPolicy");

        public static final ThingDefinition DEFINITION = ThingsModelFactory.newDefinition("example:test" +
                ":definition");

        /**
         * A known lifecycle of a Thing.
         */
        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        public static final JsonPointer LOCATION_ATTRIBUTE_POINTER = JsonFactory.newPointer("location");
        public static final JsonPointer ABSOLUTE_LOCATION_ATTRIBUTE_POINTER =
                org.eclipse.ditto.things.model.Thing.JsonFields.ATTRIBUTES.getPointer()
                        .append(LOCATION_ATTRIBUTE_POINTER);
        /**
         * A known location attribute for testing.
         */
        public static final JsonObject LOCATION_ATTRIBUTE_VALUE = JsonFactory.newObjectBuilder()
                .set("latitude", 44.673856)
                .set("longitude", 8.261719)
                .build();

        /**
         * Known attributes of a Thing.
         */
        public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
                .set(LOCATION_ATTRIBUTE_POINTER, LOCATION_ATTRIBUTE_VALUE)
                .set("maker", "Bosch")
                .build();

        /**
         * A known revision number of a Thing.
         */
        public static final long REVISION_NUMBER = 0;

        /**
         * A known revision of a Thing.
         */
        public static final ThingRevision REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);

        /**
         * A known Thing for testing.
         */
        public static final org.eclipse.ditto.things.model.Thing THING = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setDefinition(DEFINITION)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setPolicyId(POLICY_ID)
                .build();

        private Thing() {
            throw new AssertionError();
        }

    }

    /**
     * Feature-related test constants.
     */
    public static final class Feature {

        /**
         * A known ID of a Feature.
         */
        public static final String FLUX_CAPACITOR_ID = "FluxCapacitor";

        /**
         * Pointer of a known Feature Property.
         */
        public static final JsonPointer FLUX_CAPACITOR_PROPERTY_POINTER = JsonFactory.newPointer("target_year_1");

        /**
         * Value of a known Feature Property.
         */
        public static final JsonValue FLUX_CAPACITOR_PROPERTY_VALUE = JsonFactory.newValue(1955);

        /**
         * Properties of a known Feature.
         */
        public static final FeatureDefinition FLUX_CAPACITOR_DEFINITION =
                FeatureDefinition.fromIdentifier("org.eclipse.ditto:fluxcapacitor:1.0.0");

        /**
         * Properties of a known Feature.
         */
        public static final FeatureProperties FLUX_CAPACITOR_PROPERTIES =
                ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("target_year_1", 1955)
                        .set("target_year_2", 2015)
                        .set("target_year_3", 1885)
                        .build();

        /**
         * A known Feature which is required for time travel.
         */
        public static final org.eclipse.ditto.things.model.Feature FLUX_CAPACITOR =
                ThingsModelFactory.newFeatureBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();

        /**
         * Known features of a Thing.
         */
        public static final Features FEATURES = ThingsModelFactory.newFeatures(FLUX_CAPACITOR);


        private Feature() {
            throw new AssertionError();
        }

    }

}
