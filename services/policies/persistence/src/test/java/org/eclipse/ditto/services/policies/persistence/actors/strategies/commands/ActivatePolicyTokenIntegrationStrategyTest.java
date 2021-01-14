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
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegrationResponse;
import org.eclipse.ditto.signals.events.policies.SubjectsModifiedPartially;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link ActivatePolicyTokenIntegrationStrategy}.
 */
public final class ActivatePolicyTokenIntegrationStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ActivatePolicyTokenIntegrationStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ActivatePolicyTokenIntegrationStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")),
                ActorSystem.create("test"));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivatePolicyTokenIntegrationStrategy.class, areImmutable());
    }

    @Test
    public void activateTokenIntegration() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectsModifiedPartially.class,
                event -> {
                    assertThat(event.getModifiedSubjects()).containsOnlyKeys(LABEL);
                    assertThat(event.getModifiedSubjects().get(LABEL).getId()).isEqualTo(expectedSubjectId);
                    assertThat(event.getModifiedSubjects().get(LABEL).getExpiry()).isNotEmpty();
                },
                ActivatePolicyTokenIntegrationResponse.class,
                response -> assertThat(response)
                        .isEqualTo(
                                ActivatePolicyTokenIntegrationResponse.of(context.getState(), dittoHeaders)));
    }

    @Test
    public void activateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void activateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{fn:delete()}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{fn:delete()}}").build());
    }

    @Test
    public void activateTokenIntegrationWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(LABEL), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

    @Test
    public void rejectEmptyLabels() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                underTest.getNotApplicableException());
    }

    @Test
    public void rejectNonexistentLabel() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label nonexistentLabel = Label.of("nonexistent-label");
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(nonexistentLabel),
                        dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                underTest.getNotApplicableException());
    }

    @Test
    public void rejectEntryWithoutThingReadPermission() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Label label = Label.of("empty-entry");
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("integration:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivatePolicyTokenIntegration command =
                ActivatePolicyTokenIntegration.of(context.getState(), subjectId, expiry, List.of(label), dittoHeaders);
        final Policy policy = TestConstants.Policy.POLICY.toBuilder()
                .forLabel(label)
                .setSubject(TestConstants.Policy.SUPPORT_SUBJECT)
                .setGrantedPermissions(ResourceKey.newInstance("policy:/"), Permission.READ)
                .build();
        assertErrorResult(underTest, policy, command, underTest.getNotApplicableException());
    }
}
