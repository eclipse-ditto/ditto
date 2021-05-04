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
package org.eclipse.ditto.messages.model.signals.commands;

import org.eclipse.ditto.messages.model.Message;

/**
 * Gives access to a {@link org.eclipse.ditto.messages.model.Message} of a Signal.
 *
 * @param <P> the type of the message's payload.
 * @since 2.0.0
 */
public interface WithMessage<P> {

    /**
     * Returns the message to be delivered.
     *
     * @return the message.
     */
    Message<P> getMessage();

}
