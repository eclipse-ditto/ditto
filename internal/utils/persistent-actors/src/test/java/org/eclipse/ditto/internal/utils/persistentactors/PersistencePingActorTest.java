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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.internal.utils.pekko.PingCommand;
import org.eclipse.ditto.internal.utils.persistentactors.config.DefaultPingConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Tests {@link PersistencePingActor}.
 */
public final class PersistencePingActorTest {

    public static final Config CONFIG = ConfigFactory.load("persistence-ping-test");

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("PekkoTestSystem", CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Test
    public void conversionBetweenCorrelationIdAndPersistenceIdIsOneToOne() {
        final PolicyId id1 = PolicyId.of("foo:random-ID-jbxlkeimx");
        final PolicyId id2 = PolicyId.of("foo:differentId");
        final Optional<String> outputId1 = PersistencePingActor.toPersistenceId(
                PersistencePingActor.toCorrelationId(id1));
        final Optional<String> outputId2 = PersistencePingActor.toPersistenceId(
                PersistencePingActor.toCorrelationId(id2));
        assertThat(outputId1).contains(id1.toString());
        assertThat(outputId2).contains(id2.toString());
        assertThat(outputId1).isNotEqualTo(outputId2);
    }

    @Test
    public void pingPersistenceActors() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);

            final PingConfig pingConfig =
                    DefaultPingConfig.of(actorSystem.settings().config().getConfig("ditto.test"));
            final PolicyId persistenceId1 = PolicyId.of("some:pid-1");
            final PolicyId persistenceId2 = PolicyId.of("some:pid-2");
            final PolicyId persistenceId3 = PolicyId.of("some:pid-3");
            final Props props = PersistencePingActor.propsForTests(probe.ref(), pingConfig,
                    () -> Source.from(Arrays.asList(
                            persistenceId1.getEntityType() + ":" + persistenceId1,
                            persistenceId1.getEntityType() + ":" + persistenceId2,
                            persistenceId1.getEntityType() + ":" + persistenceId3)));

            actorSystem.actorOf(props);

