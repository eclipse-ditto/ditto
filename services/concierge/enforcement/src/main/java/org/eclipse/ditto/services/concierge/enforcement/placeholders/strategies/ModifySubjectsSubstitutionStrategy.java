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
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;

/**
 * Handles substitution for {@link org.eclipse.ditto.model.policies.SubjectId}
 * inside a {@link ModifySubjects} command.
 */
final class ModifySubjectsSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifySubjects> {

    ModifySubjectsSubstitutionStrategy() {
        super(ModifySubjects.class);
    }

    @Override
    public WithDittoHeaders apply(final ModifySubjects modifySubjects,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifySubjects);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifySubjects.getDittoHeaders();
        final Subjects existingSubjects = modifySubjects.getSubjects();
        Subjects substitutedSubjects = substituteSubjects(existingSubjects, substitutionAlgorithm, dittoHeaders);

        if (existingSubjects.equals(substitutedSubjects)) {
            return modifySubjects;
        } else {
            return ModifySubjects.of(modifySubjects.getId(), modifySubjects.getLabel(), substitutedSubjects,
                    dittoHeaders);
        }
    }

}
