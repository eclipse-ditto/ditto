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
package org.eclipse.ditto.thingsearch.service.persistence;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    public static ThingId thingId(final String namespace, final String idWithoutNamespace) {
        return ThingId.of(namespace, idWithoutNamespace);
    }

    /**
     * Feature-related test constants.
     */
    public static final class Feature {

        public static final String FLUX_CAPACITOR_ID = "FluxCapacitor";

        public static final FeatureProperties FLUX_CAPACITOR_PROPERTIES =
                ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("target_year_1", 1955)
                        .set("target_year_2", 2015)
                        .set("target_year_3", 1885)
                        .build();

        public static final org.eclipse.ditto.things.model.Feature FLUX_CAPACITOR =
                ThingsModelFactory.newFeatureBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();


        public static final Features FEATURES = ThingsModelFactory.newFeatures(FLUX_CAPACITOR);

        private Feature() {
            throw new AssertionError();
        }
    }

    /**
     * Thing-related test constants.
     */
    public static final class Thing {

        public static final String NAMESPACE = "example.com.things";

        public static final ThingId THING_ID = ThingId.of(NAMESPACE, "testThing");

        public static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, "testPolicy");

        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        private static final String MANUFACTURER_ATTRIBUTE_KEY = "manufacturer";

        public static final JsonObject LOCATION_ATTRIBUTE = JsonFactory.newObjectBuilder()
                .set("latitude", 44.673856)
                .set("longitude", 8.261719)
                .build();

        public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
                .set("location", LOCATION_ATTRIBUTE)
                .set(MANUFACTURER_ATTRIBUTE_KEY, "Bosch")
                .build();

        /**
         * A known V2 Thing for testing.
         */
        public static final org.eclipse.ditto.things.model.Thing THING = ThingsModelFactory.newThingBuilder()
                .setPolicyId(POLICY_ID)
                .setAttributes(ATTRIBUTES)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setId(THING_ID)
                .build();

        private Thing() {
            throw new AssertionError();
        }
    }

}
