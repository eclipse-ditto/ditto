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

import java.util.stream.Stream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.models.signal.common.SignalInterfaceImplementations;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for former {@code SignalInformationPoint} - which was split up in Ditto 3.0.0 to the interfaces as static
 * methods in order to not create a module with dependencies to all entity modules.
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
    public void isLiveCommandForNullSignalReturnsFalse() {
        assertThat(Command.isLiveCommand(null)).isFalse();
    }

    @Test
    public void isLiveCommandForMessageCommandReturnsTrue() {
        messageCommands.forEach(
                messageCommand -> softly.assertThat(Command.isLiveCommand(messageCommand))
                        .as(getSimpleClassName(messageCommand))
                        .isTrue());
    }

    @Test
    public void isLiveCommandForThingCommandWithTwinChannelReturnsFalse() {
        final var dittoHeadersWithTwinChannel = DittoHeaders.newBuilder(dittoHeaders).channel("twin").build();
        thingCommands.stream()
                .map(thingCommand -> thingCommand.setDittoHeaders(dittoHeadersWithTwinChannel))
                .forEach(thingCommand -> softly.assertThat(Command.isLiveCommand(thingCommand))
                        .as(getSimpleClassName(thingCommand))
                        .isFalse());
    }

    @Test
    public void isLiveCommandForThingCommandWithLiveChannelReturnsTrue() {
        final var dittoHeadersWithLiveChannel = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();
        thingCommands.stream()
                .map(thingCommand -> thingCommand.setDittoHeaders(dittoHeadersWithLiveChannel))
                .forEach(thingCommand -> softly.assertThat(Command.isLiveCommand(thingCommand))
                        .as(getSimpleClassName(thingCommand))
                        .isTrue());
    }

    @Test
    public void isMessageCommandForNullReturnsFalse() {
        assertThat(MessageCommand.isMessageCommand(null)).isFalse();
    }

    @Test
    public void isMessageCommandForMessageCommandReturnsTrue() {
        messageCommands.forEach(
                messageCommand -> softly.assertThat(MessageCommand.isMessageCommand(messageCommand))
                        .as(getSimpleClassName(messageCommand))
                        .isTrue());
    }

    @Test
    public void isMessageCommandForThingCommandReturnsFalse() {
        thingCommands.forEach(thingCommand -> softly.assertThat(MessageCommand.isMessageCommand(thingCommand))
                .as(getSimpleClassName(thingCommand))
                .isFalse());
    }

    @Test
    public void isThingCommandForNullReturnsFalse() {
        assertThat(ThingCommand.isThingCommand(null)).isFalse();
    }

    @Test
    public void isThingCommandForThingCommandReturnsTrue() {
        thingCommands.forEach(thingCommand -> softly.assertThat(ThingCommand.isThingCommand(thingCommand))
                .as(getSimpleClassName(thingCommand))
                .isTrue());
    }

    @Test
    public void isThingCommandForMessageCommandReturnsFalse() {
        messageCommands.forEach(
                messageCommand -> softly.assertThat(ThingCommand.isThingCommand(messageCommand))
                        .as(getSimpleClassName(messageCommand))
                        .isFalse());
    }

    @Test
    public void isLiveCommandResponseForNullReturnsFalse() {
        assertThat(CommandResponse.isLiveCommandResponse(null)).isFalse();
    }

    @Test
    public void isLiveCommandResponseForMessageCommandResponseReturnsTrue() {
        messageCommandResponses.forEach(
                response -> softly.assertThat(CommandResponse.isLiveCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isLiveCommandResponseForThingCommandResponseWithoutChannelHeaderReturnsFalse() {
        thingCommandResponses.forEach(
                response -> softly.assertThat(CommandResponse.isLiveCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isFalse());
    }

    @Test
    public void isLiveCommandResponseForThingCommandResponseWithLiveChannelHeaderReturnsTrue() {
        final var dittoHeadersWithLiveChannel = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();
        thingCommandResponses.stream()
                .map(response -> response.setDittoHeaders(dittoHeadersWithLiveChannel))
                .forEach(response -> softly.assertThat(CommandResponse.isLiveCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isMessageCommandResponseForNullReturnsFalse() {
        assertThat(MessageCommandResponse.isMessageCommandResponse(null)).isFalse();
    }

    @Test
    public void isMessageCommandResponseForMessageCommandResponseReturnsTrue() {
        messageCommandResponses.forEach(
                response -> softly.assertThat(MessageCommandResponse.isMessageCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isMessageCommandResponseForThingCommandResponseReturnsFalse() {
        thingCommandResponses.forEach(
                response -> softly.assertThat(MessageCommandResponse.isMessageCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isFalse());
    }

    @Test
    public void isThingCommandResponseForNullReturnsFalse() {
        assertThat(ThingCommandResponse.isThingCommandResponse(null)).isFalse();
    }

    @Test
    public void isThingCommandResponseForThingCommandResponseReturnsTrue() {
        thingCommandResponses.forEach(
                response -> softly.assertThat(ThingCommandResponse.isThingCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isTrue());
    }

    @Test
    public void isThingCommandResponseForMessageCommandResponseReturnsFalse() {
        messageCommandResponses.forEach(
                response -> softly.assertThat(ThingCommandResponse.isThingCommandResponse(response))
                        .as(getSimpleClassName(response))
                        .isFalse());
    }

    @Test
    public void isChannelLiveForNullReturnsFalse() {
        assertThat(Signal.isChannelLive(null)).isFalse();
    }

    @Test
    public void isChannelLiveForSignalWithLiveChannelHeaderReturnsTrue() {
        final var dittoHeadersWithLiveChannel = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();
        final var signalMock = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(signalMock.getDittoHeaders()).thenReturn(dittoHeadersWithLiveChannel);

        assertThat(Signal.isChannelLive(signalMock)).isTrue();
    }

    @Test
    public void isChannelLiveForSignalWithTwinChannelHeaderReturnsTrue() {
        final var dittoHeadersWithTwinChannel = DittoHeaders.newBuilder(dittoHeaders).channel("twin").build();
        final var signalMock = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(signalMock.getDittoHeaders()).thenReturn(dittoHeadersWithTwinChannel);

        assertThat(Signal.isChannelLive(signalMock)).isFalse();
    }

    @Test
    public void isChannelLiveForSignalWithoutChannelHeaderReturnsTrue() {
        final var signalMock = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(signalMock.getDittoHeaders()).thenReturn(dittoHeaders);

        assertThat(Signal.isChannelLive(signalMock)).isFalse();
    }

    @Test
    public void isWithEntityIdForNullSignalReturnsFalse() {
        assertThat(WithEntityId.isWithEntityId(null)).isFalse();
    }

    @Test
    public void isWithEntityIdForSignalImplementingWithEntityIdReturnsTrue() {
        final var signal = RetrieveThing.of(ThingId.generateRandom(), dittoHeaders);

        assertThat(WithEntityId.isWithEntityId(signal)).isTrue();
    }

    @Test
    public void isWithEntityIdForSignalNotImplementingWithEntityIdReturnsFalse() {
        final var signal = RetrieveThings.getBuilder(ThingId.generateRandom()).dittoHeaders(dittoHeaders).build();

        assertThat(WithEntityId.isWithEntityId(signal)).isFalse();
    }

    @Test
    public void getEntityIdForNullSignalReturnsEmptyOptional() {
        assertThat(WithEntityId.getEntityId(null)).isEmpty();
    }

    @Test
    public void getEntityIdForSignalWithEntityIdReturnsOptionalWithEntityId() {
        final var signal = RetrieveThing.of(ThingId.generateRandom(), dittoHeaders);

        assertThat(WithEntityId.getEntityId(signal)).hasValue(signal.getEntityId());
    }

    @Test
    public void getEntityIdForSignalWithoutEntityIdReturnsEmptyOptional() {
        final var signal = RetrieveThings.getBuilder(ThingId.generateRandom()).dittoHeaders(dittoHeaders).build();

        assertThat(WithEntityId.getEntityId(signal)).isEmpty();
    }

    @Test
    public void getCorrelationIdFromNullSignalReturnsEmptyOptional() {
        assertThat(WithDittoHeaders.getCorrelationId(null)).isEmpty();
    }

    @Test
    public void getCorrelationIdFromSignalWithHeadersWithoutCorrelationIdReturnsEmptyCorrelationId() {
        final Signal<?> signal = Mockito.mock(Signal.class);
        Mockito.when(signal.getDittoHeaders()).thenReturn(DittoHeaders.empty());

        assertThat(WithDittoHeaders.getCorrelationId(null)).isEmpty();
    }

    @Test
    public void getCorrelationIdFromSignalWithCorrelationIdInHeadersReturnsThoseCorrelationId() {
        final Signal<?> signal = Mockito.mock(Signal.class);
        Mockito.when(signal.getDittoHeaders()).thenReturn(dittoHeaders);

        assertThat(WithDittoHeaders.getCorrelationId(signal)).isEqualTo(dittoHeaders.getCorrelationId());
    }

    @Test
    public void isCommandForCommandReturnsFalse() {
        Stream.concat(thingCommands.stream(), messageCommands.stream())
                .forEach(command -> softly.assertThat(Command.isCommand(command))
                        .as(command.getType())
                        .isTrue());
    }

    @Test
    public void isCommandForNullReturnsFalse() {
        assertThat(Command.isCommand(null)).isFalse();
    }

    @Test
    public void isCommandForCommandResponseReturnsFalse() {
        Stream.concat(thingCommandResponses.stream(), messageCommandResponses.stream())
                .forEach(commandResponse -> softly.assertThat(Command.isCommand(commandResponse))
                        .as(commandResponse.getType())
                        .isFalse());
    }

    @Test
    public void isCommandResponseForCommandResponseReturnsFalse() {
        Stream.concat(thingCommandResponses.stream(), messageCommandResponses.stream())
                .forEach(commandResponse -> softly.assertThat(CommandResponse.isCommandResponse(commandResponse))
                        .as(commandResponse.getType())
                        .isTrue());
    }

    @Test
    public void isCommandResponseForNullReturnsFalse() {
        assertThat(CommandResponse.isCommandResponse(null)).isFalse();
    }

    @Test
    public void isCommandForCommandResponseResponseReturnsFalse() {
        Stream.concat(thingCommands.stream(), messageCommands.stream())
                .forEach(command -> softly.assertThat(CommandResponse.isCommandResponse(command))
                        .as(command.getType())
                        .isFalse());
    }

    private static String getSimpleClassName(final Object o) {
        final var oClass = o.getClass();
        return oClass.getSimpleName();
    }

}
