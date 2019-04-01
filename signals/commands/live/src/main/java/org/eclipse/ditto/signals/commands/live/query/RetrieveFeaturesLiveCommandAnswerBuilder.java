/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveFeatures} commands.
 */
public interface RetrieveFeaturesLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveFeaturesLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveFeatures} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a success response containing the retrieved value for the {@link RetrieveFeatures} command.
         *
         * @param features the value of the requested Features.
         * @return the response.
         * @throws NullPointerException if {@code features} is {@code null}.
         */
        @Nonnull
        RetrieveFeaturesResponse retrieved(Features features);

        /**
         * Creates an error response specifying that no Features exist or the requesting user does not have enough
         * permission to retrieve them.
         *
         * @return the response.
         */
        @Nonnull
        ThingErrorResponse featuresNotAccessibleError();
    }

}
