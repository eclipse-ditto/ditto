/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.placeholders;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.SubjectId;

/**
 * Placeholder with prefix policy-entry.
 */
public final class PolicyEntryPlaceholder implements Placeholder<PolicyEntry> {

    private static PolicyEntryPlaceholder INSTANCE = new PolicyEntryPlaceholder();

    private static final String PREFIX = "policy-entry";
    private static final String LABEL = "label";

    /**
     * Resolve a subject ID containing policy-entry placeholders.
     *
     * @param entry the policy entry.
     * @param subjectIdWithPlaceholder the subject ID containing placeholders.
     * @return the subject ID after resolution, or an empty optional if it contains an unresolvable placeholder.
     * @throws org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException if the subject ID contains unsupported placeholders.
     * @throws org.eclipse.ditto.model.policies.SubjectIdInvalidException if the resolved subject ID is invalid.
     */
    public static SubjectId resolveSubjectId(final PolicyEntry entry,
            final SubjectId subjectIdWithPlaceholder) {
        return PlaceholderFactory.newExpressionResolver(PlaceholderFactory.newPlaceholderResolver(INSTANCE, entry))
                .resolve(subjectIdWithPlaceholder.toString())
                .toOptional()
                .map(SubjectId::newInstance)
                .orElseThrow(() ->
                        UnresolvedPlaceholderException.newBuilder(subjectIdWithPlaceholder.toString()).build());
    }

    @Override
    public Optional<String> resolve(final PolicyEntry policyEntry, final String name) {
        return supports(name) ? Optional.of(policyEntry.getLabel().toString()) : Optional.empty();
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return List.of(LABEL);
    }

    @Override
    public boolean supports(final String name) {
        return LABEL.equals(name);
    }
}
