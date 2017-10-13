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
package org.eclipse.ditto.signals.commands.live.base;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * A factory for creating immutable instances of {@link LiveCommandAnswer}.
 */
@Immutable
public final class LiveCommandAnswerFactory {

    private LiveCommandAnswerFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable instance of {@link LiveCommandAnswer} with the given CommandResponse.
     *
     * @param commandResponse the command response of the returned instance.
     * @return the instance.
     */
    @Nonnull
    public static LiveCommandAnswer newLiveCommandAnswer(@Nullable final CommandResponse<?> commandResponse) {
        return ImmutableLiveCommandAnswer.newInstance(commandResponse, null);
    }

    /**
     * Returns a new immutable instance of {@link LiveCommandAnswer} with the given CommandResponse.
     *
     * @param event the event of the returned instance.
     * @return the instance.
     */
    @Nonnull
    public static LiveCommandAnswer newLiveCommandAnswer(@Nullable final Event<?> event) {
        return ImmutableLiveCommandAnswer.newInstance(null, event);
    }

    /**
     * Returns a new immutable instance of {@link LiveCommandAnswer} with the given CommandResponse and Event.
     *
     * @param commandResponse the command response of the returned instance.
     * @param event the event of the returned instance.
     * @return the instance.
     */
    @Nonnull
    public static LiveCommandAnswer newLiveCommandAnswer(@Nullable final CommandResponse<?> commandResponse,
            @Nullable final Event<?> event) {
        return ImmutableLiveCommandAnswer.newInstance(commandResponse, event);
    }

}
