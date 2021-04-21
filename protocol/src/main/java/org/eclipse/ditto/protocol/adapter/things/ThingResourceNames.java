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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.eclipse.ditto.things.model.signals.commands.ThingResource;

/**
 * Defines the mapping from a thing resource to a resource name.
 */
final class ThingResourceNames {

    private ThingResourceNames() {
        // prevent instantiation
    }

    /**
     * This mapping is used in the method {@code AbstractAdapter#getType} to determine the second part of the command
     * name (e.g. {@code thing} of {@value org.eclipse.ditto.things.model.signals.commands.modify.CreateThing#NAME}),
     * i.e. the name of the resource that is affected by the command, from a given path.
     */
    private static final Map<ThingResource, String> resourceNames = new EnumMap<>(ThingResource.class);

    static {
        resourceNames.put(ThingResource.THING, "thing");
        resourceNames.put(ThingResource.POLICY_ID, "policyId");
        resourceNames.put(ThingResource.POLICY, "policy");
        resourceNames.put(ThingResource.POLICY_ENTRIES, "policyEntries");
        resourceNames.put(ThingResource.POLICY_ENTRY, "policyEntry");
        resourceNames.put(ThingResource.POLICY_ENTRY_SUBJECTS, "policyEntrySubjects");
        resourceNames.put(ThingResource.POLICY_ENTRY_SUBJECT, "policyEntrySubject");
        resourceNames.put(ThingResource.POLICY_ENTRY_RESOURCES, "policyEntryResources");
        resourceNames.put(ThingResource.POLICY_ENTRY_RESOURCE, "policyEntryResource");
        resourceNames.put(ThingResource.ATTRIBUTES, "attributes");
        resourceNames.put(ThingResource.ATTRIBUTE, "attribute");
        resourceNames.put(ThingResource.FEATURES, "features");
        resourceNames.put(ThingResource.FEATURE, "feature");
        resourceNames.put(ThingResource.DEFINITION, "definition");
        resourceNames.put(ThingResource.FEATURE_DEFINITION, "featureDefinition");
        resourceNames.put(ThingResource.FEATURE_PROPERTIES, "featureProperties");
        resourceNames.put(ThingResource.FEATURE_PROPERTY, "featureProperty");
        resourceNames.put(ThingResource.FEATURE_DESIRED_PROPERTIES, "featureDesiredProperties");
        resourceNames.put(ThingResource.FEATURE_DESIRED_PROPERTY, "featureDesiredProperty");
    }

    static Map<ThingResource, String> get() {
        return Collections.unmodifiableMap(resourceNames);
    }

    static Map<ThingResource, String> get(final ThingResource... supportedResources) {
        final Map<ThingResource, String> filtered = new EnumMap<>(ThingResource.class);
        for (final ThingResource resource : supportedResources) {
            if (resourceNames.containsKey(resource)) {
                filtered.put(resource, resourceNames.get(resource));
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

}
