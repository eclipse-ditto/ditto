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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.base.api.common.ModifyConfig;
import org.eclipse.ditto.base.api.common.ModifyConfigResponse;
import org.eclipse.ditto.base.api.common.RetrieveConfig;
import org.eclipse.ditto.base.api.common.RetrieveConfigResponse;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.result.DeleteResult;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.event.Logging;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link PersistenceCleanupActor}.
 */
public final class PersistenceCleanupActorTest {

    private final ActorSystem actorSystem = ActorSystem.create("test",
            ConfigFactory.load("test.conf"));
    private final AtomicReference<Source<Source<CleanupResult, NotUsed>, NotUsed>> sourceBox =
            new AtomicReference<>(Source.empty());
    private Cleanup cleanup;
    private Credits credits;

    @Before
    public void init() {
        cleanup = mock(Cleanup.class);
        credits = mock(Credits.class);
        doAnswer(inv -> Source.empty()).when(cleanup).getCleanupStream(any());
        doAnswer(inv -> sourceBox.get()).when(credits).regulate(any(), any());
    }

    @After
    public void cleanUp() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void emptyStream() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());
            final var retrieveHealth = RetrieveHealth.newInstance();
            final var probeSource =
                    TestSource.<Source<CleanupResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("IN_QUIET_PERIOD", ""));
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            probe.expectRequest();
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", ""));
            probe.sendComplete();
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", ""), probe::expectNoMsg);
        }};
    }

    @Test
    public void successfulStream() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());
            final var retrieveHealth = RetrieveHealth.newInstance();
            final var probeSource =
                    TestSource.<Source<CleanupResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            probe.expectRequest();

            probe.sendNext(Source.single(new CleanupResult(
                    CleanupResult.Type.SNAPSHOTS,
                    new SnapshotRevision("thing:p:id", 1234, true),
                    DeleteResult.acknowledged(4)
            )));
            waitForResponse(this, underTest, retrieveHealthResponse("RUNNING", "thing:p:id"), probe::expectNoMsg);

            // WHEN stream completes successfully
            probe.sendComplete();
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", ""), probe::expectNoMsg);

            // THEN the starting PID is reset
            final var killSwitchPair = Source.<Source<CleanupResult, NotUsed>>never()
                    .viaMat(KillSwitches.single(), Keep.right())
                    .preMaterialize(actorSystem);
            sourceBox.set(killSwitchPair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", ""));
            verify(cleanup, timeout(5000L).times(2)).getCleanupStream(eq(""));
            killSwitchPair.first().shutdown();
        }};
    }

    @Test
    public void failedStream() {
        new TestKit(actorSystem) {{
            // uncomment to un-suppress error log
            actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());

            final ActorRef underTest = childActorOf(testProps());
            final var retrieveHealth = RetrieveHealth.newInstance();
            final var probeSource =
                    TestSource.<Source<CleanupResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            probe.expectRequest();

            final var pid = "thing:p:id";
            probe.sendNext(Source.single(new CleanupResult(
                    CleanupResult.Type.SNAPSHOTS,
                    new SnapshotRevision(pid, 1234, true),
                    DeleteResult.acknowledged(4)
            )));
            waitForResponse(this, underTest, retrieveHealthResponse("RUNNING", pid), probe::expectNoMsg);

            // WHEN stream fails
            probe.sendError(new IllegalStateException("Expected error"));
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", pid), probe::expectNoMsg);

            // THEN the starting PID is set to the last successful pid
            final var killSwitchPair = Source.<Source<CleanupResult, NotUsed>>never()
                    .viaMat(KillSwitches.single(), Keep.right())
                    .preMaterialize(actorSystem);
            sourceBox.set(killSwitchPair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", pid));
            verify(cleanup, timeout(5000L)).getCleanupStream(eq(pid));
            killSwitchPair.first().shutdown();
        }};
    }

    @Test
    public void retrieveConfig() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());
            final var retrieveConfig = RetrieveConfig.of();
            underTest.tell(retrieveConfig, getRef());
            final var retrieveConfigResponse = expectMsgClass(RetrieveConfigResponse.class);
            final String expectedConfigJson =
                    CleanupConfig.of(ConfigFactory.empty()).render().root().render(ConfigRenderOptions.concise());
            assertThat(retrieveConfigResponse.getConfig()).isEqualTo(JsonFactory.readFrom(expectedConfigJson));
        }};
    }

    @Test
    public void modifyConfigInQuietPeriod() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());
            final var modifyConfig =
                    ModifyConfig.of(JsonObject.newBuilder().set("enabled", false).build(), DittoHeaders.empty());
            underTest.tell(modifyConfig, getRef());
            final var response = expectMsgClass(ModifyConfigResponse.class);
            final var expectedConfig = CleanupConfig.of(ConfigFactory.parseMap(Map.of("cleanup.enabled", false)));
            assertThat(response.getConfig()).containsExactlyInAnyOrderElementsOf(
                    JsonObject.of(expectedConfig.render().root().render(ConfigRenderOptions.concise())));
        }};
    }

    @Test
    public void modifyConfigWhenRunning() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());

            // GIVEN a cleanup stream is running
            final var retrieveHealth = RetrieveHealth.newInstance();
            sourceBox.set(Source.never());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", ""));

            // WHEN config is modified
            final var modifyConfig = ModifyConfig.of(JsonObject.newBuilder()
                            .set("enabled", false)
                            .set("last-pid", "thing:last:pid")
                            .build(),
                    DittoHeaders.empty());
            underTest.tell(modifyConfig, getRef());
            final var response = expectMsgClass(ModifyConfigResponse.class);
            final var expectedConfig = CleanupConfig.of(ConfigFactory.parseMap(Map.of("cleanup.enabled", false)));
            assertThat(response.getConfig()).containsExactlyInAnyOrderElementsOf(
                    JsonObject.of(expectedConfig.render().root().render(ConfigRenderOptions.concise())));

            // THEN the running stream is shutdown
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", "thing:last:pid"), () -> {
                try {
                    Thread.sleep(300L);
                } catch (final Exception e) {
                    throw new AssertionError(e);
                }
            });
        }};
    }

    @Test
    public void shutdownInQuietPeriod() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());

            underTest.tell(PersistenceCleanupActor.Control.SERVICE_REQUESTS_DONE, getRef());
            expectMsg(Done.getInstance());
        }};
    }

    @Test
    public void shutdownWhenRunning() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());

            // GIVEN a cleanup stream is running
            final var retrieveHealth = RetrieveHealth.newInstance();
            final var probeSource =
                    TestSource.<Source<CleanupResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", ""));

            // WHEN graceful shutdown is initiated
            underTest.tell(PersistenceCleanupActor.Control.SERVICE_REQUESTS_DONE, getRef());

            // THEN expect cancellation and Done
            probe.expectCancellation();
            expectMsg(Done.getInstance());
        }};
    }

    @Test
    public void shutdownWhenStreamCompleted() throws InterruptedException {
        new TestKit(actorSystem) {{
            final ActorRef underTest = childActorOf(testProps());
            final var retrieveHealth = RetrieveHealth.newInstance();
            final var probeSource =
                    TestSource.<Source<CleanupResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            probe.expectRequest();

            probe.sendNext(Source.single(new CleanupResult(
                    CleanupResult.Type.SNAPSHOTS,
                    new SnapshotRevision("thing:p:id", 1234, true),
                    DeleteResult.acknowledged(4)
            )));
            waitForResponse(this, underTest, retrieveHealthResponse("RUNNING", "thing:p:id"), probe::expectNoMsg);

            // WHEN stream completes successfully and graceful shutdown is initiated
            probe.sendComplete();
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", ""), probe::expectNoMsg);
            underTest.tell(PersistenceCleanupActor.Control.SERVICE_REQUESTS_DONE, getRef());

            // THEN expect Done
            expectMsg(Done.getInstance());
        }};
    }

    private void waitForResponse(final TestKit testKit,
            final ActorRef underTest,
            final RetrieveHealthResponse expectedResponse,
            final Runnable wait) {
        final var retries = 30;
        Object lastResponse = null;
        for (int i = 1; i <= retries; ++i) {
            underTest.tell(RetrieveHealth.newInstance(), testKit.getRef());
            final var response = testKit.expectMsgClass(Duration.of(30, ChronoUnit.SECONDS), RetrieveHealthResponse.class);
            if (response.equals(expectedResponse)) {
                return;
            }
            lastResponse = response;
            Logging.getLogger(actorSystem, this).info("Waiting {}/{}", i, retries);
            wait.run();
        }
        throw new AssertionError(
                "Did not receive expected response: " + expectedResponse + "\nlastResponse=" + lastResponse);
    }

    private Props testProps() {
        return Props.create(PersistenceCleanupActor.class,
                () -> new PersistenceCleanupActor(cleanup, credits, mock(MongoReadJournal.class),
                        () -> Pair.create(0, 1)));
    }

    private static RetrieveHealthResponse retrieveHealthResponse(final String stateName, final String lastPid) {
        return RetrieveHealthResponse.of(
                StatusInfo.fromDetail(StatusDetailMessage.of(StatusDetailMessage.Level.INFO, JsonObject.newBuilder()
                        .set("state", stateName)
                        .set("pid", lastPid)
                        .build())
                ),
                DittoHeaders.empty()
        );
    }

}
