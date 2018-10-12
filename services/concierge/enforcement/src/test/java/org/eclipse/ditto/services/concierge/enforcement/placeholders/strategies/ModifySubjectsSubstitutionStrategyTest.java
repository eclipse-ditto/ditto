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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;
import org.junit.Test;

/**
 * Tests {@link ModifySubjectsSubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class ModifySubjectsSubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {

    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifySubjectsSubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final ModifySubjects commandWithoutPlaceholders = ModifySubjects.of(POLICY_ID,
                Label.of(LABEL), Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final Subjects subjectsWithPlaceholders =
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID_PLACEHOLDER + ":a", SubjectType.GENERATED),
                        Subject.newInstance(SUBJECT_ID_PLACEHOLDER + ":b", SubjectType.GENERATED));
        final ModifySubjects commandWithPlaceholders = ModifySubjects.of(POLICY_ID,
                Label.of(LABEL),
                subjectsWithPlaceholders,
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final Subjects expectedSubjectsReplaced =
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID + ":a", SubjectType.GENERATED),
                        Subject.newInstance(SUBJECT_ID + ":b", SubjectType.GENERATED));
        final ModifySubjects expectedCommandReplaced = ModifySubjects.of(commandWithPlaceholders.getId(),
                commandWithPlaceholders.getLabel(), expectedSubjectsReplaced,
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
