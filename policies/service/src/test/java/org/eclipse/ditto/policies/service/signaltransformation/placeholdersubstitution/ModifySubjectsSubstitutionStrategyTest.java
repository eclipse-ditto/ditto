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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.junit.Test;

/**
 * Tests {@link ModifySubjectsSubstitutionStrategy}.
 */
public class ModifySubjectsSubstitutionStrategyTest extends AbstractPolicySubstitutionStrategyTestBase {

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final ModifySubjects commandWithoutPlaceholders = ModifySubjects.of(
                POLICY_ID,
                Label.of(LABEL), Subjects.newInstance(Subject.newInstance(
                        SUBJECT_ID, SubjectType.GENERATED)),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final Subjects subjectsWithPlaceholders =
                Subjects.newInstance(Subject.newInstance(
                                SUBJECT_ID_PLACEHOLDER + ":a", SubjectType.GENERATED),
                        Subject.newInstance(SUBJECT_ID_PLACEHOLDER + ":b", SubjectType.GENERATED));
        final ModifySubjects commandWithPlaceholders = ModifySubjects.of(POLICY_ID,
                Label.of(LABEL),
                subjectsWithPlaceholders,
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final Subjects expectedSubjectsReplaced =
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID + ":a", SubjectType.GENERATED),
                        Subject.newInstance(SUBJECT_ID + ":b", SubjectType.GENERATED));
        final ModifySubjects expectedCommandReplaced = ModifySubjects.of(commandWithPlaceholders.getEntityId(),
                commandWithPlaceholders.getLabel(), expectedSubjectsReplaced,
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