            final PingCommand msg1 = probe.expectMsgClass(PingCommand.class);
            assertThat((CharSequence) msg1.getEntityId()).isEqualTo(persistenceId1);
            assertThat(msg1.getCorrelationId()).contains(PersistencePingActor.toCorrelationId(persistenceId1));
            final PingCommand msg2 = probe.expectMsgClass(PingCommand.class);
            assertThat((CharSequence) msg2.getEntityId()).isEqualTo(persistenceId2);
            assertThat(msg2.getCorrelationId()).contains(PersistencePingActor.toCorrelationId(persistenceId2));
            final PingCommand msg3 = probe.expectMsgClass(PingCommand.class);
            assertThat((CharSequence) msg3.getEntityId()).isEqualTo(persistenceId3);
            assertThat(msg3.getCorrelationId()).contains(PersistencePingActor.toCorrelationId(persistenceId3));
        }};
    }

    @Test
    public void testPersistenceActorIsNotPingedTwice() {
        new TestKit(actorSystem) {{
            final TestProbe probe = new TestProbe(actorSystem);

            final PingConfig pingConfig =
                    DefaultPingConfig.of(actorSystem.settings().config().getConfig("ditto.test"));
            final String persistenceId1 = "some:pid-1";
            final String persistenceId2 = "some:pid-2";
            final String persistenceId3 = "some:pid-3";
            final Props props = PersistencePingActor.propsForTests(probe.ref(), pingConfig,
                    () -> Source.from(Arrays.asList(persistenceId1, persistenceId2, persistenceId3)));

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(PersistencePingActor.InternalMessages.START_PINGING, getRef());

            probe.expectMsgClass(PingCommand.class);
            probe.expectMsgClass(PingCommand.class);
            probe.expectMsgClass(PingCommand.class);
            probe.expectNoMessage();
        }};
    }

    /**
     * A ping stream that fails (for example a page query cancelled by the database after the read journal's own
     * retries are exhausted) must not stop pinging: the failure is logged with its cause and the next scheduled
     * iteration runs normally.
     */
    @Test
    public void failedPingStreamIsLoggedAndTheNextScheduledIterationRuns() {
        new TestKit(actorSystem) {{
            final TestKit shardRegion = new TestKit(actorSystem);
            final TestKit logEvents = new TestKit(actorSystem);
            actorSystem.getEventStream().subscribe(logEvents.getRef(), Logging.Warning.class);
            final AtomicInteger iterations = new AtomicInteger();
            final Props props = PersistencePingActor.propsForTests(shardRegion.getRef(), fastPingConfig(), () -> {
                if (iterations.getAndIncrement() == 0) {
                    // one pid, then the stream fails.
                    return Source.from(List.of("policy:ns:first", "policy:ns:failing")).map(pid -> {
                        if (pid.endsWith("failing")) {
                            throw new IllegalStateException(
                                    "page query failed: [57014] canceling statement due to statement timeout");
                        }
                        return pid;
                    });
                }
                return Source.single("policy:ns:second");
            });
            final ActorRef underTest = actorSystem.actorOf(props);
            try {
                final PingCommand first = shardRegion.expectMsgClass(PingCommand.class);
                assertThat((CharSequence) first.getEntityId()).hasToString("ns:first");

                final Object warning = logEvents.fishForMessage(Duration.ofSeconds(10), "WARN with the cause",
                        event -> event instanceof Logging.Warning w && w.logClass() == PersistencePingActor.class
                                && String.valueOf(w.message()).contains("57014"));
                assertThat(String.valueOf(((Logging.Warning) warning).message()))
                        .as("the WARN names the number of pids pinged before the failure")
                        .contains("<1>");

                // the next scheduled iteration (interval 1s) is not blocked by the failed one.
                final PingCommand next = shardRegion.expectMsgClass(Duration.ofSeconds(10), PingCommand.class);
                assertThat((CharSequence) next.getEntityId()).hasToString("ns:second");
            } finally {
                actorSystem.getEventStream().unsubscribe(logEvents.getRef());
                actorSystem.stop(underTest);
            }
        }};
    }

    /**
     * A successful iteration logs one INFO line with the number of pids visited and leaves the actor ready for the
     * next scheduled iteration (behaviour of the success path unchanged).
     */
    @Test
    public void successfulPingIterationLogsVisitedPidsAndTheNextScheduledIterationRuns() {
        new TestKit(actorSystem) {{
            final TestKit shardRegion = new TestKit(actorSystem);
            final TestKit logEvents = new TestKit(actorSystem);
            actorSystem.getEventStream().subscribe(logEvents.getRef(), Logging.Info.class);
            final Props props = PersistencePingActor.propsForTests(shardRegion.getRef(), fastPingConfig(),
                    () -> Source.from(List.of("policy:ns:a", "policy:ns:b")));
            final ActorRef underTest = actorSystem.actorOf(props);
            try {
                shardRegion.expectMsgClass(PingCommand.class);
                shardRegion.expectMsgClass(PingCommand.class);
                logEvents.fishForMessage(Duration.ofSeconds(10), "INFO with the visited pids",
                        event -> event instanceof Logging.Info i && i.logClass() == PersistencePingActor.class
                                && String.valueOf(i.message()).contains("<2>"));

                final PingCommand next = shardRegion.expectMsgClass(Duration.ofSeconds(10), PingCommand.class);
                assertThat((CharSequence) next.getEntityId()).hasToString("ns:a");
            } finally {
                actorSystem.getEventStream().unsubscribe(logEvents.getRef());
                actorSystem.stop(underTest);
            }
        }};
    }

    /**
     * In ID order, the walk after a failed one continues after the last pid the failed walk pinged instead of starting
     * over, so a failure that recurs within the (possibly days long) walk cannot starve its tail. Once a walk
     * completes, the next one starts from the beginning again.
     */
    @Test
    public void walkAfterAFailedIdOrderWalkResumesAfterTheLastPingedPid() {
        new TestKit(actorSystem) {{
            final TestKit shardRegion = new TestKit(actorSystem);
            final AtomicInteger iterations = new AtomicInteger();
            final List<String> all = List.of("policy:ns:a", "policy:ns:b", "policy:ns:c", "policy:ns:d");
            final Props props = PersistencePingActor.propsForTests(shardRegion.getRef(), fastPingConfig(), () -> {
                if (iterations.getAndIncrement() == 0) {
                    // pings a and b, then fails.
                    return Source.from(all).map(pid -> {
                        if (pid.endsWith(":c")) {
                            throw new IllegalStateException("[57014] canceling statement due to statement timeout");
                        }
                        return pid;
                    });
                }
                return Source.from(all);
            });
            final ActorRef underTest = actorSystem.actorOf(props);
            try {
                assertThat(entityIds(shardRegion, 2)).containsExactly("ns:a", "ns:b");
                assertThat(entityIds(shardRegion, 2))
                        .as("the walk after the failed one resumes after the last pinged pid")
                        .containsExactly("ns:c", "ns:d");
                assertThat(entityIds(shardRegion, 4))
                        .as("after a complete walk, the next one starts from the beginning")
                        .containsExactly("ns:a", "ns:b", "ns:c", "ns:d");
            } finally {
                actorSystem.stop(underTest);
            }
        }};
    }

    /**
     * A pid the actor cannot turn into an entity id must not stop a best-effort wake-up of all the others: it is
     * logged at WARN and skipped, the walk goes on and completes. Two kinds, in pid order as an ID-order walk delivers
     * them: no entity-type prefix, and an id the entity type rejects ('/' in a policy name). In both streaming orders.
     */
    @Test
    public void unparseablePidIsLoggedAndSkippedAndTheWalkContinues() {
        for (final String order : List.of("ID", "TAGS")) {
            new TestKit(actorSystem) {{
                final TestKit shardRegion = new TestKit(actorSystem);
                final TestKit logEvents = new TestKit(actorSystem);
                actorSystem.getEventStream().subscribe(logEvents.getRef(), Logging.Warning.class);
                final Props props = PersistencePingActor.propsForTests(shardRegion.getRef(), fastPingConfig(order),
                        () -> Source.from(List.of("nocolon", "policy:ns:a", "policy:ns:a/x", "policy:ns:b")));
                final ActorRef underTest = actorSystem.actorOf(props);
                try {
                    assertThat(entityIds(shardRegion, 2)).as("order %s", order).containsExactly("ns:a", "ns:b");
                    for (final String unparseable : List.of("<nocolon>", "<policy:ns:a/x>")) {
                        logEvents.fishForMessage(Duration.ofSeconds(10), "WARN naming " + unparseable,
                                event -> event instanceof Logging.Warning w
                                        && w.logClass() == PersistencePingActor.class
                                        && String.valueOf(w.message()).contains("Skipping")
                                        && String.valueOf(w.message()).contains(unparseable));
                    }
                    assertThat(entityIds(shardRegion, 2))
                            .as("order %s: the walk completed, the next one starts from the beginning", order)
                            .containsExactly("ns:a", "ns:b");
                } finally {
                    actorSystem.getEventStream().unsubscribe(logEvents.getRef());
                    actorSystem.stop(underTest);
                }
            }};
        }
    }

    /**
     * A failure that recurs at the same position of every walk (on PostgreSQL a window that keeps exceeding
     * statement_timeout; on MongoDB a query that keeps timing out): the walk after the failed one resumes there, fails
     * at once without pinging anything, and must not keep resuming there for good. Its resume point is given up and
     * the next walk starts from the beginning again, so the entities before the failure keep being pinged.
     */
    @Test
    public void failureRecurringAtTheSamePositionGivesUpTheResumePointOnPostgres() {
        assertFailureRecurringAtTheSamePositionKeepsPinging(
                () -> new IllegalStateException("[57014] canceling statement due to statement timeout"));
    }

    @Test
    public void failureRecurringAtTheSamePositionGivesUpTheResumePointOnMongo() {
        assertFailureRecurringAtTheSamePositionKeepsPinging(
                () -> new com.mongodb.MongoExecutionTimeoutException(50, "operation exceeded time limit"));
    }

    private void assertFailureRecurringAtTheSamePositionKeepsPinging(final Supplier<RuntimeException> failure) {
        new TestKit(actorSystem) {{
            final TestKit shardRegion = new TestKit(actorSystem);
            final TestKit logEvents = new TestKit(actorSystem);
            actorSystem.getEventStream().subscribe(logEvents.getRef(), Logging.Warning.class);
            // every walk: a and b, then the same failure.
            final Props props = PersistencePingActor.propsForTests(shardRegion.getRef(), fastPingConfig(), () ->
                    Source.from(List.of("policy:ns:a", "policy:ns:b", "policy:ns:c")).map(pid -> {
                        if (pid.endsWith(":c")) {
                            throw failure.get();
                        }
                        return pid;
                    }));
            final ActorRef underTest = actorSystem.actorOf(props);
            try {
                assertThat(entityIds(shardRegion, 2)).containsExactly("ns:a", "ns:b");
                // the second walk resumes after b and fails at once; it gives the resume point up.
                logEvents.fishForMessage(Duration.ofSeconds(10), "WARN about the resume point given up",
                        event -> event instanceof Logging.Warning w && w.logClass() == PersistencePingActor.class
                                && String.valueOf(w.message()).contains("starts from the beginning")
                                && String.valueOf(w.message()).contains("<policy:ns:b>"));
                assertThat(entityIds(shardRegion, 2))
                        .as("the walk after that starts from the beginning: the head keeps being pinged")
                        .containsExactly("ns:a", "ns:b");
            } finally {
                actorSystem.getEventStream().unsubscribe(logEvents.getRef());
                actorSystem.stop(underTest);
            }
        }};
    }

    private static List<String> entityIds(final TestKit shardRegion, final int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> shardRegion.expectMsgClass(Duration.ofSeconds(10), PingCommand.class))
                .map(ping -> ping.getEntityId().toString())
                .toList();
    }

    /** Pings every second, starting at once, without a throttle that slows the test down. */
    private static PingConfig fastPingConfig() {
        return fastPingConfig("ID");
    }

    private static PingConfig fastPingConfig(final String streamingOrder) {
        return DefaultPingConfig.of(ConfigFactory.parseString("persistence-ping {\n"
                + "  journal-tag = \"\"\n"
                + "  initial-delay = 0s\n"
                + "  interval = 1s\n"
                + "  read-journal-batch-size = 10\n"
                + "  streaming-order = " + streamingOrder + "\n"
                + "  rate { frequency = 100ms, entities = 10 }\n"
                + "}"));
    }

}
