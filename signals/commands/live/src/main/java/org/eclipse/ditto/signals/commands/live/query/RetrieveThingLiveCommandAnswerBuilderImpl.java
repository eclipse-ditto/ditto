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

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

import org.eclipse.ditto.signals.commands.live.query.RetrieveThingLiveCommandAnswerBuilder.ResponseFactory;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link RetrieveThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveThingLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<RetrieveThingLiveCommand, ResponseFactory>
        implements RetrieveThingLiveCommandAnswerBuilder {

    private RetrieveThingLiveCommandAnswerBuilderImpl(final RetrieveThingLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveThingLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveThingLiveCommandAnswerBuilderImpl newInstance(final RetrieveThingLiveCommand command) {
        return new RetrieveThingLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {
        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @ParametersAreNonnullByDefault
    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Nonnull
        @Override
        public RetrieveThingResponse retrieved(final Thing thing) {
            return RetrieveThingResponse.of(command.getThingId(), thing, command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotAccessibleError() {
            return errorResponse(command.getThingId(), ThingNotAccessibleException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

}
