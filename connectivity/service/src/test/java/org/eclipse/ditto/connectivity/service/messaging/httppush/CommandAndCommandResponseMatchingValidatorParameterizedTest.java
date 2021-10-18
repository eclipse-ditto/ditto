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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

/**
 * Ensures that for each implementation of {@link ThingCommand} the associated implementation of
 * {@link ThingCommandResponse} passes {@link CommandAndCommandResponseMatchingValidator} without failure.
 * The same is ensured for implementations of {@link MessageCommand} with the associated implementations of
 * {@link MessageCommandResponse}.
 */
@RunWith(Parameterized.class)
public final class CommandAndCommandResponseMatchingValidatorParameterizedTest {

    @Parameterized.Parameter
    public SignalWithEntityId<?> command;

    @Parameterized.Parameter(1)
    public CommandResponse<?> commandResponse;

    private ConnectionLogger connectionLoggerMock;
    private CommandAndCommandResponseMatchingValidator underTest;

    @Before
    public void before() {
        connectionLoggerMock = Mockito.mock(ConnectionLogger.class);
        underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLoggerMock);
    }

    @Parameterized.Parameters(name = "Command: {0}, response: {1}")
    public static Object[][] getParameters() {
        final Map<SignalWithEntityId<?>, SignalWithEntityId<?>> commandsAndResponses = new HashMap<>();
        commandsAndResponses.putAll(getCommandsAndResponses(MessageCommand.class, MessageCommandResponse.class));
        commandsAndResponses.putAll(getCommandsAndResponses(ThingCommand.class, ThingCommandResponse.class));
        final var commandAndResponsesEntrySet = commandsAndResponses.entrySet();
        return commandAndResponsesEntrySet.stream()
                .map(entry -> new SignalWithEntityId<?>[]{entry.getKey(), entry.getValue()})
                .toArray(SignalWithEntityId<?>[][]::new);
    }

    private static <C extends SignalWithEntityId<?>, R extends SignalWithEntityId<?>> Map<SignalWithEntityId<?>, SignalWithEntityId<?>> getCommandsAndResponses(
            final Class<C> commandInterfaceClass,
            final Class<R> commandResponseInterfaceClass
    ) {
        final Map<SignalWithEntityId<?>, SignalWithEntityId<?>> result = new HashMap<>();

        final var commands = SignalInterfaceImplementations.newInstance(commandInterfaceClass);
        final var commandResponses = SignalInterfaceImplementations.newInstance(commandResponseInterfaceClass);
        commands.forEach(command -> {
            final var commandClass = command.getClass();
            final var commandClassSimpleName = commandClass.getSimpleName();
            commandResponses.getSignalBySimpleClassName(commandClassSimpleName + "Response")
                    .ifPresent(commandResponse -> result.put(command, commandResponse));
        });

        return result;
    }

    @Test
    public void commandAndItsResponseMatch() {
        assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();
        Mockito.verifyNoInteractions(connectionLoggerMock);
    }

}
