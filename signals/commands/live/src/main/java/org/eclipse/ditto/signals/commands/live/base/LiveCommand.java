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

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * A Handle for live {@link Command}s giving access to the command. Also provides a {@code Command} specific {@link
 * LiveCommandAnswerBuilder} used for building {@code CommandResponse}s to return and {@code Event}s to emit for
 * incoming commands.
 *
 * @param <T> the type of the LiveCommand; currently needed as return type for {@link #setDittoHeaders(DittoHeaders)}.
 * @param <B> the type of the LiveCommandAnswerBuilder to be returned for {@link #answer()}.
 */
public interface LiveCommand<T extends Command<T>, B extends LiveCommandAnswerBuilder> extends Command<T> {

    /**
     * Returns a builder for an answer to this command which could include a {@code CommandResponse}s or an {@code
     * Event}. The answer is emitted automatically for the received {@code Command}.
     *
     * @return the LiveCommandAnswerBuilder for building responses and events.
     */
    @Nonnull
    B answer();

}
