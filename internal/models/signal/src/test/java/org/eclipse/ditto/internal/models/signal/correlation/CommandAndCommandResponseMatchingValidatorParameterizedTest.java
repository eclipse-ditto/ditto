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
package org.eclipse.ditto.internal.models.signal.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.models.signal.common.SignalInterfaceImplementations;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Ensures that for each implementation of {@link ThingCommand} the associated implementation of
 * {@link ThingCommandResponse} passes {@link CommandAndCommandResponseMatchingValidator} without failure.
 * The same is ensured for implementations of {@link MessageCommand} with the associated implementations of
 * {@link MessageCommandResponse}.
 */
@RunWith(Enclosed.class)
public final class CommandAndCommandResponseMatchingValidatorParameterizedTest {

    private static final SignalInterfaceImplementations MESSAGE_COMMANDS =
            SignalInterfaceImplementations.newInstance(MessageCommand.class);

    private static final SignalInterfaceImplementations MESSAGE_COMMAND_RESPONSES =
            SignalInterfaceImplementations.newInstance(MessageCommandResponse.class);

    private static final SignalInterfaceImplementations THING_COMMANDS =
            SignalInterfaceImplementations.newInstance(ThingCommand.class);

    private static final SignalInterfaceImplementations THING_COMMAND_RESPONSES =
            SignalInterfaceImplementations.newInstance(ThingCommandResponse.class);

    public static final class ReflectionBasedInstantiationTest {

        @Test
        public void allMessageCommandsCouldBeInstantiated() {
            assertThat(MESSAGE_COMMANDS.getFailures()).isEmpty();
        }

        @Test
        public void allMessageCommandResponsesCouldBeInstantiated() {
            assertThat(MESSAGE_COMMAND_RESPONSES.getFailures()).isEmpty();
        }

        @Test
        public void allThingCommandsCouldBeInstantiated() {
            assertThat(THING_COMMANDS.getFailures()).isEmpty();
        }

        @Test
        public void allThingCommandResponsesCouldBeInstantiated() {
            assertThat(THING_COMMAND_RESPONSES.getFailures()).isEmpty();
        }

    }

    @RunWith(Parameterized.class)
    public static final class ParameterizedTest {

        @Parameterized.Parameter
        public Command<?> command;

        @Parameterized.Parameter(1)
        public CommandResponse<?> commandResponse;

        private CommandAndCommandResponseMatchingValidator underTest;

        @Before
        public void before() {
            underTest = CommandAndCommandResponseMatchingValidator.getInstance();
        }

        @Parameterized.Parameters(name = "Command: {0}, response: {1}")
        public static Object[][] getParameters() {
            final Map<SignalWithEntityId<?>, SignalWithEntityId<?>> commandsAndResponses = new HashMap<>();
            commandsAndResponses.putAll(getCommandsAndResponses(MESSAGE_COMMANDS, MESSAGE_COMMAND_RESPONSES));
            commandsAndResponses.putAll(getCommandsAndResponses(THING_COMMANDS, THING_COMMAND_RESPONSES));
            final var commandAndResponsesEntrySet = commandsAndResponses.entrySet();
            return commandAndResponsesEntrySet.stream()
                    .map(entry -> new SignalWithEntityId<?>[]{entry.getKey(), entry.getValue()})
                    .toArray(SignalWithEntityId<?>[][]::new);
        }

        private static Map<SignalWithEntityId<?>, SignalWithEntityId<?>> getCommandsAndResponses(
                final SignalInterfaceImplementations commands,
                final SignalInterfaceImplementations commandResponses
        ) {
            final Map<SignalWithEntityId<?>, SignalWithEntityId<?>> result = new HashMap<>();

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
            final var validationResult = underTest.apply(command, commandResponse);

            assertThat(validationResult.isSuccess())
                    .withFailMessage(() -> {
                        final var failure = validationResult.asFailureOrThrow();
                        return failure.getDetailMessage();
                    })
                    .isTrue();
        }

    }

}
