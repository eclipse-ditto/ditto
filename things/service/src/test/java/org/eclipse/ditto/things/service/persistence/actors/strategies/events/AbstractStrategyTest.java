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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import java.time.Instant;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Abstract base implementation for strategy unit tests.
 */
abstract class AbstractStrategyTest {

    /**
     * Thing identifier for testing.
     */
    static final ThingId THING_ID = ThingId.of("org.example", "myThing");

    /**
     * A Thing for testing.
     */
    static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .build();

    /**
     * An attribute pointer for testing.
     */
    static final JsonPointer ATTRIBUTE_POINTER = JsonPointer.of("bumlux");

    /**
     * An attribute value for testing.
     */
    static final JsonValue ATTRIBUTE_VALUE = JsonValue.of(42);

    /**
     * Attributes for testing.
     */
    static final Attributes ATTRIBUTES = Attributes.newBuilder().set(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE).build();

    /**
     * A Thing Definition for testing.
     */
    static final ThingDefinition THING_DEFINITION = ThingsModelFactory.newDefinition("example:test" +
            ":definition");

    /**
     * A Thing Definition for testing.
     */
    static final PolicyId POLICY_ID = PolicyId.of("example.com:testPolicy");

    /**
     * A Feature identifier for testing.
     */
    static final String FEATURE_ID = "flux-capacitor";

    /**
     * A Feature Definition identifier for testing.
     */
    static final String FEATURE_DEFINITION_ID = "org.example:capacitor:42";

    /**
     * A Feature Definition for testing.
     */
    static final FeatureDefinition FEATURE_DEFINITION = FeatureDefinition.fromIdentifier(FEATURE_DEFINITION_ID);

    /**
     * A Feature for testing.
     */
    static final Feature FEATURE = Feature.newBuilder().withId(FEATURE_ID).build();

    /**
     * Features for testing.
     */
    static final Features FEATURES = Features.newBuilder().set(FEATURE).build();

    /**
     * An feature property pointer for testing.
     */
    static final JsonPointer FEATURE_PROPERTY_POINTER = JsonPointer.of("bumlux");

    /**
     * An feature desired property pointer for testing.
     */
    static final JsonPointer FEATURE_DESIRED_PROPERTY_POINTER = JsonPointer.of("luxbum");

    /**
     * An feature property value for testing.
     */
    static final JsonValue FEATURE_PROPERTY_VALUE = JsonValue.of(42);

    /**
     * An feature desired property value for testing.
     */
    static final JsonValue FEATURE_DESIRED_PROPERTY_VALUE = JsonValue.of(24);

    /**
     * Feature properties for testing.
     */
    static final FeatureProperties FEATURE_PROPERTIES = FeatureProperties.newBuilder()
            .set(FEATURE_PROPERTY_POINTER, FEATURE_PROPERTY_VALUE)
            .build();

    /**
     * Feature desired properties for testing.
     */
    static final FeatureProperties FEATURE_DESIRED_PROPERTIES = FeatureProperties.newBuilder()
            .set(FEATURE_DESIRED_PROPERTY_POINTER, FEATURE_DESIRED_PROPERTY_VALUE)
            .build();

    /**
     * Revision of a Thing.
     */
    static final long REVISION = 0;

    static final Instant TIMESTAMP = Instant.EPOCH;

    static final Metadata METADATA = Metadata.newBuilder()
            .set("creator", "The epic Ditto team")
            .build();

    /**
     * Incremented revision of a Thing.
     */
    static final long NEXT_REVISION = 1;

}
