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

import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.LABEL;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegrationResponse;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link DeactivateTokenIntegrationStrategy}.
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
                DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId), dittoHeaders);
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
                DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId), dittoHeaders);
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
                command = DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void deactivateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{fn:delete()}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration
                command = DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{fn:delete()}}").build());
    }

    @Test
    public void deactivateTokenIntegrationWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = buildActivateTokenIntegrationHeaders();
        final DeactivateTokenIntegration
                command = DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }

    @Test
    public void deactivateTokenIntegrationWithoutAuthContextContainingPolicyEntrySubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final DeactivateTokenIntegration command =
                DeactivateTokenIntegration.of(context.getState(), LABEL, Collections.singleton(subjectId), dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                underTest.getNotApplicableException(dittoHeaders));
    }

}
