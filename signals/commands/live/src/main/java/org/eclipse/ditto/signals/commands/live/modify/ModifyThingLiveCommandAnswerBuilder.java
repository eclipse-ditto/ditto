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
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingModified;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link ModifyThing}
 * commands.
 */
public interface ModifyThingLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<ModifyThingLiveCommandAnswerBuilder.ResponseFactory,
                ModifyThingLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyThing} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyThingResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyThingResponse created();

        /**
         * Builds a "modified"  {@link ModifyThingResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyThingResponse modified();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Thing was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException
         * ThingNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse thingNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Thing was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException
         * ThingNotModifiableException
         */
        @Nonnull
        ThingErrorResponse thingNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link ModifyThing} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link ThingCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        ThingCreated created();

        /**
         * Creates a {@link ThingModified} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        ThingModified modified();
    }

}
