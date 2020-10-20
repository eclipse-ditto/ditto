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

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertyResponse;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveFeatureDesiredProperty} commands.
 *
 * @since 1.4.0
 */
public interface RetrieveFeatureDesiredPropertyLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveFeatureDesiredPropertyLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveFeatureDesiredProperty} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a success response containing the retrieved value for the {@link RetrieveFeatureDesiredProperty} command.
         *
         * @param desiredPropertyValue the value of the requested Feature's desired property.
         * @return the response.
         * @throws NullPointerException if {@code propertyValue} is {@code null}.
         */
        @Nonnull
        RetrieveFeatureDesiredPropertyResponse retrieved(JsonValue desiredPropertyValue);

        /**
         * Creates an error response specifying that the requested Feature's desired property does not exist or the requesting
         * user does not have enough permission to retrieve them.
         *
         * @return the response.
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertyNotAccessibleError();
    }

}
