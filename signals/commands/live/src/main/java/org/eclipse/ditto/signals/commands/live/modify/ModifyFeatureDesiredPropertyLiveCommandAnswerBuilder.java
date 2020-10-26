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

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredPropertyResponse;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertyCreated;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertyModified;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link ModifyFeatureDesiredProperty}
 * commands.
 *
 * @since 1.5.0
 */
public interface ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder
        extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder.ResponseFactory, ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyFeatureDesiredProperty} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyFeatureDesiredPropertyResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyFeatureDesiredPropertyResponse created();

        /**
         * Builds a "modified"  {@link ModifyFeatureDesiredPropertyResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyFeatureDesiredPropertyResponse modified();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature's desired property was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertyNotAccessibleException
         * FeatureDesiredPropertyNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertyNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature's desired property was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertyNotModifiableException
         * FeatureDesiredPropertyNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertyNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link ModifyFeatureDesiredProperty} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureDesiredPropertyCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDesiredPropertyCreated created();

        /**
         * Creates a {@link FeatureDesiredPropertyModified} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDesiredPropertyModified modified();
    }

}
