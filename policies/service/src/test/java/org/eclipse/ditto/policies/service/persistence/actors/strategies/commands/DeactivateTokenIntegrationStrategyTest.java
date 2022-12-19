/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.LABEL;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIdInvalidException;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegrationResponse;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.DeactivateTokenIntegrationStrategy}.
 */
public final class DeactivateTokenIntegrationStrategyTest extends AbstractPolicyCommandStrategyTest {

    private DeactivateTokenIntegrationStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeactivateTokenIntegrationStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")),
                ActorSystem.create("test"));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeactivateTokenIntegrationStrategy.class, areImmutable());
    }

    @Test
    public void deactivateTokenIntegration() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration command =
                DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId),
                        dittoHeaders);
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectDeleted.class,
                DeactivateTokenIntegrationResponse.of(context.getState(), LABEL, dittoHeaders));
    }

    @Test
    public void deactivatePermanentSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration command =
                DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId),
                        dittoHeaders);
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectDeleted.class,
                DeactivateTokenIntegrationResponse.of(context.getState(), LABEL, dittoHeaders));
    }

    @Test
    public void deactivateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration
                command = DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId),
                dittoHeaders);
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void deactivateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{ policy:id }}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration
                command = DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId),
                dittoHeaders);
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{ policy:id }}").build());
    }

    @Test
    public void deactivateTokenIntegrationWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration
                command = DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId),
                dittoHeaders);
        assertExceptionIsThrown(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

    @Test
    public void deactivateTokenIntegrationWithoutAuthContextContainingPolicyEntrySubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivateTokenIntegration command =
                DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId),
                        dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                command.getNotApplicableException(dittoHeaders));
    }

}
