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
            return ModifySubjects.of(modifySubjects.getEntityId(), modifySubjects.getLabel(), substitutedSubjects,
                    dittoHeaders);
        }
    }

}
