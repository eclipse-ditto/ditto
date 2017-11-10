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
package org.eclipse.ditto.signals.commands.live.base;

import java.util.function.Function;

import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * A mutable builder with a fluent API for an immutable {@link LiveCommandAnswer}. This is the counterpart of {@link
 * LiveCommand} interface.
 */
@ParametersAreNonnullByDefault
public interface LiveCommandAnswerBuilder {

    /**
     * Interface for LiveCommandHandleResults which are {@code QueryCommands} (retrieving data).
     *
     * @param <R> the type of the LiveCommandResponseFactory to use for building {@link CommandResponse}s.
     */
    @ParametersAreNonnullByDefault
    interface QueryCommandResponseStep<R extends LiveCommandResponseFactory> extends LiveCommandAnswerBuilder {

        /**
         * Configures the function for creating a {@link CommandResponse} for the incoming {@code Command}.
         *
         * @param createResponseFunction the function used to build the {@code CommandResponse} to be returned.
         * @return the next step for building the answer.
         * @throws NullPointerException if {@code createResponseFunction} is {@code null}.
         */
        BuildStep withResponse(Function<R, CommandResponse<?>> createResponseFunction);

        /**
         * Configures that no {@link CommandResponse} is sent for the incoming {@code Command}.
         * <p>Be careful using this as the sender of the {@code Command} might wait for a {@code CommandResponse}.</p>
         *
         * @return the next step for building the answer.
         */
        BuildStep withoutResponse();
    }

    /**
     * Interface for LiveCommandHandleResults which are {@code ModifyCommands} (changing data).
     *
     * @param <R> the type of the LiveCommandResponseFactory to use for building {@link CommandResponse}s.
     * @param <E> the type of the LiveEventFactory to use for building {@link Event}s.
     */
    @ParametersAreNonnullByDefault
    interface ModifyCommandResponseStep<R extends LiveCommandResponseFactory, E extends LiveEventFactory> extends
            LiveCommandAnswerBuilder {

        /**
         * Configures the function for creating a {@link CommandResponse} for the incoming {@code Command}.
         *
         * @param createResponseFunction the function used to build the {@code CommandResponse} to be returned.
         * @return an EventStep LiveCommandAnswerBuilder in order to configure the {@code Event} to emit.
         * @throws NullPointerException if {@code createResponseFunction} is {@code null}.
         */
        EventStep<E> withResponse(Function<R, CommandResponse<?>> createResponseFunction);

        /**
         * Configures that no {@link CommandResponse} is sent for the incoming {@code Command}. <p> Be careful using
         * this as the sender of the {@code Command} might wait for a {@code CommandResponse}. </p>
         *
         * @return an EventStep LiveCommandAnswerBuilder in order to configure the {@code Event} to emit
         */
        EventStep<E> withoutResponse();
    }

    /**
     * Interface for LiveCommandHandleResults which emit {@code Event}s.
     *
     * @param <E> the type of the LiveEventFactory to use for building {@link Event}s
     */
    @ParametersAreNonnullByDefault
    interface EventStep<E extends LiveEventFactory> extends LiveCommandAnswerBuilder {

        /**
         * Configures the {@link Event} confirming the modifications requested in the incoming {@code Command}.
         *
         * @param createEventFunction the createEventFunction used to build the {@code Event} to be emitted
         * @return the next step for building the answer.
         * @throws NullPointerException if {@code createEventFunction} is {@code null}.
         */
        BuildStep withEvent(Function<E, Event<?>> createEventFunction);

        /**
         * Configures that no {@link Event} is sent for the incoming {@code Command}. <p> Be careful using this as the
         * sender of the {@code Command} or another party might expect or wait for {@code Event}s. </p>
         *
         * @return the next step for building the answer.
         */
        BuildStep withoutEvent();
    }

    /**
     * The final step for building the LiveCommandAnswer.
     */
    @SuppressWarnings("squid:S1609")
    interface BuildStep {

        /**
         * Builds the {@link LiveCommandAnswer} to a {@code LiveCommand}.
         *
         * @return the new LiveCommandAnswer object.
         */
        LiveCommandAnswer build();
    }

}
