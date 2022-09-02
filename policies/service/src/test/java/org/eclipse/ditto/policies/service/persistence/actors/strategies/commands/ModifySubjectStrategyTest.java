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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectExpiryInvalidException;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectResponse;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.ModifySubjectStrategy}.
 */
public final class ModifySubjectStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ModifySubjectStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifySubjectStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifySubjectStrategy.class, areImmutable());
    }

    @Test
    public void modifySubjectOfPolicyCreatingNewSubject() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();

        final Subject subject = Subject.newInstance(SubjectIssuer.INTEGRATION, "this-is-me");
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ModifySubject command =
                ModifySubject.of(context.getState(), TestConstants.Policy.LABEL, subject, dittoHeaders);


        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectCreated.class,
                ModifySubjectResponse.created(context.getState(), TestConstants.Policy.LABEL, subject,
                        appendETagToDittoHeaders(subject, dittoHeaders)));
    }

    @Test
    public void modifySubjectOfPolicyWithExpiryTimestamp() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();

        final LocalDateTime localDateTime = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(52);
        final Instant expiry = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        final Instant expectedAdjustedExpiry = localDateTime
                .truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1)
                .atZone(ZoneId.systemDefault()).toInstant();

        final Subject subject = Subject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION, "this-is-me"),
                TestConstants.Policy.SUBJECT_TYPE, SubjectExpiry.newInstance(expiry));
        final Subject expectedSubject = Subject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION, "this-is-me"),
                TestConstants.Policy.SUBJECT_TYPE, SubjectExpiry.newInstance(expectedAdjustedExpiry));

        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ModifySubject command =
                ModifySubject.of(context.getState(), TestConstants.Policy.LABEL, subject, dittoHeaders);

        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectCreated.class,
                ModifySubjectResponse.created(context.getState(), TestConstants.Policy.LABEL, expectedSubject,
                        appendETagToDittoHeaders(expectedSubject, dittoHeaders)));
    }

    @Test
    public void modifySubjectOfPolicyWithExpiryTimestampInThePast() {
        final CommandStrategy.Context<PolicyId> context = getDefaultContext();

        final var subject = Subject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION, "this-is-me"),
                TestConstants.Policy.SUBJECT_TYPE,
                SubjectExpiry.newInstance(Instant.parse("2020-11-23T15:52:36.123Z")));
        final var dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final var command = ModifySubject.of(context.getState(), TestConstants.Policy.LABEL, subject, dittoHeaders);
        final var expectedRoundedExpirySubjectExpiry = SubjectExpiry.newInstance("2020-11-23T15:52:40Z");

        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectExpiryInvalidException.newBuilderTimestampInThePast(expectedRoundedExpirySubjectExpiry)
                        .description("It must not be in the past, please adjust to a timestamp in the future.")
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
    }

}
