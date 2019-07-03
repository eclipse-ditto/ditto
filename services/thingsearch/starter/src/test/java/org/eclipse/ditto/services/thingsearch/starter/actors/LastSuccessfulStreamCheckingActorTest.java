/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.Attributes;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public final class LastSuccessfulStreamCheckingActorTest {

    private static ActorSystem actorSystem;
    private static Duration syncErrorOffset;
    private static Duration syncWarningOffset;

    private TestKit testKit;
    private ActorRef underTest;

    @Mock
    private SyncConfig syncConfig;
    @Mock
    private TimestampPersistence searchSyncPersistence;

    @BeforeClass
    public static void setupOnce() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
        syncErrorOffset = Duration.ofMinutes(30);
        syncWarningOffset = Duration.ofMinutes(20);
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    @Before
    public void setup() {
        testKit = new TestKit(actorSystem);
        when(syncConfig.isEnabled()).thenReturn(true);
        when(syncConfig.getOutdatedWarningOffset()).thenReturn(syncWarningOffset);
        when(syncConfig.getOutdatedErrorOffset()).thenReturn(syncErrorOffset);
    }

    @Test
    public void triggerHealthRetrievalWithNoSuccessfulStream() {
        underTest = actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(syncConfig, searchSyncPersistence));
        when(searchSyncPersistence.getTimestampAsync()).thenReturn(Source.single(Optional.empty()));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo = createStatusInfo(StatusDetailMessage.Level.WARN,
                LastSuccessfulStreamCheckingActor.NO_SUCCESSFUL_STREAM_YET_MESSAGE);
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithExceededErrorOffset() {
        // GIVEN: actor startup was more than syncWarningOffset in the past
        final Instant startUpInstant = Instant.now().minus(syncErrorOffset.multipliedBy(2L));

        final Props props = Props.create(LastSuccessfulStreamCheckingActor.class, syncConfig, searchSyncPersistence,
                startUpInstant);

        underTest = actorSystem.actorOf(props);

        // WHEN: last successful sync is over syncErrorOffset
        final Instant lastSuccessfulStream = now().minusSeconds(syncErrorOffset.getSeconds() + 60);
        when(searchSyncPersistence.getTimestampAsync())
                .thenReturn(Source.single(Optional.of(lastSuccessfulStream)));

        // THEN: status is ERROR
        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo =
                createStatusInfo(StatusDetailMessage.Level.ERROR,
                        "End timestamp of last successful sync is about <31> " +
                                "minutes ago. Maximum duration before showing this error is <PT30M>.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithExceededWarningOffset() {
        underTest = actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(syncConfig, searchSyncPersistence));
        final Instant lastSuccessfulStream = now().minusSeconds(syncWarningOffset.getSeconds() + 60);
        when(searchSyncPersistence.getTimestampAsync())
                .thenReturn(Source.single(Optional.of(lastSuccessfulStream)));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo =
                createStatusInfo(StatusDetailMessage.Level.WARN,
                        "End timestamp of last successful sync is about <21> " +
                                "minutes ago. Maximum duration before showing this warning is <PT20M>.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithExceptionWhenAskingForLastSyncTime() {
        // disable logging to suppress stack trace. comment out to debug test.
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());

        underTest = actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(syncConfig, searchSyncPersistence));
        final IllegalStateException mockedEx = new IllegalStateException("Something happened");
        when(searchSyncPersistence.getTimestampAsync()).thenReturn(Source.failed(mockedEx));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo =
                createStatusInfo(StatusDetailMessage.Level.ERROR, "An error occurred when asking for the end " +
                        "timestamp of last successful sync. Reason: <" + mockedEx.toString() + ">.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithSuccessfulStreamInTime() {
        underTest = actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(syncConfig, searchSyncPersistence));
        final Instant lastSuccessfulStream = now().minusSeconds(syncWarningOffset.getSeconds() - 10);
        when(searchSyncPersistence.getTimestampAsync())
                .thenReturn(Source.single(Optional.of(lastSuccessfulStream)));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo = createStatusInfo(StatusDetailMessage.Level.INFO,
                "End timestamp of last successful sync is about <19> minutes ago.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithDisabledSync() {
        when(syncConfig.isEnabled()).thenReturn(false);
        underTest = actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(syncConfig, searchSyncPersistence));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo = StatusInfo.fromStatus(StatusInfo.Status.UNKNOWN, Collections.singleton(
                StatusDetailMessage.of(StatusDetailMessage.Level.WARN,
                        LastSuccessfulStreamCheckingActor.SYNC_DISABLED_MESSAGE)));
        expectStatusInfo(expectedStatusInfo);
        verify(searchSyncPersistence, never()).getTimestampAsync();
    }

    private void sendRetrieveHealth() {
        underTest.tell(RetrieveHealth.newInstance(), testKit.getRef());
    }

    private static StatusInfo createStatusInfo(final StatusDetailMessage.Level level, final String message) {
        return StatusInfo.fromDetail(StatusDetailMessage.of(level, message));
    }

    private void expectStatusInfo(final StatusInfo expectedStatusInfo) {
        final StatusInfo statusInfo = testKit.expectMsgClass(StatusInfo.class);
        assertThat(statusInfo).isEqualTo(expectedStatusInfo);
    }

}
