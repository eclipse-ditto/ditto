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
package org.eclipse.ditto.protocoladapter.things;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ThingCommandAdapters;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;


/**
 * Instantiates {@link Adapter}s used to process thing commands, responses, messages, events and errors.
 */
public class DefaultThingCommandAdapters implements ThingCommandAdapters {

    private final ThingQueryCommandAdapter queryCommandAdapter;
    private final ThingModifyCommandAdapter modifyCommandAdapter;
    private final ThingQueryCommandResponseAdapter queryCommandResponseAdapter;
    private final ThingModifyCommandResponseAdapter modifyCommandResponseAdapter;
    private final MessageCommandAdapter messageCommandAdapter;
    private final MessageCommandResponseAdapter messageCommandResponseAdapter;
    private final ThingEventAdapter eventAdapter;
    private final ThingErrorResponseAdapter errorResponseAdapter;

    public DefaultThingCommandAdapters(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.queryCommandAdapter = ThingQueryCommandAdapter.of(headerTranslator);
        this.modifyCommandAdapter = ThingModifyCommandAdapter.of(headerTranslator);
        this.queryCommandResponseAdapter = ThingQueryCommandResponseAdapter.of(headerTranslator);
        this.modifyCommandResponseAdapter = ThingModifyCommandResponseAdapter.of(headerTranslator);
        this.messageCommandAdapter = MessageCommandAdapter.of(headerTranslator);
        this.messageCommandResponseAdapter = MessageCommandResponseAdapter.of(headerTranslator);
        this.eventAdapter = ThingEventAdapter.of(headerTranslator);
        this.errorResponseAdapter = ThingErrorResponseAdapter.of(headerTranslator, errorRegistry);
    }

    @Override
    public Adapter<ThingErrorResponse> getErrorResponseAdapter() {
        return errorResponseAdapter;
    }

    @Override
    public Adapter<ThingEvent<?>> getEventAdapter() {
        return eventAdapter;
    }

    @Override
    public Adapter<ThingModifyCommand<?>> getModifyCommandAdapter() {
        return modifyCommandAdapter;
    }

    @Override
    public Adapter<ThingModifyCommandResponse<?>> getModifyCommandResponseAdapter() {
        return modifyCommandResponseAdapter;
    }

    @Override
    public Adapter<ThingQueryCommand<?>> getQueryCommandAdapter() {
        return queryCommandAdapter;
    }

    @Override
    public Adapter<ThingQueryCommandResponse<?>> getQueryCommandResponseAdapter() {
        return queryCommandResponseAdapter;
    }

    @Override
    public Adapter<MessageCommand<?, ?>> getMessageCommandAdapter() {
        return messageCommandAdapter;
    }

    @Override
    public Adapter<MessageCommandResponse<?, ?>> getMessageCommandResponseAdapter() {
        return messageCommandResponseAdapter;
    }
}