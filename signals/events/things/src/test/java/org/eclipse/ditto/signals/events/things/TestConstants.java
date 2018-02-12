/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.things;


import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;

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
            .authorizationSubjects("the_subject", "another_subject").build();

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /**
     * A known timestamp.
     */
    public static final Instant TIMESTAMP = Instant.EPOCH;
    /**
     * Known JSON parse options.
     */
    public static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();
    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES =
            JsonFactory.newFieldSelector("attributes(location,maker)", JSON_PARSE_OPTIONS);
    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES_WITH_THING_ID =
            JsonFactory.newFieldSelector("thingId,attributes(location,maker)", JSON_PARSE_OPTIONS);
    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_FEATURE_PROPERTIES =
            JsonFactory.newFieldSelector("properties/target_year_1", JSON_PARSE_OPTIONS);

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

        /**
         * An Authorization Context which contains all known Authorization Subjects.
         */
        public static final AuthorizationContext AUTH_CONTEXT =
                AuthorizationModelFactory.newAuthContext(AUTH_SUBJECT_OLDMAN, AUTH_SUBJECT_GRIMES);

        public static final List<AuthorizationSubject> authorizationSubjects =
                Arrays.asList(AUTH_SUBJECT_OLDMAN, AUTH_SUBJECT_GRIMES);

        /**
         * The known ACL entry of John Oldman.
         */
        public static final AclEntry ACL_ENTRY_OLDMAN =
                ThingsModelFactory.newAclEntry(Authorization.AUTH_SUBJECT_OLDMAN, Permission.READ, Permission.WRITE,
                        Permission.ADMINISTRATE);

        /**
         * The known ACL entry of Frank Grimes.
         */
        public static final AclEntry ACL_ENTRY_GRIMES =
                ThingsModelFactory.newAclEntry(Authorization.AUTH_SUBJECT_GRIMES, Permission.READ);

        private Authorization() {
            throw new AssertionError();
        }
    }


    /**
     * Thing-related test constants.
     */
    public static final class Thing {

        /**
         * A known Thing ID for testing.
         */
        public static final String THING_ID = "example.com:testThing";

        public static final String POLICY_ID = "example.com:testPolicy";

        /**
         * A known lifecycle of a Thing.
         */
        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        /**
         * A known Access Control List of a Thing.
         */
        public static final AccessControlList ACL =
                ThingsModelFactory.newAcl(Authorization.ACL_ENTRY_OLDMAN, Authorization.ACL_ENTRY_GRIMES);

        public static final JsonPointer LOCATION_ATTRIBUTE_POINTER = JsonFactory.newPointer("location");

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
        public static final org.eclipse.ditto.model.things.Thing THING = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
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
        public static final org.eclipse.ditto.model.things.Feature FLUX_CAPACITOR =
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
