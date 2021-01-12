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

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivatePolicyTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivatePolicyTokenIntegrationResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.signals.events.policies.SubjectsDeletedPartially;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link DeactivatePolicyTokenIntegrationStrategy}.
 */
public final class DeactivatePolicyTokenIntegrationStrategyTest extends AbstractPolicyCommandStrategyTest {

    private DeactivatePolicyTokenIntegrationStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeactivatePolicyTokenIntegrationStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")),
                ActorSystem.create("test"));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeactivatePolicyTokenIntegrationStrategy.class, areImmutable());
    }

    @Test
    public void deactivateTokenIntegration() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(LABEL), dittoHeaders);
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .setSubjectFor(LABEL, PoliciesModelFactory.newSubject(expectedSubjectId,
                        PoliciesModelFactory.newSubjectType(MessageFormat.format("via action <{0}>",
                                ActivatePolicyTokenIntegration.NAME)),
                        SubjectExpiry.newInstance(Instant.now().plus(Duration.ofDays(1L)))))
                .build();
        assertModificationResult(underTest, policy, command,
                SubjectsDeletedPartially.class,
                event -> {
                    assertThat(event.getDeletedSubjectIds()).containsOnlyKeys(LABEL);
                    assertThat(event.getDeletedSubjectIds().get(LABEL)).isEqualTo(expectedSubjectId);
                },
                DeactivatePolicyTokenIntegrationResponse.class,
                response -> assertThat(response)
                        .isEqualTo(
                                DeactivatePolicyTokenIntegrationResponse.of(context.getState(), subjectId, dittoHeaders)));
    }

    @Test
    public void deactivateNonexistentSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(LABEL), dittoHeaders);
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectsDeletedPartially.class,
                event -> assertThat(event.getDeletedSubjectIds()).isEmpty(),
                DeactivatePolicyTokenIntegrationResponse.class,
                response -> assertThat(response)
                        .isEqualTo(
                                DeactivatePolicyTokenIntegrationResponse.of(context.getState(), subjectId, dittoHeaders)));
    }

    @Test
    public void deactivatePermanentSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(LABEL), dittoHeaders);
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .setSubjectFor(LABEL, PoliciesModelFactory.newSubject(expectedSubjectId,
                        PoliciesModelFactory.newSubjectType(MessageFormat.format("added via action <{0}>",
                                ActivatePolicyTokenIntegration.NAME))))
                .build();
        assertErrorResult(underTest, policy, command,
                PolicyActionFailedException.newBuilderForDeactivatingPermanentSubjects().build());
    }

    @Test
    public void deactivateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void deactivateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{fn:delete()}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{fn:delete()}}").build());
    }

    @Test
    public void deactivateTokenIntegrationWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

    @Test
    public void rejectEmptyLabels() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyActionFailedException.newBuilderForDeactivateTokenIntegration().build());
    }

    @Test
    public void rejectNonexistentLabel() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label nonexistentLabel = Label.of("nonexistent-label");
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivatePolicyTokenIntegration command =
                DeactivatePolicyTokenIntegration.of(context.getState(), subjectId, List.of(nonexistentLabel), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyActionFailedException.newBuilderForDeactivateTokenIntegration().build());
    }
}
