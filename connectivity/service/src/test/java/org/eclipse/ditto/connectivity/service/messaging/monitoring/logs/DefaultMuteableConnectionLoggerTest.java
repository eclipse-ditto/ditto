/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link DefaultMuteableConnectionLogger}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultMuteableConnectionLoggerTest {

    private static final ConnectionMonitor.InfoProvider INFO_PROVIDER = InfoProviderFactory.empty();
    private static final String MESSAGE = "something happened to {0}";
    private static final Object[] MESSAGE_ARGUMENTS = {"ditto"};
    private static final ThingId THING_ID = ThingId.of("the:thing");
    private static final Signal<?> SIGNAL =
            RetrieveConnectionLogs.of(TestConstants.createRandomConnectionId(), DittoHeaders.empty());
    private static final DittoRuntimeException DITTO_RUNTIME_EXCEPTION = new ThingIdInvalidException("invalid");
    private static final Exception EXCEPTION = new IllegalArgumentException();

    @Mock
    private ConnectionLogger delegate;

    private DefaultMuteableConnectionLogger underTest;

    @Before
    public void before() {
        underTest = new DefaultMuteableConnectionLogger(TestConstants.createRandomConnectionId(), delegate);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DefaultMuteableConnectionLogger.class)
                .suppress(Warning.NONFINAL_FIELDS)
                .withIgnoredFields("logger")
                .verify();
    }

    @Test
    public void mute() {
        underTest.mute();

        assertThat(underTest.isMuted()).isTrue();
    }

    @Test
    public void unmute() {
        underTest.unmute();

        assertThat(underTest.isMuted()).isFalse();
    }

    @Test
    public void testMutingAndUnmuting() {

        // initially should be muted
        assertThat(underTest.isMuted()).isTrue();

        // can be unmuted
        underTest.unmute();
        assertThat(underTest.isMuted()).isFalse();

        // and be muted again
        underTest.mute();
        assertThat(underTest.isMuted()).isTrue();
    }

    @Test
    public void mutedLoggerShouldNotCallDelegate() {
        underTest.mute();
        underTest.success(INFO_PROVIDER);
        underTest.success(INFO_PROVIDER, MESSAGE, MESSAGE_ARGUMENTS);
        underTest.failure(INFO_PROVIDER, DITTO_RUNTIME_EXCEPTION);
        underTest.failure(SIGNAL, DITTO_RUNTIME_EXCEPTION);
        underTest.failure(INFO_PROVIDER);
        underTest.failure(INFO_PROVIDER, MESSAGE, MESSAGE_ARGUMENTS);
        underTest.exception(INFO_PROVIDER, EXCEPTION);
        underTest.exception(INFO_PROVIDER);
        underTest.exception(INFO_PROVIDER, MESSAGE, MESSAGE_ARGUMENTS);
        underTest.getLogs();

        Mockito.verifyNoInteractions(delegate);
    }

    @Test
    public void success() {
        underTest.unmute();

        underTest.success(INFO_PROVIDER);

        verify(delegate).success(INFO_PROVIDER);
    }

    @Test
    public void success1() {
        underTest.unmute();

        underTest.success(INFO_PROVIDER, MESSAGE, THING_ID);

        verify(delegate).success(INFO_PROVIDER, MESSAGE, THING_ID);
    }

    @Test
    public void failure() {
        underTest.unmute();

        underTest.failure(INFO_PROVIDER, DITTO_RUNTIME_EXCEPTION);

        verify(delegate).failure(INFO_PROVIDER, DITTO_RUNTIME_EXCEPTION);
    }

    @Test
    public void failure1() {
        underTest.unmute();

        underTest.failure(INFO_PROVIDER, MESSAGE, THING_ID);

        verify(delegate).failure(INFO_PROVIDER, MESSAGE, THING_ID);
    }

    @Test
    public void exception() {
        underTest.unmute();

        underTest.exception(INFO_PROVIDER, DITTO_RUNTIME_EXCEPTION);

        verify(delegate).exception(INFO_PROVIDER, DITTO_RUNTIME_EXCEPTION);
    }

    @Test
    public void exception1() {
        underTest.unmute();

        underTest.exception(INFO_PROVIDER, MESSAGE, THING_ID);

        verify(delegate).exception(INFO_PROVIDER, MESSAGE, THING_ID);
    }

    @Test
    public void getLogsFromMuted() {
        underTest.mute();

        assertThat(underTest.getLogs()).isEmpty();
        verify(delegate, Mockito.never()).getLogs();
    }

    @Test
    public void getLogsFromUnmuted() {
        underTest.unmute();

        final Collection<LogEntry> entries = List.of(Mockito.mock(LogEntry.class));
        when(delegate.getLogs()).thenReturn(entries);

        assertThat(underTest.getLogs()).isEqualTo(entries);
        verify(delegate).getLogs();
    }

    @Test
    public void exceptionInLoggingLeadsToExceptionalDelegate() {
        final var randomConnectionId = TestConstants.createRandomConnectionId();
        final var mockDelegate = Mockito.mock(ConnectionLogger.class);
        final var exception = new IllegalArgumentException();
        final var expected = logger(randomConnectionId, new ExceptionalConnectionLogger(randomConnectionId, exception));
        expected.unmute();
        doThrow(exception).when(mockDelegate).success(INFO_PROVIDER);
        final var underTest = logger(randomConnectionId, mockDelegate);
        underTest.unmute();

        assertThatNoException().as("Exception is not propagated").isThrownBy(() -> underTest.success(INFO_PROVIDER));
        assertThat(underTest).as("delegate changes to ExceptionalConnectionLogger").isEqualTo(expected);
    }

    private static DefaultMuteableConnectionLogger logger(final ConnectionId connectionId,
            final ConnectionLogger delegate) {

        return new DefaultMuteableConnectionLogger(connectionId, delegate);
    }

    @Test
    public void logEntryWithNullLogEntryOnUnmutedLoggerThrowsException() {
        underTest.unmute();

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.logEntry(null))
                .withMessage("The logEntry must not be null!")
                .withNoCause();
    }

    @Test
    public void logEntryInUnmutedStateDelegates() {
        underTest.unmute();

        final var logEntry = Mockito.mock(LogEntry.class);

        underTest.logEntry(logEntry);

        verify(delegate).logEntry(Mockito.eq(logEntry));
    }

    @Test
    public void logEntryInMutedStateDoesNotDelegate() {
        underTest.mute();

        final var logEntry = Mockito.mock(LogEntry.class);

        underTest.logEntry(logEntry);

        verify(delegate, Mockito.never()).logEntry(Mockito.eq(logEntry));
    }

}
