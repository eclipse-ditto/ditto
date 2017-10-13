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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Immutable implementation of {@link LiveCommandAnswer}.
 */
@Immutable
final class ImmutableLiveCommandAnswer implements LiveCommandAnswer {

    private final CommandResponse<?> commandResponse;
    private final Event<?> event;

    private ImmutableLiveCommandAnswer(final CommandResponse<?> theCommandResponse, final Event<?> theEvent) {
        commandResponse = theCommandResponse;
        event = theEvent;
    }

    /**
     * Returns a new instance of {@code ImmutableCommandAnswer}.
     *
     * @return the instance.
     */
    public static ImmutableLiveCommandAnswer newInstance(@Nullable final CommandResponse<?> commandResponse,
            @Nullable final Event<?> event) {
        return new ImmutableLiveCommandAnswer(commandResponse, event);
    }

    @Nonnull
    @Override
    public Optional<CommandResponse> getResponse() {
        return Optional.ofNullable(commandResponse);
    }

    @Nonnull
    @Override
    public Optional<Event> getEvent() {
        return Optional.ofNullable(event);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableLiveCommandAnswer that = (ImmutableLiveCommandAnswer) o;
        return Objects.equals(commandResponse, that.commandResponse)
                && Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandResponse, event);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "commandResponse=" + commandResponse + ", event="
                + event + "]";
    }

}
