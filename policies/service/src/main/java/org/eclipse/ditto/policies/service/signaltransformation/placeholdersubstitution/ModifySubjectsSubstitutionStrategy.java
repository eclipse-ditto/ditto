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
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;

/**
 * Handles substitution for {@link org.eclipse.ditto.policies.model.SubjectId}
 * inside a {@link ModifySubjects} command.
 */
final class ModifySubjectsSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifySubjects> {

    ModifySubjectsSubstitutionStrategy() {
        super(ModifySubjects.class);
    }

    @Override
    public ModifySubjects apply(final ModifySubjects signal,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(signal);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final Subjects existingSubjects = signal.getSubjects();
        Subjects substitutedSubjects = substituteSubjects(existingSubjects, substitutionAlgorithm, dittoHeaders);

        if (existingSubjects.equals(substitutedSubjects)) {
            return signal;
        } else {
            return ModifySubjects.of(signal.getEntityId(), signal.getLabel(), substitutedSubjects,
                    dittoHeaders);
        }
    }

}
