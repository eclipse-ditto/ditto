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

import java.time.Duration;
import java.time.Instant;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubject;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubjectResponse;
import org.eclipse.ditto.signals.events.policies.SubjectActivated;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ActivateSubjectStrategy}.
 */
public final class ActivateSubjectStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ActivateSubjectStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ActivateSubjectStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivateSubjectStrategy.class, areImmutable());
    }

    @Test
    public void activateSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId =
                SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{policy-entry:label}}:this-is-me");
        final SubjectId expectedSubjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, LABEL + ":this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubject command =
                ActivateSubject.of(context.getState(), LABEL, subjectId, expiry, dittoHeaders);
        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectActivated.class,
                ActivateSubjectResponse.of(context.getState(), LABEL, expectedSubjectId, dittoHeaders));
    }

    @Test
    public void activateInvalidSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubject command = ActivateSubject.of(context.getState(), LABEL, subjectId, expiry, dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectIdInvalidException.newBuilder(LABEL).build());
    }

    @Test
    public void activateUnresolvableSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance(SubjectIssuer.INTEGRATION, "{{fn:delete()}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubject command = ActivateSubject.of(context.getState(), LABEL, subjectId, expiry, dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("integration:{{fn:delete()}}").build());
    }

    @Test
    public void activateSubjectWithUnsupportedPlaceholder() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Instant expiry = Instant.now().plus(Duration.ofDays(1L));
        final SubjectId subjectId = SubjectId.newInstance("{{request:subjectId}}");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ActivateSubject command = ActivateSubject.of(context.getState(), LABEL, subjectId, expiry, dittoHeaders);
        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                UnresolvedPlaceholderException.newBuilder("{{request:subjectId}}").build());
    }
}
