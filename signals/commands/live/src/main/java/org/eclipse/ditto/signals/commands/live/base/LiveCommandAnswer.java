/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
