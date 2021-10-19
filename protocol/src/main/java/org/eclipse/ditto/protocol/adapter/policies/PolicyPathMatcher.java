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
package org.eclipse.ditto.protocol.adapter.policies;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyResource;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.UnknownPathException;

/**
 * PayloadPathMatcher implementation that handles policy resources.
 * <p>
 * @since 2.0.0
 */
final class PolicyPathMatcher implements PayloadPathMatcher {

    /**
     * This mapping is used in the method {@code AbstractAdapter#getType} to determine the second part of the command
     * name (e.g. {@code policy} of {@value org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy#NAME}),
     * i.e. the name of the resource that is affected by the command, from a given path.
     */
    private static final Map<PolicyResource, String> resourceNames = new EnumMap<>(PolicyResource.class);

    static {
        resourceNames.put(PolicyResource.POLICY, "policy");
        resourceNames.put(PolicyResource.POLICY_IMPORTS, "policyImports");
        resourceNames.put(PolicyResource.POLICY_IMPORT, "policyImport");
        resourceNames.put(PolicyResource.POLICY_ENTRIES, "policyEntries");
        resourceNames.put(PolicyResource.POLICY_ENTRY, "policyEntry");
        resourceNames.put(PolicyResource.POLICY_ENTRY_RESOURCES, "resources");
        resourceNames.put(PolicyResource.POLICY_ENTRY_RESOURCE, "resource");
        resourceNames.put(PolicyResource.POLICY_ENTRY_SUBJECTS, "subjects");
        resourceNames.put(PolicyResource.POLICY_ENTRY_SUBJECT, "subject");
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
