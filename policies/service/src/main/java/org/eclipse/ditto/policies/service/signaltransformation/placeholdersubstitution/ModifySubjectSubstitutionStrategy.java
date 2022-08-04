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

import org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.AbstractTypedSubstitutionStrategy;
import org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;

/**
 * Handles substitution for {@link org.eclipse.ditto.policies.model.SubjectId} inside a {@link ModifySubject} command.
 */
final class ModifySubjectSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifySubject> {

    ModifySubjectSubstitutionStrategy() {
        super(ModifySubject.class);
    }

    @Override
    public ModifySubject apply(final ModifySubject signal,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(signal);
        requireNonNull(substitutionAlgorithm);

        final String subjectId = signal.getSubject().getId().toString();
        final String substitutedSubjectId = substitutionAlgorithm.substitute(subjectId, signal);

        if (subjectId.equals(substitutedSubjectId)) {
            return signal;
        } else {
            final Subject newSubject =
                    Subject.newInstance(substitutedSubjectId, signal.getSubject().getType());
            return ModifySubject.of(signal.getEntityId(), signal.getLabel(), newSubject,
                    signal.getDittoHeaders());
        }
    }
}
