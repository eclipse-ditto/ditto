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


import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.base.model.signals.commands.ResourceMap;

/**
 * Defines all valid thing resources and provides a {@link #from(org.eclipse.ditto.json.JsonPointer)} method to
 * resolve the thing resource from a given path.
 *
 * @since 2.0.0
 */
public enum ThingResource {

    THING,
    POLICY_ID,
    POLICY,
    POLICY_ENTRIES,
    POLICY_ENTRY,
    POLICY_ENTRY_SUBJECTS,
    POLICY_ENTRY_SUBJECT,
    POLICY_ENTRY_RESOURCES,
    POLICY_ENTRY_RESOURCE,
    ATTRIBUTES,
    ATTRIBUTE,
    FEATURES,
    FEATURE,
    DEFINITION,
    FEATURE_DEFINITION,
    FEATURE_PROPERTIES,
    FEATURE_PROPERTY,
    FEATURE_DESIRED_PROPERTIES,
    FEATURE_DESIRED_PROPERTY;

    private static final ResourceMap<ThingResource> resources;

    static {
        resources = ResourceMap.newBuilder(THING)
                .add(Thing.JsonFields.POLICY_ID, POLICY_ID)
                .add(Thing.JsonFields.DEFINITION, DEFINITION)
                .add(JsonKey.of(Policy.INLINED_FIELD_NAME),
                        ResourceMap.newBuilder(POLICY)
                                .add(Policy.JsonFields.ENTRIES,
                                        ResourceMap.newBuilder(POLICY_ENTRIES)
                                                .addOne(ResourceMap.newBuilder(POLICY_ENTRY)
                                                        .add(PolicyEntry.JsonFields.SUBJECTS,
                                                                ResourceMap.newBuilder(POLICY_ENTRY_SUBJECTS)
                                                                        .addAny(POLICY_ENTRY_SUBJECT))
                                                        .add(PolicyEntry.JsonFields.RESOURCES,
                                                                ResourceMap.newBuilder(POLICY_ENTRY_RESOURCES)
                                                                        .addAny(POLICY_ENTRY_RESOURCE))
                                                        .end()))
                                .end())
                .add(Thing.JsonFields.ATTRIBUTES,
                        ResourceMap.newBuilder(ATTRIBUTES)
                                .addAny(ATTRIBUTE))
                .add(Thing.JsonFields.FEATURES,
                        ResourceMap.newBuilder(FEATURES).addOne(
                                ResourceMap.newBuilder(FEATURE)
                                        .add(Feature.JsonFields.DEFINITION, FEATURE_DEFINITION)
                                        .add(Feature.JsonFields.PROPERTIES,
                                                ResourceMap.newBuilder(FEATURE_PROPERTIES)
                                                        .addAny(FEATURE_PROPERTY))
                                        .add(Feature.JsonFields.DESIRED_PROPERTIES,
                                                ResourceMap.newBuilder(FEATURE_DESIRED_PROPERTIES)
                                                        .addAny(FEATURE_DESIRED_PROPERTY))
                                        .end()))
                .end();
    }

    /**
     * Resolve the ThingResource from a path.
     *
     * @param path the path that should be matched to a resource
     * @return the ThingResource that matches the given {@code path} or an empty optional if the path could not be
     * matched.
     */
    public static Optional<ThingResource> from(final JsonPointer path) {
        checkNotNull(path, "path");
        return resources.seek(path.iterator());
    }
}
