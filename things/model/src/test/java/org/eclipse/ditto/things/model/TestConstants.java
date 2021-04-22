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
package org.eclipse.ditto.things.model;


import java.time.Instant;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Authorization-related test constants.
     */
    public static final class Authorization {

        /**
         * A known Authorization Subject for testing.
         */
        public static final AuthorizationSubject AUTH_SUBJECT_OLDMAN =
                AuthorizationModelFactory.newAuthSubject("JohnOldman");

        /**
         * Another known AuthorizationSubject for testing.
         */
        public static final AuthorizationSubject AUTH_SUBJECT_GRIMES =
                AuthorizationModelFactory.newAuthSubject("FrankGrimes");

        private Authorization() {
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
         * Properties of a known Feature.
         */
        public static final FeatureProperties FLUX_CAPACITOR_PROPERTIES = FeatureProperties.newBuilder()
                .set("target_year_1", 1955)
                .set("target_year_2", 2015)
                .set("target_year_3", 1885)
                .build();

        /**
         * a known FeatureDefinition for a Flux Capacitor.
         */
        public static final FeatureDefinition FLUX_CAPACITOR_DEFINITION =
                FeatureDefinition.fromIdentifier("com.example:fluxcapacitor:42.0.0");

        /**
         * A known Feature which is required for time travel.
         */
        public static final org.eclipse.ditto.things.model.Feature FLUX_CAPACITOR =
                org.eclipse.ditto.things.model.Feature.newBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .desiredProperties(FLUX_CAPACITOR_PROPERTIES)
                        .definition(FLUX_CAPACITOR_DEFINITION)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();

        /**
         * Known features of a Thing for API v2.
         */
        public static final Features FEATURES = ThingsModelFactory.newFeatures(Feature.FLUX_CAPACITOR);

        private Feature() {
            throw new AssertionError();
        }
    }

    /**
     * Thing-related test constants.
     */
    public static final class Thing {

        /**
         * A known namespace for testing.
         */
        public static final String NAMESPACE = "example.com";

        /**
         * A known Thing ID for testing.
         */
        public static final ThingId THING_ID = ThingId.of("example.com", "testThing");

        /**
         * A known Policy ID for testing.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("example.com:testPolicy");

        /**
         * A known lifecycle of a Thing.
         */
        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        /**
         * A known location attribute for testing.
         */
        public static final JsonObject LOCATION_ATTRIBUTE = JsonFactory.newObjectBuilder()
                .set("latitude", 44.673856)
                .set("longitude", 8.261719)
                .build();

        /**
         * Known attributes of a Thing.
         */
        public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
                .set("location", LOCATION_ATTRIBUTE)
                .set("maker", "Bosch")
                .build();


        /**
         * A known Definition for testing.
         */
        public static final ThingDefinition DEFINITION =
                ImmutableThingDefinition.ofParsed("Namespace.test.version:thing:1.0");

        /**
         * A known revision number of a Thing.
         */
        public static final long REVISION_NUMBER = 0;

        /**
         * A known revision of a Thing.
         */
        public static final ThingRevision REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);

        public static final Instant MODIFIED = Instant.EPOCH;

        public static final Instant CREATED = Instant.EPOCH;

        /**
         * A known Thing for testing in V2.
         */
        public static final org.eclipse.ditto.things.model.Thing THING_V2 = ThingsModelFactory.newThingBuilder()
                .setAttributes(ATTRIBUTES)
                .setDefinition(DEFINITION)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setPolicyId(POLICY_ID)
                .setId(THING_ID)
                .setRevision(REVISION_NUMBER)
                .setModified(MODIFIED)
                .setCreated(CREATED)
                .build();

        private Thing() {
            throw new AssertionError();
        }
    }

    /**
     * Metadata-related test constants.
     */
    public static final class Metadata {

        private Metadata() {
            throw new AssertionError();
        }

        /**
         * Known Metadata of a Thing.
         */
        public static final org.eclipse.ditto.base.model.entity.metadata.Metadata METADATA =
                ThingsModelFactory.newMetadataBuilder()
                        .set("issuedAt", 0L)
                        .build();
    }

}
