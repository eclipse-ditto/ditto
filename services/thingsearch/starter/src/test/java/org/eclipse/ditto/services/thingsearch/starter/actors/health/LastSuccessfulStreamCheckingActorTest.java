/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.starter.actors.health;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.starter.actors.health.LastSuccessfulStreamCheckingActor.SYNC_DISABLED_MESSAGE;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
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
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class LastSuccessfulStreamCheckingActorTest {

    private static ActorSystem actorSystem;
    private static Duration syncErrorOffset;
    private static Duration syncWarningOffset;

    private TestKit testKit;
    private ActorRef underTest;

    @Mock
    private StreamMetadataPersistence searchSyncPersistence;

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
    }

    @Test
    public void triggerHealthRetrievalWithNoSuccessfulStream() {
        final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties =
                buildConfigProperties(true, searchSyncPersistence, syncWarningOffset, syncErrorOffset);
        underTest =
                actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(streamHealthCheckConfigurationProperties));
        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd()).thenReturn(Optional.empty());

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo = createStatusInfo(StatusDetailMessage.Level.WARN,
                LastSuccessfulStreamCheckingActor.NO_SUCCESSFUL_STREAM_YET_MESSAGE);
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithExceededErrorOffset() {
        // GIVEN: actor startup was more than syncWarningOffset in the past
        final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties =
                buildConfigProperties(true, searchSyncPersistence, syncWarningOffset, syncErrorOffset);

        final Instant startUpInstant = Instant.now().minus(syncErrorOffset.multipliedBy(2L));

        final Props props = Props.create(LastSuccessfulStreamCheckingActor.class, () ->
                new LastSuccessfulStreamCheckingActor(streamHealthCheckConfigurationProperties, startUpInstant));

        underTest = actorSystem.actorOf(props);

        // WHEN: last successful sync is over syncErrorOffset
        Instant lastSuccessfulStream = now().minusSeconds(syncErrorOffset.getSeconds() + 60);
        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd()).thenReturn(Optional.of(lastSuccessfulStream));

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
        final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties =
                buildConfigProperties(true, searchSyncPersistence, syncWarningOffset, syncErrorOffset);
        underTest =
                actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(streamHealthCheckConfigurationProperties));
        Instant lastSuccessfulStream = now().minusSeconds(syncWarningOffset.getSeconds() + 60);
        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd()).thenReturn(Optional.of(lastSuccessfulStream));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo =
                createStatusInfo(StatusDetailMessage.Level.WARN,
                        "End timestamp of last successful sync is about <21> " +
                                "minutes ago. Maximum duration before showing this warning is <PT20M>.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithExceptionWhenAskingForLastSyncTime() {
        final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties =
                buildConfigProperties(true, searchSyncPersistence, syncWarningOffset, syncErrorOffset);
        underTest =
                actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(streamHealthCheckConfigurationProperties));
        final IllegalStateException mockedEx = new IllegalStateException("Something happened");
        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd()).thenThrow(mockedEx);

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo =
                createStatusInfo(StatusDetailMessage.Level.ERROR, "An error occurred when asking for the end " +
                        "timestamp of last successful sync. Reason: <" + mockedEx.toString() + ">.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithSuccessfulStreamInTime() {
        final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties =
                buildConfigProperties(true, searchSyncPersistence, syncWarningOffset, syncErrorOffset);
        underTest =
                actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(streamHealthCheckConfigurationProperties));
        Instant lastSuccessfulStream = now().minusSeconds(syncWarningOffset.getSeconds() - 10);
        when(searchSyncPersistence.retrieveLastSuccessfulStreamEnd()).thenReturn(Optional.of(lastSuccessfulStream));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo = createStatusInfo(StatusDetailMessage.Level.INFO,
                "End timestamp of last successful sync is about <19> minutes ago.");
        expectStatusInfo(expectedStatusInfo);
    }

    @Test
    public void triggerHealthRetrievalWithDisabledSync() {
        final LastSuccessfulStreamCheckingActorConfigurationProperties streamHealthCheckConfigurationProperties =
                buildConfigProperties(false, searchSyncPersistence, syncWarningOffset, syncErrorOffset);
        underTest =
                actorSystem.actorOf(LastSuccessfulStreamCheckingActor.props(streamHealthCheckConfigurationProperties));

        sendRetrieveHealth();

        final StatusInfo expectedStatusInfo =
                createStatusInfo(StatusInfo.Status.UNKNOWN, StatusDetailMessage.Level.WARN,
                        SYNC_DISABLED_MESSAGE);
        expectStatusInfo(expectedStatusInfo);
        verify(searchSyncPersistence, never()).retrieveLastSuccessfulStreamEnd();
    }

    private static LastSuccessfulStreamCheckingActorConfigurationProperties buildConfigProperties(
            final boolean syncEnabled, final StreamMetadataPersistence streamMetadataPersistence,
            final Duration syncWarningOffset, final Duration syncErrorOffset) {

        return new LastSuccessfulStreamCheckingActorConfigurationProperties(syncEnabled, syncWarningOffset,
                syncErrorOffset, streamMetadataPersistence);
    }

    private void sendRetrieveHealth() {
        underTest.tell(RetrieveHealth.newInstance(), testKit.getRef());
    }

    private StatusInfo createStatusInfo(final StatusDetailMessage.Level level, final String message) {
        return StatusInfo.fromDetail(StatusDetailMessage.of(level, message));
    }

    private StatusInfo createStatusInfo(final StatusInfo.Status status, final StatusDetailMessage.Level level,
            final String message) {
        return StatusInfo.fromStatus(status, Collections.singleton(StatusDetailMessage.of(level, message)));
    }

    private void expectStatusInfo(final StatusInfo expectedStatusInfo) {
        final StatusInfo statusInfo = testKit.expectMsgClass(StatusInfo.class);
        assertThat(statusInfo).isEqualTo(expectedStatusInfo);
    }
}