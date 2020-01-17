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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Resolves the proper {@link Adapter} for the given {@link Adaptable}.
 */
public interface PolicyCommandAdapters
        extends QueryCommandAdapterResolver<PolicyQueryCommand<?>, PolicyQueryCommandResponse<?>>,
        ModifyCommandAdapterResolver<PolicyModifyCommand<?>, PolicyModifyCommandResponse<?>>,
        ErrorResponseAdapterResolver<PolicyErrorResponse>,
        Adapters {

    @Override
    default Adapter<? extends MessageCommand<?, ?>> getMessageCommandAdapter() {
        return null;
    }

    @Override
    default Adapter<? extends MessageCommandResponse<?, ?>> getMessageCommandResponseAdapter() {
        return null;
    }

    @Override
    default Adapter<? extends Event<?>> getEventAdapter() {
        return null;
    }
}
