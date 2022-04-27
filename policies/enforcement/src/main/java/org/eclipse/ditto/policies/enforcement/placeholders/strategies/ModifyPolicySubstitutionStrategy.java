/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;

/**
 * Handles substitution for {@link org.eclipse.ditto.policies.model.SubjectId}
 * inside a {@link ModifyPolicy} command.
 */
final class ModifyPolicySubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyPolicy> {

    ModifyPolicySubstitutionStrategy() {
        super(ModifyPolicy.class);
    }

    @Override
    public DittoHeadersSettable<?> apply(final ModifyPolicy modifyPolicy,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifyPolicy);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifyPolicy.getDittoHeaders();
        final Policy existingPolicy = modifyPolicy.getPolicy();
        final Policy substitutedPolicy =
                substitutePolicy(existingPolicy, substitutionAlgorithm, dittoHeaders);

        if (existingPolicy.equals(substitutedPolicy)) {
            return modifyPolicy;
        } else {
            return ModifyPolicy.of(modifyPolicy.getEntityId(), substitutedPolicy, dittoHeaders);
        }
    }

}
