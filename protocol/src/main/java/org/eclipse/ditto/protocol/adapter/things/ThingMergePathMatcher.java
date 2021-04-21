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
package org.eclipse.ditto.protocol.adapter.things;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.UnknownPathException;
import org.eclipse.ditto.things.model.signals.commands.ThingResource;

/**
 * PathMatcher implementation for merge thing commands.
 * <p>
 * @since 2.0.0
 */
final class ThingMergePathMatcher implements PayloadPathMatcher {

    /**
     * Merge supports only a subset of resources e.g. no inlined policy.
     */
    private static final Map<ThingResource, String> MERGE_RESOURCE_NAMES = ThingResourceNames.get(
            ThingResource.THING,
            ThingResource.POLICY_ID,
            ThingResource.DEFINITION,
            ThingResource.ATTRIBUTES,
            ThingResource.ATTRIBUTE,
            ThingResource.FEATURES,
            ThingResource.FEATURE,
            ThingResource.FEATURE_DEFINITION,
            ThingResource.FEATURE_PROPERTIES,
            ThingResource.FEATURE_PROPERTY,
            ThingResource.FEATURE_DESIRED_PROPERTIES,
            ThingResource.FEATURE_DESIRED_PROPERTY
    );

    private static final ThingMergePathMatcher INSTANCE = new ThingMergePathMatcher();

    private ThingMergePathMatcher() {
    }

    static ThingMergePathMatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public String match(final JsonPointer path) {
        final ThingResource resource =
                ThingResource.from(path).orElseThrow(() -> UnknownPathException.newBuilder(path).build());
        return Optional.ofNullable(MERGE_RESOURCE_NAMES.get(resource))
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }
}
