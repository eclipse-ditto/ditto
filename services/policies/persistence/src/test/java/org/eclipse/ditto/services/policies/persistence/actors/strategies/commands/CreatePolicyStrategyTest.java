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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectExpiryInvalidException;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link CreatePolicyStrategy}.
 */
public final class CreatePolicyStrategyTest extends AbstractPolicyCommandStrategyTest {

    private CreatePolicyStrategy underTest;

    @Before
    public void setUp() {
        underTest = new CreatePolicyStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(CreatePolicyStrategy.class, areImmutable());
    }

    @Test
    public void createPolicy() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final Policy policy = TestConstants.Policy.POLICY
                .toBuilder()
                .setRevision(NEXT_REVISION)
                .build();
        final CreatePolicy command =
                CreatePolicy.of(policy, dittoHeaders);

        assertModificationResult(underTest, policy, command,
                PolicyCreated.class,
                event -> {
                    assertThat(event.getPolicy().getEntityId())
                            .contains(TestConstants.Policy.POLICY.getEntityId().get());
                    assertThat(event.getPolicy().getEntriesSet())
                            .containsAll(TestConstants.Policy.POLICY.getEntriesSet());
                },
                CreatePolicyResponse.class,
                response -> {
                    assertThat(response.getPolicyCreated()).isPresent();
                    assertThat(response.getPolicyCreated().get().getEntriesSet())
                            .containsAll(TestConstants.Policy.POLICY.getEntriesSet());
                }
        );
    }

    @Test
    public void createPolicyWithSubjectHavingExpiryTimestamp() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();

        final LocalDateTime localDateTime = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(23);
        final Instant expiry = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance(expiry);
        final Subject subjectWithExpiry = Subject.newInstance(SubjectId.newInstance("foo-issuer:bar-subject"),
                SubjectType.GENERATED, subjectExpiry);
        final Policy policy = TestConstants.Policy.POLICY
                .toBuilder()
                .setRevision(NEXT_REVISION)
                .setSubjectFor(TestConstants.Policy.LABEL, subjectWithExpiry)
                .build();
        final CreatePolicy command = CreatePolicy.of(policy, dittoHeaders);

        final Instant expectedAdjustedExpiry = localDateTime
                .truncatedTo(ChronoUnit.MINUTES)
                .plusSeconds(30)
                .atZone(ZoneId.systemDefault()).toInstant();
        final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance(expectedAdjustedExpiry);
        final Subject expectedSubjectWithExpiry = Subject.newInstance(subjectWithExpiry.getId(),
                subjectWithExpiry.getType(), expectedSubjectExpiry);

        assertModificationResult(underTest, policy, command,
                PolicyCreated.class,
                event -> {
                    assertThat(event.getPolicy().getEntityId())
                            .contains(TestConstants.Policy.POLICY.getEntityId().get());
                    assertThat(event.getPolicy().getEntryFor(TestConstants.Policy.LABEL).get().getSubjects())
                            .contains(expectedSubjectWithExpiry);
                },
                CreatePolicyResponse.class,
                response -> {
                    assertThat(response.getPolicyCreated()).isPresent();
                    assertThat(response.getPolicyCreated().get()
                            .getEntryFor(TestConstants.Policy.LABEL).get().getSubjects())
                            .contains(expectedSubjectWithExpiry);
                }
        );
    }

    @Test
    public void createPolicyWithSubjectHavingExpiryTimestampInThePast() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance("2020-11-23T15:52:36.123Z");
        final Subject subjectWithExpiry = Subject.newInstance(SubjectId.newInstance("foo-issuer:bar-subject"),
                SubjectType.GENERATED, subjectExpiry);
        final Policy policy = TestConstants.Policy.POLICY
                .toBuilder()
                .setSubjectFor(TestConstants.Policy.LABEL, subjectWithExpiry)
                .build();
        final CreatePolicy command = CreatePolicy.of(policy, dittoHeaders);

        final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance("2020-11-23T15:52:40Z");

        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectExpiryInvalidException.newBuilderTimestampInThePast(expectedSubjectExpiry)
                        .description("It must not be in the past, please adjust to a timestamp in the future.")
                        .dittoHeaders(dittoHeaders)
                        .build());
    }

}
