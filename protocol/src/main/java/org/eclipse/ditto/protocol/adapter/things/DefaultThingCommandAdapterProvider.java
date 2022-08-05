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
package org.eclipse.ditto.protocol.adapter.things;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.protocol.adapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.thingsearch.model.signals.commands.SearchErrorResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;


/**
 * Instantiates and provides {@link Adapter}s used to process Thing commands, responses, messages, events and errors.
 */
public class DefaultThingCommandAdapterProvider implements ThingCommandAdapterProvider {

    private final ThingQueryCommandAdapter queryCommandAdapter;
    private final ThingModifyCommandAdapter modifyCommandAdapter;
    private final ThingMergeCommandAdapter mergeCommandAdapter;
    private final ThingQueryCommandResponseAdapter queryCommandResponseAdapter;
    private final ThingModifyCommandResponseAdapter modifyCommandResponseAdapter;
    private final ThingMergeCommandResponseAdapter mergeCommandResponseAdapter;
    private final ThingSearchCommandAdapter searchCommandAdapter;
    private final MessageCommandAdapter messageCommandAdapter;
    private final MessageCommandResponseAdapter messageCommandResponseAdapter;
    private final ThingEventAdapter thingEventAdapter;
    private final ThingMergedEventAdapter thingMergedEventAdapter;
    private final SubscriptionEventAdapter subscriptionEventAdapter;
    private final ThingErrorResponseAdapter errorResponseAdapter;
    private final RetrieveThingsCommandAdapter retrieveThingsCommandAdapter;
    private final RetrieveThingsCommandResponseAdapter retrieveThingsCommandResponseAdapter;
    private final SearchErrorResponseAdapter searchErrorResponseAdapter;

    public DefaultThingCommandAdapterProvider(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.queryCommandAdapter = ThingQueryCommandAdapter.of(headerTranslator);
        this.retrieveThingsCommandAdapter = RetrieveThingsCommandAdapter.of(headerTranslator);
        this.retrieveThingsCommandResponseAdapter = RetrieveThingsCommandResponseAdapter.of(headerTranslator);
        this.modifyCommandAdapter = ThingModifyCommandAdapter.of(headerTranslator);
        this.mergeCommandAdapter = ThingMergeCommandAdapter.of(headerTranslator);
        this.queryCommandResponseAdapter = ThingQueryCommandResponseAdapter.of(headerTranslator);
        this.modifyCommandResponseAdapter = ThingModifyCommandResponseAdapter.of(headerTranslator);
        this.mergeCommandResponseAdapter = ThingMergeCommandResponseAdapter.of(headerTranslator);
        this.searchCommandAdapter = ThingSearchCommandAdapter.of(headerTranslator);
        this.messageCommandAdapter = MessageCommandAdapter.of(headerTranslator);
        this.messageCommandResponseAdapter = MessageCommandResponseAdapter.of(headerTranslator);
        this.thingEventAdapter = ThingEventAdapter.of(headerTranslator);
        this.thingMergedEventAdapter = ThingMergedEventAdapter.of(headerTranslator);
        this.subscriptionEventAdapter = SubscriptionEventAdapter.of(headerTranslator, errorRegistry);
        this.errorResponseAdapter = ThingErrorResponseAdapter.of(headerTranslator, errorRegistry);
        this.searchErrorResponseAdapter = SearchErrorResponseAdapter.of(headerTranslator, errorRegistry);
    }

    @Override
    public List<Adapter<?>> getAdapters() {
        return Arrays.asList(
                queryCommandAdapter,
                retrieveThingsCommandAdapter,
                retrieveThingsCommandResponseAdapter,
                modifyCommandAdapter,
                mergeCommandAdapter,
                queryCommandResponseAdapter,
                modifyCommandResponseAdapter,
                mergeCommandResponseAdapter,
                messageCommandAdapter,
                messageCommandResponseAdapter,
                thingEventAdapter,
                thingMergedEventAdapter,
                searchCommandAdapter,
                subscriptionEventAdapter,
                errorResponseAdapter,
                searchErrorResponseAdapter
        );
    }

    @Override
    public Adapter<ThingErrorResponse> getErrorResponseAdapter() {
        return errorResponseAdapter;
    }

    @Override
    public Adapter<ThingEvent<?>> getEventAdapter() {
        return thingEventAdapter;
    }

    @Override
    public Adapter<ThingMerged> getMergedEventAdapter() {
        return thingMergedEventAdapter;
    }

    @Override
    public Adapter<SubscriptionEvent<?>> getSubscriptionEventAdapter() {
        return subscriptionEventAdapter;
    }

    @Override
    public Adapter<ThingModifyCommand<?>> getModifyCommandAdapter() {
        return modifyCommandAdapter;
    }

    @Override
    public Adapter<MergeThing> getMergeCommandAdapter() {
        return mergeCommandAdapter;
    }

    @Override
    public Adapter<ThingModifyCommandResponse<?>> getModifyCommandResponseAdapter() {
        return modifyCommandResponseAdapter;
    }

    @Override
    public Adapter<MergeThingResponse> getMergeCommandResponseAdapter() {
        return mergeCommandResponseAdapter;
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

    @Override
    public Adapter<ThingSearchCommand<?>> getSearchCommandAdapter() {
        return searchCommandAdapter;
    }

    @Override
    public Adapter<RetrieveThings> getRetrieveThingsCommandAdapter() {
        return retrieveThingsCommandAdapter;
    }

    @Override
    public Adapter<RetrieveThingsResponse> getRetrieveThingsCommandResponseAdapter() {
        return retrieveThingsCommandResponseAdapter;
    }

    @Override
    public Adapter<SearchErrorResponse> getSearchErrorResponseAdapter() {
        return searchErrorResponseAdapter;
    }
}
