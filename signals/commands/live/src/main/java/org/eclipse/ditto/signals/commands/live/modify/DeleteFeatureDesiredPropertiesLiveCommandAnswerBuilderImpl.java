/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertiesDeleted;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * DeleteFeatureDesiredPropertiesLiveCommand}.
 *
 * @since 1.5.0
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<DeleteFeatureDesiredPropertiesLiveCommand, DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder.ResponseFactory, DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder.EventFactory>
        implements DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder {

    private DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl(
            final DeleteFeatureDesiredPropertiesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl newInstance(
            final DeleteFeatureDesiredPropertiesLiveCommand command) {
        return new DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl(command);
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
        public DeleteFeatureDesiredPropertiesResponse deleted() {
            return DeleteFeatureDesiredPropertiesResponse.of(command.getThingEntityId(), command.getFeatureId(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featureDesiredPropertiesNotAccessibleError() {
            return errorResponse(command.getThingEntityId(),
                    FeatureDesiredPropertiesNotAccessibleException.newBuilder(command.getThingEntityId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featureDesiredPropertiesNotModifiableError() {
            return errorResponse(command.getThingEntityId(),
                    FeatureDesiredPropertiesNotModifiableException.newBuilder(command.getThingEntityId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public FeatureDesiredPropertiesDeleted deleted() {
            return FeatureDesiredPropertiesDeleted.of(command.getThingEntityId(), command.getFeatureId(), -1,
                    Instant.now(), command.getDittoHeaders(), null);
        }
    }

}
