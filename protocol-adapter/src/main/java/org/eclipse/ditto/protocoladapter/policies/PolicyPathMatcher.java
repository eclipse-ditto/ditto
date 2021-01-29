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
package org.eclipse.ditto.protocoladapter.policies;

import java.util.EnumMap;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.PayloadPathMatcher;
import org.eclipse.ditto.protocoladapter.UnknownPathException;
import org.eclipse.ditto.signals.commands.policies.PolicyResource;

/**
 * PayloadPathMatcher implementation that handles policy resources.
 * <p>
 * TODO adapt @since annotation @since 1.6.0
 */
final class PolicyPathMatcher implements PayloadPathMatcher {

    /**
     * This mapping is used in the method {@code AbstractAdapter#getType} to determine the second part of the command
     * name (e.g. {@code policy} of {@value org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy#NAME}),
     * i.e. the name of the resource that is affected by the command, from a given path.
     */
    private static final EnumMap<PolicyResource, String> resourceNames = new EnumMap<>(PolicyResource.class);

    static {
        resourceNames.put(PolicyResource.POLICY, "policy");
        resourceNames.put(PolicyResource.POLICY_ENTRIES, "policyEntries");
        resourceNames.put(PolicyResource.POLICY_ENTRY, "policyEntry");
        resourceNames.put(PolicyResource.POLICY_ENTRY_RESOURCES, "resources");
        resourceNames.put(PolicyResource.POLICY_ENTRY_RESOURCE, "resource");
        resourceNames.put(PolicyResource.POLICY_ENTRY_SUBJECTS, "subjects");
    }

    private static final PolicyPathMatcher INSTANCE = new PolicyPathMatcher();

    private PolicyPathMatcher() {
    }

    static PolicyPathMatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public String match(final JsonPointer path) {
        final PolicyResource resource =
                PolicyResource.from(path).orElseThrow(() -> UnknownPathException.newBuilder(path).build());
        return Optional.ofNullable(resourceNames.get(resource))
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }
}
