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
package org.eclipse.ditto.model.thingsearch;


import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;

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
    public static final class SearchAuthorization {

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

        /**
         * The known ACL entry of John Oldman.
         */
        public static final AclEntry ACL_ENTRY_OLDMAN =
                ThingsModelFactory.newAclEntry(SearchAuthorization.AUTH_SUBJECT_OLDMAN, Permission.READ,
                        Permission.WRITE, Permission.ADMINISTRATE);

        /**
         * The known ACL entry of Frank Grimes.
         */
        public static final AclEntry ACL_ENTRY_GRIMES =
                ThingsModelFactory.newAclEntry(SearchAuthorization.AUTH_SUBJECT_GRIMES, Permission.READ);

        private SearchAuthorization() {
            throw new AssertionError();
        }
    }

    /**
     * Feature-related test constants.
     */
    public static final class SearchFeature {

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
         * A known Feature which is required for time travel.
         */
        public static final org.eclipse.ditto.model.things.Feature FLUX_CAPACITOR =
                org.eclipse.ditto.model.things.Feature.newBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();

        /**
         * Known features of a Thing.
         */
        public static final Features FEATURES = ThingsModelFactory.newFeatures(SearchFeature.FLUX_CAPACITOR);

        private SearchFeature() {
            throw new AssertionError();
        }
    }

    /**
     * Thing-related test constants.
     */
    public static final class SearchThing {

        /**
         * A known Thing ID for testing.
         */
        public static final String THING_ID = "example.com:testThing";

        /**
         * A known lifecycle of a Thing.
         */
        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        /**
         * A known Access Control List of a Thing.
         */
        public static final AccessControlList ACL =
                ThingsModelFactory.newAcl(SearchAuthorization.ACL_ENTRY_OLDMAN, SearchAuthorization.ACL_ENTRY_GRIMES);

        /**
         * A known path to the attribute with the key "manufacturer" within a Thing.
         */
        public static final JsonPointer MANUFACTURER_PATH = JsonFactory.newPointer("attributes/manufacturer");

        /**
         * A known location attribute for testing.
         */
        public static final JsonObject LOCATION_ATTRIBUTE = JsonFactory.newObjectBuilder() //
                .set("latitude", 44.673856) //
                .set("longitude", 8.261719) //
                .build();

        /**
         * Known attributes of a Thing.
         */
        public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder() //
                .set("location", LOCATION_ATTRIBUTE) //
                .set(MANUFACTURER_PATH.getLeaf().get(), "Bosch") //
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
        public static final org.eclipse.ditto.model.things.Thing THING = ThingsModelFactory.newThingBuilder() //
                .setAttributes(ATTRIBUTES) //
                .setFeatures(SearchFeature.FEATURES) //
                .setLifecycle(LIFECYCLE) //
                .setPermissions(ACL) //
                .setId(THING_ID) //
                .build();

        private SearchThing() {
            throw new AssertionError();
        }
    }

}
