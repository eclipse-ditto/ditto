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
package org.eclipse.ditto.concierge.service.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;

/**
 * Handles substitution for {@link org.eclipse.ditto.policies.model.SubjectId}
 * inside a {@link ModifyPolicyEntries} command.
 */
final class ModifyPolicyEntriesSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyPolicyEntries> {

    ModifyPolicyEntriesSubstitutionStrategy() {
        super(ModifyPolicyEntries.class);
    }

    @Override
    public DittoHeadersSettable<?> apply(final ModifyPolicyEntries modifyPolicyEntries,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifyPolicyEntries);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifyPolicyEntries.getDittoHeaders();
        final Iterable<PolicyEntry> existingPolicyEntries = modifyPolicyEntries.getPolicyEntries();
        final Iterable<PolicyEntry> substitutedEntries =
                substitutePolicyEntries(existingPolicyEntries, substitutionAlgorithm, dittoHeaders);

        if (existingPolicyEntries.equals(substitutedEntries)) {
            return modifyPolicyEntries;
        } else {
            return ModifyPolicyEntries.of(modifyPolicyEntries.getEntityId(), substitutedEntries,
                    dittoHeaders);
        }
    }

}
