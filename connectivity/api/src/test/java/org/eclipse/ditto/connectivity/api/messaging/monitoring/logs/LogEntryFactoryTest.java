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
package org.eclipse.ditto.connectivity.api.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mappingstrategies.IllegalAdaptableException;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link LogEntryFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class LogEntryFactoryTest {

    private static final String DETAIL_MESSAGE_FAILURE = "This is the failure detail message.";

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private Command<?> command;

    @Mock
    private CommandResponse<?> commandResponse;

    @Mock
    private Adaptable adaptable;

    @Mock
    private TopicPath topicPath;

    private DittoHeaders dittoHeadersWithCorrelationId;

    @Before
    public void before() {
        dittoHeadersWithCorrelationId =
                DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();

        final var emptyDittoHeaders = DittoHeaders.empty();
        Mockito.when(command.getDittoHeaders()).thenReturn(emptyDittoHeaders);
        Mockito.when(commandResponse.getDittoHeaders()).thenReturn(emptyDittoHeaders);
        Mockito.when(adaptable.getDittoHeaders()).thenReturn(dittoHeadersWithCorrelationId);
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(LogEntryFactory.class, areImmutable());
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithNullCommandThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(null,
                        commandResponse,
                        DETAIL_MESSAGE_FAILURE))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithNullCommandResponseThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                        null,
                        DETAIL_MESSAGE_FAILURE))
                .withMessage("The commandResponse must not be null!")
                .withNoCause();
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithNullDetailMessageThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                        commandResponse,
                        null))
                .withMessage("The detailMessage must not be null!")
                .withNoCause();
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithBlankDetailMessageThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                        commandResponse,
                        " "))
                .withMessage("The detailMessage must not be blank.")
                .withNoCause();
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithNoCorrelationIdReturnsExpected() {
        final var emptyDittoHeaders = DittoHeaders.empty();
        Mockito.when(command.getDittoHeaders()).thenReturn(emptyDittoHeaders);
        Mockito.when(commandResponse.getDittoHeaders()).thenReturn(emptyDittoHeaders);

        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getCorrelationId()).isEqualTo(LogEntryFactory.FALLBACK_CORRELATION_ID);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithCommandCorrelationIdOnlyReturnsExpected() {
        Mockito.when(command.getDittoHeaders()).thenReturn(dittoHeadersWithCorrelationId);

        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getCorrelationId()).isEqualTo(testNameCorrelationId.getCorrelationId().toString());
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripWithPrefersCommandCorrelationIdOnlyReturnsExpected() {
        Mockito.when(command.getDittoHeaders()).thenReturn(dittoHeadersWithCorrelationId);
        Mockito.lenient()
                .when(commandResponse.getDittoHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId("anotherCorrelationId").build());

        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getCorrelationId()).isEqualTo(testNameCorrelationId.getCorrelationId().toString());
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithTimestampCloseToNow() {
        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);
        final var instantNow = Instant.now();

        assertThat(logEntry.getTimestamp()).isBetween(instantNow.minusMillis(500L), instantNow);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithLogCategoryResponse() {
        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getLogCategory()).isEqualTo(LogCategory.RESPONSE);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithLogTypeDropped() {
        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getLogType()).isEqualTo(LogType.DROPPED);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithLogLevelFailure() {
        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getLogLevel()).isEqualTo(LogLevel.FAILURE);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithExpectedDetailMessage() {
        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getMessage()).isEqualTo(DETAIL_MESSAGE_FAILURE);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithoutEntityId() {
        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getEntityId()).isEmpty();
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithEntityIdOfCommand() {
        final var entityId = Mockito.mock(EntityId.class);
        final Command<?> commandWithEntityId =
                Mockito.mock(Command.class, Mockito.withSettings().extraInterfaces(WithEntityId.class));
        Mockito.when(commandWithEntityId.getDittoHeaders()).thenReturn(dittoHeadersWithCorrelationId);
        Mockito.when(((WithEntityId) commandWithEntityId).getEntityId()).thenReturn(entityId);

        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(commandWithEntityId,
                commandResponse,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getEntityId()).hasValue(entityId);
    }

    @Test
    public void getLogEntryForFailedCommandResponseRoundTripReturnsLogEntryWithEntityIdOfCommandResponse() {
        final var entityId = Mockito.mock(EntityId.class);
        final CommandResponse<?> commandResponseWithEntityId =
                Mockito.mock(CommandResponse.class, Mockito.withSettings().extraInterfaces(WithEntityId.class));
        Mockito.when(commandResponseWithEntityId.getDittoHeaders()).thenReturn(dittoHeadersWithCorrelationId);
        Mockito.when(((WithEntityId) commandResponseWithEntityId).getEntityId()).thenReturn(entityId);

        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                commandResponseWithEntityId,
                DETAIL_MESSAGE_FAILURE);

        assertThat(logEntry.getEntityId()).hasValue(entityId);
    }

    @Test
    public void getLogEntryForIllegalCommandResponseAdaptableWithNullIllegalAdaptableExceptionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForIllegalCommandResponseAdaptable(null,
                        DETAIL_MESSAGE_FAILURE))
                .withMessage("The illegalAdaptableException must not be null!")
                .withNoCause();
    }

    @Test
    public void getLogEntryForIllegalCommandResponseAdaptableWithNullDetailMessageThrowsException() {
        final var illegalAdaptableException = IllegalAdaptableException.newInstance(DETAIL_MESSAGE_FAILURE, adaptable);

        assertThatNullPointerException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForIllegalCommandResponseAdaptable(
                        illegalAdaptableException,
                        null))
                .withMessage("The detailMessage must not be null!")
                .withNoCause();
    }

    @Test
    public void getLogEntryForIllegalCommandResponseAdaptableWithBlankDetailMessageThrowsException() {
        final var illegalAdaptableException = IllegalAdaptableException.newInstance(DETAIL_MESSAGE_FAILURE, adaptable);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> LogEntryFactory.getLogEntryForIllegalCommandResponseAdaptable(
                        illegalAdaptableException,
                        " "))
                .withMessage("The detailMessage must not be blank.")
                .withNoCause();
    }

    @Test
    public void getLogEntryForIllegalCommandResponseAdaptableReturnsExpected() {
        final var thingId = ThingId.generateRandom();
        final var topicPath = TopicPath.newBuilder(thingId).live().things().commands().modify().build();
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
        final var illegalAdaptableException = IllegalAdaptableException.newInstance(DETAIL_MESSAGE_FAILURE, adaptable);
        final var logEntry = LogEntryFactory.getLogEntryForIllegalCommandResponseAdaptable(illegalAdaptableException,
                illegalAdaptableException.getMessage());

        final var instantNow = Instant.now();
        final var softly = new SoftAssertions();
        softly.assertThat(logEntry.getCorrelationId())
                .as("correlation ID")
                .isEqualTo(String.valueOf(testNameCorrelationId.getCorrelationId()));
        softly.assertThat(logEntry.getTimestamp()).as("timestamp").isBetween(instantNow.minusMillis(500L), instantNow);
        softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
        softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
        softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
        softly.assertThat(logEntry.getEntityId()).as("entity ID").contains(thingId);
        softly.assertAll();
    }

}
