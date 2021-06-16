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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectResponse;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Unit test for testing the adjustment (rounding up) of a Policy Subject
 * {@link org.eclipse.ditto.policies.model.SubjectExpiry}.
 */
public final class SubjectExpiryAdjustmentTest extends AbstractPolicyCommandStrategyTest {

    @Test
    public void roundingUpToZero() {
        final ModifySubjectStrategy underTest = createStrategy("0");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(52);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry;

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToOneSecond() {
        final ModifySubjectStrategy underTest = createStrategy("1s");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(52);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(1);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToTenSeconds() {
        final ModifySubjectStrategy underTest = createStrategy("10s");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(44);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.MINUTES)
                .plusSeconds(50);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToOneMinute() {
        final ModifySubjectStrategy underTest = createStrategy("1m");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(3);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToFifteenMinutes() {
        final ModifySubjectStrategy underTest = createStrategy("15m");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(3);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.HOURS)
                .plusMinutes(15);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToOneHour() {
        final ModifySubjectStrategy underTest = createStrategy("1h");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(3);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToTwelveHours() {
        final ModifySubjectStrategy underTest = createStrategy("12h");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .plusDays(1)
                .withHour(9);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.DAYS)
                .plusHours(12);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void roundingUpToOneDay() {
        final ModifySubjectStrategy underTest = createStrategy("1d");

        final LocalDateTime givenExpiry = LocalDateTime.now()
                .plusHours(1)
                .plusMinutes(7)
                .plusSeconds(3);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry
                .truncatedTo(ChronoUnit.DAYS)
                .plusDays(1);

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    @Test
    public void doesNotRoundUpMatchingGranularity() {
        final ModifySubjectStrategy underTest = createStrategy("1m");

        final LocalDateTime givenExpiry = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        final LocalDateTime expectedAdjustedExpiry = givenExpiry;

        doTestSubjectExpiryAdjustment(underTest, givenExpiry, expectedAdjustedExpiry);
    }

    private static ModifySubjectStrategy createStrategy(final String configuredGranularityString) {
        return new ModifySubjectStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")
                .withValue("policy.subject-expiry-granularity",
                        ConfigValueFactory.fromAnyRef(configuredGranularityString))));
    }

    private static void doTestSubjectExpiryAdjustment(final ModifySubjectStrategy underTest,
            final LocalDateTime expiry,
            final LocalDateTime expectedExpiry) {

        final Subject subject = Subject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION, "this-is-me"),
                TestConstants.Policy.SUBJECT_TYPE, SubjectExpiry.newInstance(
                        expiry.atOffset(ZoneOffset.UTC).toInstant()));
        final Subject expectedSubject =
                Subject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION, "this-is-me"),
                        TestConstants.Policy.SUBJECT_TYPE,
                        SubjectExpiry.newInstance(expectedExpiry.atOffset(ZoneOffset.UTC).toInstant()));

        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ModifySubject command =
                ModifySubject.of(context.getState(), TestConstants.Policy.LABEL, subject, dittoHeaders);

        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                SubjectCreated.class,
                ModifySubjectResponse.created(context.getState(), TestConstants.Policy.LABEL, expectedSubject,
                        appendETagToDittoHeaders(expectedSubject, dittoHeaders)));
    }

}
