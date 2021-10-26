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
package org.eclipse.ditto.internal.models.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.internal.models.signal.common.SignalInterfaceImplementations;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link SignalInformationPoint}.
 */
public final class SignalInformationPointTest {

    private static SignalInterfaceImplementations messageCommands;
    private static SignalInterfaceImplementations thingCommands;
    private static SignalInterfaceImplementations messageCommandResponses;
    private static SignalInterfaceImplementations thingCommandResponses;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private DittoHeaders dittoHeaders;

    @BeforeClass
    public static void beforeClass() {
        messageCommands = getSignalInterfaceImplementations(MessageCommand.class);
        thingCommands = getSignalInterfaceImplementations(ThingCommand.class);
        messageCommandResponses = getSignalInterfaceImplementations(MessageCommandResponse.class);
        thingCommandResponses = getSignalInterfaceImplementations(ThingCommandResponse.class);
    }

    private static <T extends SignalWithEntityId<?>> SignalInterfaceImplementations getSignalInterfaceImplementations(
            final Class<T> interfaceClass
    ) {
        final var result = SignalInterfaceImplementations.newInstance(interfaceClass);
        assertThat(result).as(interfaceClass.getSimpleName()).isNotEmpty();
        return result;
    }

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SignalInformationPoint.class, areImmutable());
    }

    @Test
    public void isLiveCommandForNullSignalReturnsFalse() {
        assertThat(SignalInformationPoint.isLiveCommand(null)).isFalse();
    }

    @Test
    public void isLiveCommandForMessageCommandReturnsTrue() {
        messageCommands.forEach(
                messageCommand -> softly.assertThat(SignalInformationPoint.isLiveCommand(messageCommand))
                        .as(getSimpleClassName(messageCommand))
                        .isTrue());
    }

    @Test
    public void isLiveCommandForThingCommandWithTwinChannelReturnsFalse() {
        final var dittoHeadersWithTwinChannel = DittoHeaders.newBuilder(dittoHeaders).channel("twin").build();
        thingCommands.stream()
                .map(thingCommand -> thingCommand.setDittoHeaders(dittoHeadersWithTwinChannel))
                .forEach(thingCommand -> softly.assertThat(SignalInformationPoint.isLiveCommand(thingCommand))
                        .as(getSimpleClassName(thingCommand))
                        .isFalse());
    }

    @Test
    public void isLiveCommandForThingCommandWithLiveChannelReturnsTrue() {
        final var dittoHeadersWithLiveChannel = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();
        thingCommands.stream()
                .map(thingCommand -> thingCommand.setDittoHeaders(dittoHeadersWithLiveChannel))
                .forEach(thingCommand -> softly.assertThat(SignalInformationPoint.isLiveCommand(thingCommand))
                        .as(getSimpleClassName(thingCommand))
                        .isTrue());
    }

    @Test
    public void isMessageCommandForNullReturnsFalse() {
        assertThat(SignalInformationPoint.isMessageCommand(null)).isFalse();
    }

    @Test
    public void isMessageCommandForMessageCommandReturnsTrue() {
        messageCommands.forEach(
                messageCommand -> softly.assertThat(SignalInformationPoint.isMessageCommand(messageCommand))
                        .as(getSimpleClassName(messageCommand))
                        .isTrue());
    }

    @Test
    public void isMessageCommandForThingCommandReturnsFalse() {
        thingCommands.forEach(thingCommand -> softly.assertThat(SignalInformationPoint.isMessageCommand(thingCommand))
                .as(getSimpleClassName(thingCommand))
                .isFalse());
    }

    @Test
    public void isThingCommandForNullReturnsFalse() {
        assertThat(SignalInformationPoint.isThingCommand(null)).isFalse();
    }

    @Test
    public void isThingCommandForThingCommandReturnsTrue() {
        thingCommands.forEach(thingCommand -> softly.assertThat(SignalInformationPoint.isThingCommand(thingCommand))
                .as(getSimpleClassName(thingCommand))
                .isTrue());
    }

    @Test
    public void isThingCommandForMessageCommandReturnsFalse() {
        messageCommands.forEach(
                messageCommand -> softly.assertThat(SignalInformationPoint.isThingCommand(messageCommand))
                        .as(getSimpleClassName(messageCommand))
                        .isFalse());
    }

    @Test
    public void isLiveCommandResponseForNullReturnsFalse() {
        assertThat(SignalInformationPoint.isLiveCommandResponse(null)).isFalse();
    }

    @Test
    public void isLiveCommandResponseForMessageCommandResponseReturnsTrue() {
        messageCommandResponses.forEach(
                response -> softly.assertThat(SignalInformationPoint.isLiveCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isLiveCommandResponseForThingCommandResponseWithoutChannelHeaderReturnsFalse() {
        thingCommandResponses.forEach(
                response -> softly.assertThat(SignalInformationPoint.isLiveCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isFalse());
    }

    @Test
    public void isLiveCommandResponseForThingCommandResponseWithLiveChannelHeaderReturnsTrue() {
        final var dittoHeadersWithLiveChannel = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();
        thingCommandResponses.stream()
                .map(response -> response.setDittoHeaders(dittoHeadersWithLiveChannel))
                .forEach(response -> softly.assertThat(SignalInformationPoint.isLiveCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isMessageCommandResponseForNullReturnsFalse() {
        assertThat(SignalInformationPoint.isMessageCommandResponse(null)).isFalse();
    }

    @Test
    public void isMessageCommandResponseForMessageCommandResponseReturnsTrue() {
        messageCommandResponses.forEach(
                response -> softly.assertThat(SignalInformationPoint.isMessageCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isMessageCommandResponseForThingCommandResponseReturnsFalse() {
        thingCommandResponses.forEach(
                response -> softly.assertThat(SignalInformationPoint.isMessageCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isFalse());
    }

    @Test
    public void isThingCommandResponseForNullReturnsFalse() {
        assertThat(SignalInformationPoint.isThingCommandResponse(null)).isFalse();
    }

    @Test
    public void isThingCommandResponseForThingCommandResponseReturnsTrue() {
        thingCommandResponses.forEach(
                response -> softly.assertThat(SignalInformationPoint.isThingCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isThingCommandResponseForMessageCommandResponseReturnsFalse() {
        messageCommandResponses.forEach(
                response -> softly.assertThat(SignalInformationPoint.isThingCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isFalse());
    }

    @Test
    public void isChannelLiveForNullReturnsFalse() {
        assertThat(SignalInformationPoint.isChannelLive(null)).isFalse();
    }

    @Test
    public void isChannelLiveForSignalWithLiveChannelHeaderReturnsTrue() {
        final var dittoHeadersWithLiveChannel = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();
        final var signalMock = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(signalMock.getDittoHeaders()).thenReturn(dittoHeadersWithLiveChannel);

        assertThat(SignalInformationPoint.isChannelLive(signalMock)).isTrue();
    }

    @Test
    public void isChannelLiveForSignalWithTwinChannelHeaderReturnsTrue() {
        final var dittoHeadersWithTwinChannel = DittoHeaders.newBuilder(dittoHeaders).channel("twin").build();
        final var signalMock = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(signalMock.getDittoHeaders()).thenReturn(dittoHeadersWithTwinChannel);

        assertThat(SignalInformationPoint.isChannelLive(signalMock)).isFalse();
    }

    @Test
    public void isChannelLiveForSignalWithoutChannelHeaderReturnsTrue() {
        final var signalMock = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(signalMock.getDittoHeaders()).thenReturn(dittoHeaders);

        assertThat(SignalInformationPoint.isChannelLive(signalMock)).isFalse();
    }

    private static String getSimpleClassName(final Object o) {
        final var oClass = o.getClass();
        return oClass.getSimpleName();
    }

}
