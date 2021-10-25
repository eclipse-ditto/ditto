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

import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

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
        SearchErrorResponseAdapterProvider,
        EventAdapterProvider<ThingEvent<?>>,
        MergeEventAdapterProvider,
        SubscriptionEventAdapterProvider<SubscriptionEvent<?>>,
        ThingSearchCommandAdapterProvider<ThingSearchCommand<?>>,
        AdapterProvider {
}
