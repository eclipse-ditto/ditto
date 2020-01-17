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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;

/**
 * Resolves the propert {@link Adapter} for the given {@link Adaptable}. Subclasses should extend the abstract class
 * {@link AbstractAdapterResolver} to provide the implementations of the {@link
 * Adapter}s.
 */
public interface MessageCommandAdapterResolver {

    /**
     * @return the message command adapter
     */
    Adapter<MessageCommand<?, ?>> getMessageCommandAdapter();

    /**
     * @return the message command response adapter
     */
    Adapter<MessageCommandResponse<?, ?>> getMessageCommandResponseAdapter();

}
