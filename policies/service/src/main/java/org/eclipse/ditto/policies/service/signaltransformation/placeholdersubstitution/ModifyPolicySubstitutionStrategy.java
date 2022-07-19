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
package org.eclipse.ditto.policies.service.signaltransformation.placeholdersubstitution;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.AbstractTypedSubstitutionStrategy;
import org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.HeaderBasedPlaceholderSubstitutionAlgorithm;
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
    public ModifyPolicy apply(final ModifyPolicy signal,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(signal);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final Policy existingPolicy = signal.getPolicy();
        final Policy substitutedPolicy =
                substitutePolicy(existingPolicy, substitutionAlgorithm, dittoHeaders);

        if (existingPolicy.equals(substitutedPolicy)) {
            return signal;
        } else {
            return ModifyPolicy.of(signal.getEntityId(), substitutedPolicy, dittoHeaders);
        }
    }

}
