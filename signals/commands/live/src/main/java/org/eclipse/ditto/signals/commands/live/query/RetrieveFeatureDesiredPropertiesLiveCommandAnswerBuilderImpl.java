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
package org.eclipse.ditto.signals.commands.live.query;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.live.query.RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder.ResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertiesResponse;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * RetrieveFeatureDesiredPropertiesLiveCommand}.
 *
 * @since 1.5.0
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<RetrieveFeatureDesiredPropertiesLiveCommand, ResponseFactory>
        implements RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder {

    private RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl(final RetrieveFeatureDesiredPropertiesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl newInstance(
            final RetrieveFeatureDesiredPropertiesLiveCommand command) {
        return new RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl(command);
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
        public RetrieveFeatureDesiredPropertiesResponse retrieved(final FeatureProperties desiredProperties) {
            return RetrieveFeatureDesiredPropertiesResponse.of(command.getThingEntityId(), command.getFeatureId(), desiredProperties,
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
    }

}
