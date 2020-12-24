/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.LABEL;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyActionFailedException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubjects;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubjectsResponse;
import org.eclipse.ditto.signals.events.policies.SubjectsActivated;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ActivateSubjectsStrategy}.
 */
public final class ActivateSubjectsStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ActivateSubjectsStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ActivateSubjectsStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivateSubjectsStrategy.class, areImmutable());
    }

    @Test
    public void activateSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubjects command =
                ActivateSubjects.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectsActivated.class,
                event -> {
                    assertThat(event.getActivatedSubjects()).containsOnlyKeys(LABEL);
                    assertThat(event.getActivatedSubjects().get(LABEL).getId()).isEqualTo(expectedSubjectId);
                    assertThat(event.getActivatedSubjects().get(LABEL).getExpiry()).isNotEmpty();
                },
                ActivateSubjectsResponse.class,
                response -> assertThat(response)
                        .isEqualTo(ActivateSubjectsResponse.of(context.getState(), subjectId, dittoHeaders)));
    }

    @Test
    public void activateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubjects command =
                ActivateSubjects.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void activateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{fn:delete()}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubjects command =
                ActivateSubjects.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{fn:delete()}}").build());
    }

    @Test
    public void activateSubjectWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubjects command =
                ActivateSubjects.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

    @Test
    public void rejectEmptyLabels() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubjects command =
                ActivateSubjects.of(context.getState(), subjectId, expiry, List.of(), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyActionFailedException.newBuilder().build());
    }

    @Test
    public void rejectNonexistentLabel() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label nonexistentLabel = Label.of("nonexistent-label");
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubjects command =
                ActivateSubjects.of(context.getState(), subjectId, expiry, List.of(nonexistentLabel), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyActionFailedException.newBuilder().build());
    }
}
