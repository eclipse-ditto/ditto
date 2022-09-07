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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectExpiryInvalidException;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntriesModified;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.ModifyPolicyEntriesStrategy}.
 */
public final class ModifyPolicyEntriesStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ModifyPolicyEntriesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyPolicyEntriesStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyEntriesStrategy.class, areImmutable());
    }

    @Test
    public void modifyPolicyEntries() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final Policy policy = TestConstants.Policy.POLICY
                .toBuilder()
                .setRevision(NEXT_REVISION)
                .build();
        final ModifyPolicyEntries command = ModifyPolicyEntries.of(TestConstants.Policy.POLICY_ID, policy, dittoHeaders);

        assertModificationResult(underTest, policy, command,
                PolicyEntriesModified.class,
                event -> {
                    assertThat(event.getPolicyEntries())
                            .containsAll(TestConstants.Policy.POLICY.getEntriesSet());
                },
                ModifyPolicyEntriesResponse.class,
                response -> {
                }
        );
    }

    @Test
    public void modifyPolicyEntriesWithSubjectHavingExpiryTimestamp() {
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
        final ModifyPolicyEntries command = ModifyPolicyEntries.of(TestConstants.Policy.POLICY_ID, policy, dittoHeaders);

        final Instant expectedAdjustedExpiry = localDateTime
                .truncatedTo(ChronoUnit.MINUTES)
                .plusSeconds(30)
                .atZone(ZoneId.systemDefault()).toInstant();
        final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance(expectedAdjustedExpiry);
        final Subject expectedSubjectWithExpiry = Subject.newInstance(subjectWithExpiry.getId(),
                subjectWithExpiry.getType(), expectedSubjectExpiry);

        assertModificationResult(underTest, policy, command,
                PolicyEntriesModified.class,
                event -> {
                    assertThat(StreamSupport.stream(event.getPolicyEntries().spliterator(), false)
                            .filter(entry -> entry.getLabel().equals(TestConstants.Policy.LABEL))
                            .map(PolicyEntry::getSubjects)
                            .findFirst().get()
                    )
                            .contains(expectedSubjectWithExpiry);
                },
                ModifyPolicyEntriesResponse.class,
                response -> {
                }
        );
    }

    @Test
    public void modifyPolicyEntriesWithSubjectHavingExpiryTimestampInThePast() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance("2020-11-23T15:52:36.123Z");
        final Subject subjectWithExpiry = Subject.newInstance(SubjectId.newInstance("foo-issuer:bar-subject"),
                SubjectType.GENERATED, subjectExpiry);
        final Policy policy = TestConstants.Policy.POLICY
                .toBuilder()
                .setSubjectFor(TestConstants.Policy.LABEL, subjectWithExpiry)
                .build();
        final ModifyPolicyEntries command = ModifyPolicyEntries.of(TestConstants.Policy.POLICY_ID, policy, dittoHeaders);

        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectExpiryInvalidException.newBuilderTimestampInThePast(subjectExpiry)
                        .description("It must not be in the past, please adjust to a timestamp in the future.")
                        .dittoHeaders(dittoHeaders)
                        .build());
    }

}
