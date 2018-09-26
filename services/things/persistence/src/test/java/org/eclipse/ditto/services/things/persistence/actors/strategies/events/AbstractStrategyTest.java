/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;

/**
 * Abstract base implementation for strategy unit tests.
 */
abstract class AbstractStrategyTest {

    /**
     * Thing identifier for testing.
     */
    static final String THING_ID = "org.example:myThing";

    /**
     * A Thing for testing.
     */
    static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .build();

    /**
     * An authorization subject for testing.
     */
    static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance("ditto:subject");

    /**
     * An ACL entry for testing.
     */
    static final AclEntry ACL_ENTRY = AclEntry.newInstance(AUTHORIZATION_SUBJECT,
            Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);

    /**
     * An ACL for testing.
     */
    static final AccessControlList ACL = AccessControlList.newBuilder().set(ACL_ENTRY).build();

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
     * An feature property value for testing.
     */
    static final JsonValue FEATURE_PROPERTY_VALUE = JsonValue.of(42);

    /**
     * Feature properties for testing.
     */
    static final FeatureProperties FEATURE_PROPERTIES = FeatureProperties.newBuilder()
            .set(FEATURE_PROPERTY_POINTER, FEATURE_PROPERTY_VALUE)
            .build();

    /**
     * Revision of a Thing.
     */
    static final long REVISION = 0;

    /**
     * Incremented revision of a Thing.
     */
    static final long NEXT_REVISION = 1;

}
