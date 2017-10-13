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
package org.eclipse.ditto.signals.commands.live.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;

/**
 * Abstract base implementation for all {@link LiveCommandAnswerBuilder}s for query commands.
 *
 * @param <C> the type of the LiveCommand
 * @param <R> the type of the LiveCommandResponseFactory to be used as function parameter for the {@link
 * #createResponseFunction}
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
abstract class AbstractLiveCommandAnswerBuilder<C extends LiveCommand, R extends LiveCommandResponseFactory>
        implements LiveCommandAnswerBuilder.QueryCommandResponseStep<R>, LiveCommandAnswerBuilder.BuildStep {

    // This variable being protected is no problem as it is a) immutable and b) not visible beyond "modify" package.
    protected final C command;
    private Function<R, CommandResponse<?>> createResponseFunction;

    /**
     * Constructs a new {@code AbstractLiveCommandAnswerBuilder} object.
     *
     * @param command the command to build an answer for.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    protected AbstractLiveCommandAnswerBuilder(final C command) {
        this.command = checkNotNull(command, "command");
        createResponseFunction = r -> null;
    }

    @Override
    public BuildStep withResponse(final Function<R, CommandResponse<?>> createResponseFunction) {
        this.createResponseFunction = checkNotNull(createResponseFunction, "function for creating a command response");
        return this;
    }

    @Override
    public BuildStep withoutResponse() {
        return this;
    }

    @Override
    public LiveCommandAnswer build() {
        final CommandResponse<?> commandResponse = doCreateResponse(createResponseFunction);

        return LiveCommandAnswerFactory.newLiveCommandAnswer(commandResponse);
    }

    /**
     * Creates a CommandResponse using the given Function.
     *
     * @param createResponseFunction the function for creating a CommandResponse with the help of the implied
     * LiveCommandResponseFactory.
     * @return the CommandResponse.
     */
    protected abstract CommandResponse doCreateResponse(Function<R, CommandResponse<?>> createResponseFunction);

}
