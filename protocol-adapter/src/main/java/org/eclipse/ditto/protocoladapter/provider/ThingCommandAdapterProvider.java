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
package org.eclipse.ditto.protocoladapter.provider;

import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

/**
 * Provider for all thing command adapters. This interface only defines the generic type arguments.
 */
public interface ThingCommandAdapterProvider
        extends QueryCommandAdapterProvider<ThingQueryCommand<?>, ThingQueryCommandResponse<?>>,
        RetrieveThingsCommandAdapterProvider<RetrieveThings, RetrieveThingsResponse>,
        ModifyCommandAdapterProvider<ThingModifyCommand<?>, ThingModifyCommandResponse<?>>,
        MergeCommandAdapterProvider,
        MessageCommandAdapterProvider,
        ErrorResponseAdapterProvider<ThingErrorResponse>,
        EventAdapterProvider<ThingEvent<?>>,
        MergeEventAdapterProvider,
        SubscriptionEventAdapterProvider<SubscriptionEvent<?>>,
        ThingSearchCommandAdapterProvider<ThingSearchCommand<?>>,
        AdapterProvider {
}
