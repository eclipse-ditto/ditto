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
package org.eclipse.ditto.model.things;


import java.time.Instant;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

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
        public static final org.eclipse.ditto.model.things.Feature FLUX_CAPACITOR =
                org.eclipse.ditto.model.things.Feature.newBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .definition(FLUX_CAPACITOR_DEFINITION)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();

        /**
         * Known features of a Thing.
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
        public static final String THING_ID = "example.com:testThing";

        /**
         * A known Policy ID for testing.
         */
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
         * A known revision number of a Thing.
         */
        public static final long REVISION_NUMBER = 0;

        /**
         * A known revision of a Thing.
         */
        public static final ThingRevision REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);

        public static final Instant MODIFIED = Instant.EPOCH;

        /**
         * A known Thing for testing in V1.
         */
        public static final org.eclipse.ditto.model.things.Thing THING_V1 = ThingsModelFactory.newThingBuilder()
                .setAttributes(ATTRIBUTES)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setPermissions(ACL)
                .setId(THING_ID)
                .setRevision(REVISION_NUMBER)
                .setModified(MODIFIED)
                .build();

        /**
         * A known Thing for testing in V2.
         */
        public static final org.eclipse.ditto.model.things.Thing THING_V2 = ThingsModelFactory.newThingBuilder()
                .setAttributes(ATTRIBUTES)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setPolicyId(POLICY_ID)
                .setId(THING_ID)
                .setRevision(REVISION_NUMBER)
                .setModified(MODIFIED)
                .build();

        private Thing() {
            throw new AssertionError();
        }
    }

}
