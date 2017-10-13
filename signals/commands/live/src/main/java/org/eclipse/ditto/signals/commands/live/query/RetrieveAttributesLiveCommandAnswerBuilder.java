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

import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveAttributes} commands.
 */
public interface RetrieveAttributesLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveAttributesLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveAttributes} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a {@link RetrieveAttributesResponse} containing the retrieved value for the {@link
         * RetrieveAttributes} command.
         *
         * @param attributes the value of the attributes.
         * @return the response.
         * @throws NullPointerException if {@code attributes} is {@code null}.
         */
        @Nonnull
        RetrieveAttributesResponse retrieved(Attributes attributes);

        /**
         * Creates a {@link ThingErrorResponse} specifying that no attribute exists or the requesting user does not have
         * enough permission to retrieve it.
         *
         * @return the response.
         */
        @Nonnull
        ThingErrorResponse attributesNotAccessibleError();
    }

}
