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

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveFeatureProperty} commands.
 */
public interface RetrieveFeaturePropertyLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveFeaturePropertyLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveFeatureProperty} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a success response containing the retrieved value for the {@link RetrieveFeatureProperty} command.
         *
         * @param propertyValue the value of the requested Feature property.
         * @return the response.
         * @throws NullPointerException if {@code propertyValue} is {@code null}.
         */
        @Nonnull
        RetrieveFeaturePropertyResponse retrieved(JsonValue propertyValue);

        /**
         * Creates an error response specifying that the requested Feature property does not exist or the requesting
         * user does not have enough permission to retrieve them.
         *
         * @return the response.
         */
        @Nonnull
        ThingErrorResponse featurePropertyNotAccessibleError();
    }

}
