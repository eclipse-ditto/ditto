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
package org.eclipse.ditto.protocol.adapter.provider;

import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;

/**
 * Interface providing the message command adapter and the message command response adapter.
 */
interface MessageCommandAdapterProvider {

    /**
     * @return the message command adapter
     */
    Adapter<MessageCommand<?, ?>> getMessageCommandAdapter();

    /**
     * @return the message command response adapter
     */
    Adapter<MessageCommandResponse<?, ?>> getMessageCommandResponseAdapter();

}
