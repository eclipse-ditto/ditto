/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;
import org.eclipse.ditto.signals.events.things.ThingMerged;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for
 * {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing} commands.
 */
public interface MergeThingLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<MergeThingLiveCommandAnswerBuilder.ResponseFactory,
                MergeThingLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing}
     * command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "merged"  {@link org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse} using the
         * values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        MergeThingResponse merged();

        /**
         * Builds a {@link org.eclipse.ditto.signals.commands.things.ThingErrorResponse} indicating that the Thing was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException
         * ThingNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse thingNotAccessibleError();

        /**
         * Builds a {@link org.eclipse.ditto.signals.commands.things.ThingErrorResponse} indicating that the Thing was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException
         * ThingNotModifiableException
         */
        @Nonnull
        ThingErrorResponse thingNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link org.eclipse.ditto.signals.events.things.ThingMerged} event using the values of the {@code
         * Command}.
         *
         * @return the event.
         */
        @Nonnull
        ThingMerged merged();

    }

}
