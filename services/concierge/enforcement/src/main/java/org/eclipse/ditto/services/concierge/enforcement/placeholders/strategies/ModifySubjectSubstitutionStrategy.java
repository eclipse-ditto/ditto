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

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;

/**
 * Handles substitution for {@link org.eclipse.ditto.model.policies.SubjectId} inside a {@link ModifySubject} command.
 */
final class ModifySubjectSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifySubject> {

    ModifySubjectSubstitutionStrategy() {
        super(ModifySubject.class);
    }

    @Override
    public WithDittoHeaders apply(final ModifySubject modifySubject,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifySubject);
        requireNonNull(substitutionAlgorithm);

        final String subjectId = modifySubject.getSubject().getId().toString();
        final String substitutedSubjectId = substitutionAlgorithm.substitute(subjectId, modifySubject);

        if (subjectId.equals(substitutedSubjectId)) {
            return modifySubject;
        } else {
            final Subject newSubject =
                    Subject.newInstance(substitutedSubjectId, modifySubject.getSubject().getType());
            return ModifySubject.of(modifySubject.getId(), modifySubject.getLabel(), newSubject,
                    modifySubject.getDittoHeaders());
        }
    }
}
