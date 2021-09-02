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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.result.DeleteResult;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.event.Logging;
import akka.stream.Attributes;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link PersistenceCleanUpActor}.
 */
public final class PersistenceCleanUpActorTest {

    private final ActorSystem actorSystem = ActorSystem.create();
    private final AtomicReference<Source<Source<CleanUpResult, NotUsed>, NotUsed>> sourceBox =
            new AtomicReference<>(Source.empty());
    private CleanUp cleanUp;
    private Credits credits;

    @Before
    public void init() {
        cleanUp = mock(CleanUp.class);
        credits = mock(Credits.class);
        doAnswer(inv -> Source.empty()).when(cleanUp).getCleanUpStream(any());
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
                    TestSource.<Source<CleanUpResult, NotUsed>>probe(actorSystem);
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
                    TestSource.<Source<CleanUpResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            probe.expectRequest();

            probe.sendNext(Source.single(new CleanUpResult(
                    CleanUpResult.Type.SNAPSHOTS,
                    new SnapshotRevision("thing:p:id", 1234, true),
                    DeleteResult.acknowledged(4)
            )));
            waitForResponse(this, underTest, retrieveHealthResponse("RUNNING", "thing:p:id"), probe::expectNoMsg);

            // WHEN stream completes successfully
            probe.sendComplete();
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", ""), probe::expectNoMsg);

            // THEN the starting PID is reset
            final var killSwitchPair = Source.<Source<CleanUpResult, NotUsed>>never()
                    .viaMat(KillSwitches.single(), Keep.right())
                    .preMaterialize(actorSystem);
            sourceBox.set(killSwitchPair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", ""));
            verify(cleanUp, timeout(5000L).times(2)).getCleanUpStream(eq(""));
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
                    TestSource.<Source<CleanUpResult, NotUsed>>probe(actorSystem);
            final var probeSourcePair = probeSource.preMaterialize(actorSystem);
            final var probe = probeSourcePair.first();
            sourceBox.set(probeSourcePair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            probe.expectRequest();

            final var pid = "thing:p:id";
            probe.sendNext(Source.single(new CleanUpResult(
                    CleanUpResult.Type.SNAPSHOTS,
                    new SnapshotRevision(pid, 1234, true),
                    DeleteResult.acknowledged(4)
            )));
            waitForResponse(this, underTest, retrieveHealthResponse("RUNNING", pid), probe::expectNoMsg);

            // WHEN stream fails
            probe.sendError(new IllegalStateException("Expected error"));
            waitForResponse(this, underTest, retrieveHealthResponse("IN_QUIET_PERIOD", pid), probe::expectNoMsg);

            // THEN the starting PID is set to the last successful pid
            final var killSwitchPair = Source.<Source<CleanUpResult, NotUsed>>never()
                    .viaMat(KillSwitches.single(), Keep.right())
                    .preMaterialize(actorSystem);
            sourceBox.set(killSwitchPair.second());
            underTest.tell(FSM.StateTimeout$.MODULE$, ActorRef.noSender());
            underTest.tell(retrieveHealth, getRef());
            expectMsg(retrieveHealthResponse("RUNNING", pid));
            verify(cleanUp, timeout(5000L)).getCleanUpStream(eq(pid));
            killSwitchPair.first().shutdown();
        }};
    }

    private void waitForResponse(final TestKit testKit,
            final ActorRef underTest,
            final RetrieveHealthResponse expectedResponse,
            final Runnable wait) {
        final var retries = 30;
        for (int i = 1; i <= retries; ++i) {
            underTest.tell(RetrieveHealth.newInstance(), testKit.getRef());
            final var response = testKit.expectMsgClass(RetrieveHealthResponse.class);
            if (response.equals(expectedResponse)) {
                return;
            }
            Logging.getLogger(actorSystem, this).info("Waiting {}/{}", i, retries);
            wait.run();
        }
        throw new AssertionError("Did not receive expected response: " + expectedResponse);
    }

    private Props testProps() {
        return Props.create(PersistenceCleanUpActor.class,
                () -> new PersistenceCleanUpActor(Duration.ofHours(1L), cleanUp, credits));
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
