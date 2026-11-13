/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link CreatedPolicyRollback}: which answers are retried, when it gives up, and that it reports a
 * deletion only when the policies service confirmed it.
 */
public final class CreatedPolicyRollbackTest {

    private static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto", "rollback");
    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "rollback");
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder()
            .correlationId("created-policy-rollback")
            .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
            .build();
    private static final DeletePolicy DELETE_POLICY = DeletePolicy.of(POLICY_ID, HEADERS);
    private static final FiniteDuration SHORT = FiniteDuration.apply(300, TimeUnit.MILLISECONDS);

    private ActorSystem system;
    private TestProbe policies;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        system = ActorSystem.create("CreatedPolicyRollbackTest");
        policies = new TestProbe(system);
        logger = (Logger) LoggerFactory.getLogger(CreatedPolicyRollback.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void tearDown() {
        logger.detachAppender(appender);
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void confirmedDeletionIsReportedAsDeleted() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("BACKOFF_DELAY", 2));

        policies.expectMsg(DELETE_POLICY);
        policies.reply(DeletePolicyResponse.of(POLICY_ID, HEADERS));

        assertResult(rollback, CreatedPolicyRollback.Outcome.DELETED, 1);
        assertThat(logLines()).containsExactly("INFO Policy <" + POLICY_ID +
                "> deleted after rolling back the creation of Thing <" + THING_ID + "> (attempt 1).");
    }

    @Test
    public void policyThatIsAlreadyGoneCountsAsRolledBackAndIsNotRetried() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("BACKOFF_DELAY", 2));

        policies.expectMsg(DELETE_POLICY);
        policies.reply(PolicyNotAccessibleException.newBuilder(POLICY_ID).dittoHeaders(HEADERS).build());

        assertResult(rollback, CreatedPolicyRollback.Outcome.ALREADY_ABSENT, 1);
        policies.expectNoMessage(SHORT);
        assertThat(logLines()).singleElement().asString()
                .startsWith("INFO Policy <" + POLICY_ID + "> was already absent when rolling back the creation");
    }

    @Test
    public void unavailablePoliciesServiceIsRetriedUntilTheDeletionIsConfirmed() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("BACKOFF_DELAY", 2));

        policies.expectMsg(DELETE_POLICY);
        policies.reply(PolicyUnavailableException.newBuilder(POLICY_ID).dittoHeaders(HEADERS).build());
        policies.expectMsg(DELETE_POLICY);
        policies.reply(DeletePolicyResponse.of(POLICY_ID, HEADERS));

        assertResult(rollback, CreatedPolicyRollback.Outcome.DELETED, 2);
    }

    @Test
    public void unansweredDeletionIsRetried() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("BACKOFF_DELAY", 2));

        policies.expectMsg(DELETE_POLICY); // not answered: the 300 ms ask times out
        policies.expectMsg(DELETE_POLICY);
        policies.reply(DeletePolicyResponse.of(POLICY_ID, HEADERS));

        assertResult(rollback, CreatedPolicyRollback.Outcome.DELETED, 2);
    }

    @Test
    public void rollbackThatStaysUnavailableGivesUpAndSaysSo() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("BACKOFF_DELAY", 2));

        for (int attempt = 0; attempt < 3; attempt++) {
            policies.expectMsg(DELETE_POLICY);
            policies.reply(PolicyUnavailableException.newBuilder(POLICY_ID).dittoHeaders(HEADERS).build());
        }

        assertResult(rollback, CreatedPolicyRollback.Outcome.GAVE_UP, 3);
        policies.expectNoMessage(SHORT);
        assertThat(logLines()).singleElement().asString()
                .startsWith("ERROR Rollback of the creation of Thing <" + THING_ID + "> failed: Policy <" + POLICY_ID +
                        "> could not be deleted in 3 attempt(s)")
                .doesNotContain("deleted after rolling back");
    }

    @Test
    public void clientErrorIsNotRetried() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("BACKOFF_DELAY", 2));

        policies.expectMsg(DELETE_POLICY);
        policies.reply(PolicyConflictException.newBuilder(POLICY_ID).dittoHeaders(HEADERS).build());

        assertResult(rollback, CreatedPolicyRollback.Outcome.REJECTED, 1);
        policies.expectNoMessage(SHORT);
    }

    @Test
    public void retryStrategyOffMakesOneAttempt() throws Exception {
        final CompletionStage<CreatedPolicyRollback.Result> rollback = start(config("OFF", 3));

        policies.expectMsg(DELETE_POLICY);
        policies.reply(PolicyUnavailableException.newBuilder(POLICY_ID).dittoHeaders(HEADERS).build());

        assertResult(rollback, CreatedPolicyRollback.Outcome.GAVE_UP, 1);
        policies.expectNoMessage(SHORT);
    }

    private CompletionStage<CreatedPolicyRollback.Result> start(final AskWithRetryConfig config) {
        return CreatedPolicyRollback.deletePolicy(policies.ref(), DELETE_POLICY, THING_ID, config, system);
    }

    private static AskWithRetryConfig config(final String retryStrategy, final int retryAttempts) {
        return DefaultAskWithRetryConfig.of(ConfigFactory.parseString("rollback {\n" +
                "  ask-timeout = 300ms\n" +
                "  retry-strategy = " + retryStrategy + "\n" +
                "  retry-attempts = " + retryAttempts + "\n" +
                "  fixed-delay = 50ms\n" +
                "  backoff-delay { min = 50ms, max = 100ms, random-factor = 0.0 }\n" +
                "}"), "rollback");
    }

    private static void assertResult(final CompletionStage<CreatedPolicyRollback.Result> rollback,
            final CreatedPolicyRollback.Outcome expectedOutcome, final int expectedAttempts) throws Exception {

        final CreatedPolicyRollback.Result result = rollback.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(expectedOutcome);
        assertThat(result.attempts()).isEqualTo(expectedAttempts);
    }

    private List<String> logLines() {
        return List.copyOf(appender.list).stream()
                .map(event -> event.getLevel() + " " + event.getFormattedMessage())
                .toList();
    }

}
