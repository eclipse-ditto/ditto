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
package org.eclipse.ditto.policies.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import org.eclipse.ditto.base.model.signals.commands.ResourceMap;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;

/**
 * Defines all valid policy resources and provides the method {@link #from(org.eclipse.ditto.json.JsonPointer)} to
 * resolve the policy resource from a given path.
 *
 * @since 2.0.0
 */
public enum PolicyResource {

    POLICY,
    POLICY_IMPORTS,
    POLICY_IMPORT,
    POLICY_IMPORT_ENTRIES,
    POLICY_IMPORT_ENTRIES_ADDITIONS,
    POLICY_IMPORT_ENTRY_ADDITION,
    POLICY_ENTRIES,
    POLICY_ENTRY,
    POLICY_ENTRY_RESOURCES,
    POLICY_ENTRY_RESOURCE,
    POLICY_ENTRY_SUBJECTS,
    POLICY_ENTRY_SUBJECT,
    POLICY_ENTRY_IMPORTABLE,
    POLICY_ENTRY_ALLOWED_IMPORT_ADDITIONS,
    POLICY_ENTRY_NAMESPACES,
    POLICY_SUBJECT_ALIASES,
    POLICY_SUBJECT_ALIAS;

    private static final ResourceMap<PolicyResource> resources;

    static {
        resources = ResourceMap.newBuilder(POLICY)
                .add(Policy.JsonFields.IMPORTS, ResourceMap.newBuilder(POLICY_IMPORTS)
                        .addOne(ResourceMap.newBuilder(POLICY_IMPORT)
                                .add(EffectedImports.JsonFields.ENTRIES, POLICY_IMPORT_ENTRIES)
                                .add(EffectedImports.JsonFields.ENTRIES_ADDITIONS,
                                        ResourceMap.newBuilder(POLICY_IMPORT_ENTRIES_ADDITIONS)
                                                .addOne(POLICY_IMPORT_ENTRY_ADDITION))
                                .end())
                )
                .add(Policy.JsonFields.SUBJECT_ALIASES, ResourceMap.newBuilder(POLICY_SUBJECT_ALIASES)
                        .addOne(POLICY_SUBJECT_ALIAS))
                .add(Policy.JsonFields.ENTRIES, ResourceMap.newBuilder(POLICY_ENTRIES)
                        .addOne(ResourceMap.newBuilder(POLICY_ENTRY)
                                .add(PolicyEntry.JsonFields.RESOURCES,
                                        ResourceMap.newBuilder(POLICY_ENTRY_RESOURCES).addAny(POLICY_ENTRY_RESOURCE))
                                .add(PolicyEntry.JsonFields.SUBJECTS,
                                        ResourceMap.newBuilder(POLICY_ENTRY_SUBJECTS).addAny(POLICY_ENTRY_SUBJECT))
                                .add(PolicyEntry.JsonFields.IMPORTABLE_TYPE, POLICY_ENTRY_IMPORTABLE)
                                .add(PolicyEntry.JsonFields.ALLOWED_IMPORT_ADDITIONS,
                                        POLICY_ENTRY_ALLOWED_IMPORT_ADDITIONS)
                                .add(PolicyEntry.JsonFields.NAMESPACES,
                                        POLICY_ENTRY_NAMESPACES)
                                .end())
                ).end();
    }

    /**
     * Resolve the PolicyResource from a path.
     *
     * @param path the path that should be matched to a resource
     * @return the PolicyResource that matches the given {@code path} or an empty optional if the path could not be
     * matched.
     */
    public static Optional<PolicyResource> from(final JsonPointer path) {
        checkNotNull(path, "path");
        return resources.seek(path.iterator());
    }
}
