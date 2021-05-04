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

import org.eclipse.ditto.json.JsonPointer;

/**
 * Defines test cases for thing resources.
 */
enum ThingResourceTestCase {
    THING("", ThingResource.THING),
    POLICY_ID("policyId", ThingResource.POLICY_ID),
    ATTRIBUTES("attributes", ThingResource.ATTRIBUTES),
    ATTRIBUTE1("attributes/a", ThingResource.ATTRIBUTE),
    ATTRIBUTE2("attributes/a/b/c", ThingResource.ATTRIBUTE),
    FEATURES("features", ThingResource.FEATURES),
    FEATURE("features/id", ThingResource.FEATURE),
    DEFINITION("definition", ThingResource.DEFINITION),
    FEATURE_DEFINITION("features/id/definition", ThingResource.FEATURE_DEFINITION),
    FEATURE_PROPERTIES("features/id/properties", ThingResource.FEATURE_PROPERTIES),
    FEATURE_PROPERTY("features/id/properties/status/temperature", ThingResource.FEATURE_PROPERTY),
    FEATURE_DESIRED_PROPERTIES("features/id/desiredProperties", ThingResource.FEATURE_DESIRED_PROPERTIES),
    FEATURE_DESIRED_PROPERTY("features/id/desiredProperties/status/temperature",
            ThingResource.FEATURE_DESIRED_PROPERTY);

    private final JsonPointer path;
    private final ThingResource expectedResource;

    ThingResourceTestCase(final String path, final ThingResource expectedResource) {
        this.path = JsonPointer.of(path);
        this.expectedResource = expectedResource;
    }

    JsonPointer getPath() {
        return path;
    }

    ThingResource getExpectedResource() {
        return expectedResource;
    }

    @Override
    public String toString() {
        return "given path: " + path + " -> expected resource: " + expectedResource;
    }
}
