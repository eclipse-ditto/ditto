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
package org.eclipse.ditto.signals.commands.live.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder.BuildStep;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder.EventStep;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder.ModifyCommandResponseStep;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * Abstract base implementation for all {@link LiveCommandAnswerBuilder}s for modification commands.
 *
 * @param <C> the type of the LiveCommand
 * @param <R> the type of the LiveCommandResponseFactory to be used as function parameter for the {@link
 * #createResponseFunction}
 * @param <E> the type of the LiveEventFactory to bse used as function parameter for the {@link #createEventFunction}
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
abstract class AbstractLiveCommandAnswerBuilder<C extends LiveCommand, R extends LiveCommandResponseFactory, E extends LiveEventFactory>
        implements ModifyCommandResponseStep<R, E>, EventStep<E>, BuildStep {

    // This variable being protected is no problem as it is a) immutable and b) not visible beyond "modify" package.
    protected final C command;
    private Function<R, CommandResponse<?>> createResponseFunction;
    private Function<E, Event<?>> createEventFunction;

    /**
     * Constructs a new {@code AbstractLiveCommandAnswerBuilder} object.
     *
     * @param command the command to build an answer for.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    protected AbstractLiveCommandAnswerBuilder(final C command) {
        this.command = checkNotNull(command, "command");
        createResponseFunction = r -> null;
        createEventFunction = e -> null;
    }

    @Override
    public EventStep<E> withResponse(final Function<R, CommandResponse<?>> createResponseFunction) {
        this.createResponseFunction = checkNotNull(createResponseFunction, "function for creating a command response");
        return this;
    }

    @Override
    public EventStep<E> withoutResponse() {
        return this;
    }

    @Override
    public BuildStep withEvent(final Function<E, Event<?>> createEventFunction) {
        this.createEventFunction = checkNotNull(createEventFunction, "function for creating an event");
        return this;
    }

    @Override
    public BuildStep withoutEvent() {
        return this;
    }

    @Override
    public LiveCommandAnswer build() {
        final CommandResponse<?> commandResponse = doCreateResponse(createResponseFunction);
        final Event<?> event = doCreateEvent(createEventFunction);

        return LiveCommandAnswerFactory.newLiveCommandAnswer(commandResponse, event);
    }

    /**
     * Creates a CommandResponse using the given Function.
     *
     * @param createResponseFunction the function for creating a CommandResponse with the help of the implied
     * LiveCommandResponseFactory.
     * @return the CommandResponse.
     */
    @Nullable
    protected abstract CommandResponse doCreateResponse(Function<R, CommandResponse<?>> createResponseFunction);

    /**
     * Creates an Event using the given Function.
     *
     * @param createEventFunction the function for creating an Event with the help of the implied LiveEventFactory.
     * @return the Event.
     */
    @Nullable
    protected abstract Event doCreateEvent(Function<E, Event<?>> createEventFunction);

}
