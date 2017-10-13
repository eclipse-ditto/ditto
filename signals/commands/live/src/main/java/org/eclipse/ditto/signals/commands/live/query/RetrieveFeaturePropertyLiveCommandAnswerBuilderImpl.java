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

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * RetrieveFeaturePropertyLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveFeaturePropertyLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<RetrieveFeaturePropertyLiveCommand, RetrieveFeaturePropertyLiveCommandAnswerBuilder.ResponseFactory>
        implements RetrieveFeaturePropertyLiveCommandAnswerBuilder {

    private RetrieveFeaturePropertyLiveCommandAnswerBuilderImpl(final RetrieveFeaturePropertyLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveFeaturePropertyLiveCommandAnswerBuilderImpl newInstance(
            final RetrieveFeaturePropertyLiveCommand command) {
        return new RetrieveFeaturePropertyLiveCommandAnswerBuilderImpl(command);
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
        public RetrieveFeaturePropertyResponse retrieved(final JsonValue propertyValue) {
            return RetrieveFeaturePropertyResponse.of(command.getThingId(), command.getFeatureId(),
                    command.getPropertyPointer(),
                    propertyValue, command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featurePropertyNotAccessibleError() {
            return errorResponse(command.getThingId(),
                    FeaturePropertyNotAccessibleException.newBuilder(command.getThingId(),
                            command.getFeatureId(), command.getPropertyPointer())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

}
