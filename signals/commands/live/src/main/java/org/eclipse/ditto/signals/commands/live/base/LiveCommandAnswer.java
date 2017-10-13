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

import java.util.Optional;

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * This interface defines an answer for a received {@link LiveCommand}.
 */
public interface LiveCommandAnswer {

    /**
     * Returns the {@code CommandResponse} of this answer.
     *
     * @return the CommandResponse or an empty Optional.
     */
    @Nonnull
    Optional<CommandResponse> getResponse();

    /**
     * Returns the {@code Event} of this answer.
     *
     * @return the Event or an empty Optional.
     */
    @Nonnull
    Optional<Event> getEvent();

}
