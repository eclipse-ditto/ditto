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
/*
 * Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved.
 */
package org.eclipse.ditto.signals.commands.live.modify;

import java.time.Instant;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer LiveCommandAnswer} for a {@link
 * DeleteFeaturesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class DeleteFeaturesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<DeleteFeaturesLiveCommand, DeleteFeaturesLiveCommandAnswerBuilder.ResponseFactory, DeleteFeaturesLiveCommandAnswerBuilder.EventFactory>
        implements DeleteFeaturesLiveCommandAnswerBuilder {

    private DeleteFeaturesLiveCommandAnswerBuilderImpl(final DeleteFeaturesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteFeaturesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static DeleteFeaturesLiveCommandAnswerBuilderImpl newInstance(final DeleteFeaturesLiveCommand command) {
        return new DeleteFeaturesLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {
        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @Override
    protected Event doCreateEvent(final Function<EventFactory, Event<?>> createEventFunction) {
        return createEventFunction.apply(new EventFactoryImpl());
    }

    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Nonnull
        @Override
        public DeleteFeaturesResponse deleted() {
            return DeleteFeaturesResponse.of(command.getThingId(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featuresNotAccessibleError() {
            return errorResponse(command.getThingId(), FeaturesNotAccessibleException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featuresNotModifiableError() {
            return errorResponse(command.getThingId(), FeaturesNotModifiableException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public FeaturesDeleted deleted() {
            return FeaturesDeleted.of(command.getThingId(), -1, Instant.now(), command.getDittoHeaders());
        }
    }

}
