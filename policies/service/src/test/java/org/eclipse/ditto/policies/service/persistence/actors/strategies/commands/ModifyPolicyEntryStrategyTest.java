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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectExpiryInvalidException;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryModified;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.ModifyPolicyEntryStrategy}.
 */
public final class ModifyPolicyEntryStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ModifyPolicyEntryStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyPolicyEntryStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyEntryStrategy.class, areImmutable());
    }

    @Test
    public void modifyPolicyEntry() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final Subject subject = Subject.newInstance(SubjectIssuer.INTEGRATION, "this-is-me");
        final PolicyEntry policyEntry =
                PolicyEntry.newInstance(TestConstants.Policy.LABEL, Collections.singleton(subject),
                        Collections.singleton(Resource.newInstance(
                                TestConstants.Policy.RESOURCE_TYPE_POLICY, "/", EffectedPermissions.newInstance(
                                        Arrays.asList("READ", "WRITE"), Collections.emptyList())))
                );
        final ModifyPolicyEntry command = ModifyPolicyEntry.of(TestConstants.Policy.POLICY_ID, policyEntry,
                dittoHeaders);

        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyEntryModified.class,
                event -> {
                    assertThat(event.getPolicyEntry().getSubjects())
                            .contains(subject);
                },
                ModifyPolicyEntryResponse.class,
                response -> {
                }
        );
    }

    @Test
    public void modifyPolicyEntryWithSubjectHavingExpiryTimestamp() {
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
        final PolicyEntry policyEntry =
                PolicyEntry.newInstance(TestConstants.Policy.LABEL,
                        List.of(subjectWithExpiry,
                                Subject.newInstance("permanent:subject", SubjectType.GENERATED)),
                        Collections.singleton(Resource.newInstance(
                                TestConstants.Policy.RESOURCE_TYPE_POLICY, "/", EffectedPermissions.newInstance(
                                        Arrays.asList("READ", "WRITE"), Collections.emptyList())))
                );
        final ModifyPolicyEntry command = ModifyPolicyEntry.of(TestConstants.Policy.POLICY_ID, policyEntry,
                dittoHeaders);

        final Instant expectedAdjustedExpiry = localDateTime
                .truncatedTo(ChronoUnit.MINUTES)
                .plusSeconds(30)
                .atZone(ZoneId.systemDefault()).toInstant();
        final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance(expectedAdjustedExpiry);
        final Subject expectedSubjectWithExpiry = Subject.newInstance(subjectWithExpiry.getId(),
                subjectWithExpiry.getType(), expectedSubjectExpiry);

        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyEntryModified.class,
                event -> {
                    assertThat(event.getPolicyEntry().getSubjects())
                            .contains(expectedSubjectWithExpiry);
                },
                ModifyPolicyEntryResponse.class,
                response -> {
                }
        );
    }

    @Test
    public void modifyPolicyEntryWithSubjectHavingExpiryTimestampInThePast() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final SubjectExpiry subjectExpiry = SubjectExpiry.newInstance("2020-11-23T15:52:36.123Z");
        final Subject subjectWithExpiry = Subject.newInstance(SubjectId.newInstance("foo-issuer:bar-subject"),
                SubjectType.GENERATED, subjectExpiry);
        final PolicyEntry policyEntry =
                PolicyEntry.newInstance(TestConstants.Policy.LABEL, Collections.singleton(subjectWithExpiry),
                        Collections.emptyList());
        final ModifyPolicyEntry command = ModifyPolicyEntry.of(TestConstants.Policy.POLICY_ID, policyEntry,
                dittoHeaders);

        final SubjectExpiry expectedSubjectExpiry = SubjectExpiry.newInstance("2020-11-23T15:52:40Z");

        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectExpiryInvalidException.newBuilderTimestampInThePast(expectedSubjectExpiry)
                        .description("It must not be in the past, please adjust to a timestamp in the future.")
                        .dittoHeaders(dittoHeaders)
                        .build());
    }

}
