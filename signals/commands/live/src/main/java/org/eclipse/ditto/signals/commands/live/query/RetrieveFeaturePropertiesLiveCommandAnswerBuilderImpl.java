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

import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;

import org.eclipse.ditto.signals.commands.live.query.RetrieveFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * RetrieveFeaturePropertiesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<RetrieveFeaturePropertiesLiveCommand, ResponseFactory>
        implements RetrieveFeaturePropertiesLiveCommandAnswerBuilder {

    private RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl(final RetrieveFeaturePropertiesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl newInstance(
            final RetrieveFeaturePropertiesLiveCommand command) {
        return new RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl(command);
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
        public RetrieveFeaturePropertiesResponse retrieved(final FeatureProperties featureProperties) {
            return RetrieveFeaturePropertiesResponse.of(command.getThingId(), command.getFeatureId(), featureProperties,
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featurePropertiesNotAccessibleError() {
            return errorResponse(command.getThingId(),
                    FeaturePropertiesNotAccessibleException.newBuilder(command.getThingId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

}
