/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * TODO javadoc
 */
public interface Adapters {

    /**
     * @return the query command adapter
     */
    Adapter<? extends Signal<?>> getQueryCommandAdapter();

    /**
     * @return the query command response adapter
     */
    Adapter<? extends CommandResponse<?>> getQueryCommandResponseAdapter();

    /**
     * @return the modify command adapter
     */
    Adapter<? extends Signal<?>> getModifyCommandAdapter();

    /**
     * @return the modify command response adapter
     */
    Adapter<? extends CommandResponse<?>> getModifyCommandResponseAdapter();

    /**
     * @return the message command adapter
     */
    Adapter<? extends MessageCommand<?, ?>> getMessageCommandAdapter();

    /**
     * @return the message command response adapter
     */
    Adapter<? extends MessageCommandResponse<?, ?>> getMessageCommandResponseAdapter();

    /**
     * @return the event adapter
     */
    Adapter<? extends Event<?>> getEventAdapter();

    /**
     * @return the error response adapter
     */
    Adapter<? extends ErrorResponse<?>> getErrorResponseAdapter();

}
