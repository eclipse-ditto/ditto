/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;

/**
 * Handles substitution for {@link org.eclipse.ditto.model.policies.SubjectId}
 * inside a {@link ModifyPolicyEntry} command.
 */
final class ModifyPolicyEntrySubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyPolicyEntry> {

    ModifyPolicyEntrySubstitutionStrategy() {
        super(ModifyPolicyEntry.class);
    }

    @Override
    public WithDittoHeaders apply(final ModifyPolicyEntry modifyPolicyEntry,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifyPolicyEntry);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifyPolicyEntry.getDittoHeaders();
        final PolicyEntry existingPolicyEntry = modifyPolicyEntry.getPolicyEntry();
        final PolicyEntry resultEntry = substitutePolicyEntry(existingPolicyEntry, substitutionAlgorithm, dittoHeaders);

        if (existingPolicyEntry.equals(resultEntry)) {
            return modifyPolicyEntry;
        } else {
            return ModifyPolicyEntry.of(modifyPolicyEntry.getId(), resultEntry, modifyPolicyEntry.getDittoHeaders());
        }
    }

}
