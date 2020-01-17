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

import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Resolves the proper {@link Adapter} for the given {@link Adaptable}.
 */
public interface ThingCommandAdapters
        extends QueryCommandAdapterResolver<ThingQueryCommand<?>, ThingQueryCommandResponse<?>>,
        ModifyCommandAdapterResolver<ThingModifyCommand<?>, ThingModifyCommandResponse<?>>,
        MessageCommandAdapterResolver,
        ErrorResponseAdapterResolver<ThingErrorResponse>,
        EventAdapterResolver<ThingEvent<?>>,
        Adapters {

}
