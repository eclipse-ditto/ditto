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
package org.eclipse.ditto.signals.commands.live.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link DeleteFeature}
 * commands.
 */
public interface DeleteFeatureLiveCommandAnswerBuilder extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        DeleteFeatureLiveCommandAnswerBuilder.ResponseFactory, DeleteFeatureLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link DeleteFeature} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteFeatureResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteFeatureResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException
         * FeatureNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featureNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotModifiableException
         * FeatureNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featureNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link DeleteFeature} command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDeleted deleted();
    }

}
