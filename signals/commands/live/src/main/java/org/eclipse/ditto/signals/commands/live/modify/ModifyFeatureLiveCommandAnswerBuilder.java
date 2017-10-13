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
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link ModifyFeature}
 * commands.
 */
public interface ModifyFeatureLiveCommandAnswerBuilder extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        ModifyFeatureLiveCommandAnswerBuilder.ResponseFactory, ModifyFeatureLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyFeature} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyFeatureResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyFeatureResponse created();

        /**
         * Builds a "modified"  {@link ModifyFeatureResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyFeatureResponse modified();

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
     * Factory for events triggered by {@link ModifyFeature} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureCreated created();

        /**
         * Creates a {@link FeatureModified} event using the values of the {@code Command}.
         *
         * @return the FeatureModified event
         */
        @Nonnull
        FeatureModified modified();
    }

}
