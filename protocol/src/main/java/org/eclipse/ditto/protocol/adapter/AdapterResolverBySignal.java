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
package org.eclipse.ditto.protocol.adapter;

import static org.eclipse.ditto.protocol.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.TWIN;

import java.util.Arrays;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionEvent;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownChannelException;
import org.eclipse.ditto.protocol.UnknownSignalException;
import org.eclipse.ditto.protocol.adapter.connectivity.ConnectivityCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.AcknowledgementAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.PolicyCommandAdapterProvider;
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

final class AdapterResolverBySignal {

    private final ThingCommandAdapterProvider thingsAdapters;
    private final PolicyCommandAdapterProvider policiesAdapters;
    private final ConnectivityCommandAdapterProvider connectivityAdapters;
    private final AcknowledgementAdapterProvider acknowledgementAdapters;
    private final StreamingSubscriptionCommandAdapter streamingSubscriptionCommandAdapter;
    private final StreamingSubscriptionEventAdapter streamingSubscriptionEventAdapter;

    AdapterResolverBySignal(final ThingCommandAdapterProvider thingsAdapters,
            final PolicyCommandAdapterProvider policiesAdapters,
            final ConnectivityCommandAdapterProvider connectivityAdapters,
            final AcknowledgementAdapterProvider acknowledgementAdapters,
            final StreamingSubscriptionCommandAdapter streamingSubscriptionCommandAdapter,
            final StreamingSubscriptionEventAdapter streamingSubscriptionEventAdapter) {

        this.thingsAdapters = thingsAdapters;
        this.policiesAdapters = policiesAdapters;
        this.connectivityAdapters = connectivityAdapters;
        this.acknowledgementAdapters = acknowledgementAdapters;
        this.streamingSubscriptionCommandAdapter = streamingSubscriptionCommandAdapter;
        this.streamingSubscriptionEventAdapter = streamingSubscriptionEventAdapter;
    }

    @SuppressWarnings("unchecked")
    <T extends Signal<?>> Adapter<T> resolve(final T signal, final TopicPath.Channel channel) {

        if (signal instanceof Event) {
            return resolveEvent((Event<?>) signal, channel);
        }
        if (signal instanceof CommandResponse) {
            return resolveCommandResponse((CommandResponse<?>) signal, channel);
        }
        if (signal instanceof Command) {
            return resolveCommand((Command<?>) signal, channel);
        }

        if (signal instanceof PolicyAnnouncement) {
            validateChannel(channel, signal, NONE);
            return (Adapter<T>) policiesAdapters.getAnnouncementAdapter();
        }
        if (signal instanceof ConnectivityAnnouncement) {
            validateChannel(channel, signal, NONE);
            return (Adapter<T>) connectivityAdapters.getAnnouncementAdapter();
        }

        throw UnknownSignalException.newBuilder(signal.getName())
                .dittoHeaders(signal.getDittoHeaders())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>> Adapter<T> resolveEvent(final Event<?> event, final TopicPath.Channel channel) {
        if (event instanceof ThingMerged) {
            validateChannel(channel, event, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getMergedEventAdapter();
        }
        if (event instanceof ThingEvent) {
            validateChannel(channel, event, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getEventAdapter();
        }
        if (event instanceof PolicyEvent) {
            validateChannel(channel, event, NONE);
            return (Adapter<T>) policiesAdapters.getEventAdapter();
        }

        if (event instanceof SubscriptionEvent) {
            validateNotLive(event);
            return (Adapter<T>) thingsAdapters.getSubscriptionEventAdapter();
        }
        if (event instanceof StreamingSubscriptionEvent) {
            validateNotLive(event);
            return (Adapter<T>) streamingSubscriptionEventAdapter;
        }

        throw UnknownSignalException.newBuilder(event.getName())
                .dittoHeaders(event.getDittoHeaders())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>> Adapter<T> resolveCommandResponse(
            final CommandResponse<?> commandResponse, final TopicPath.Channel channel) {

        if (commandResponse instanceof RetrieveThingsResponse) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getRetrieveThingsCommandResponseAdapter();
        }
        if (commandResponse instanceof MergeThingResponse) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getMergeCommandResponseAdapter();
        }
        if (commandResponse instanceof ThingModifyCommandResponse) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getModifyCommandResponseAdapter();
        }
        if (commandResponse instanceof ThingQueryCommandResponse) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getQueryCommandResponseAdapter();
        }
        if (commandResponse instanceof ThingErrorResponse) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getErrorResponseAdapter();
        }

        if (commandResponse instanceof MessageCommandResponse) {
            validateChannel(channel, commandResponse, LIVE);
            return (Adapter<T>) thingsAdapters.getMessageCommandResponseAdapter();
        }

        if (commandResponse instanceof PolicyModifyCommandResponse) {
            validateChannel(channel, commandResponse, NONE);
            return (Adapter<T>) policiesAdapters.getModifyCommandResponseAdapter();
        }
        if (commandResponse instanceof PolicyQueryCommandResponse) {
            validateChannel(channel, commandResponse, NONE);
            return (Adapter<T>) policiesAdapters.getQueryCommandResponseAdapter();
        }
        if (commandResponse instanceof PolicyErrorResponse) {
            validateChannel(channel, commandResponse, NONE);
            return (Adapter<T>) policiesAdapters.getErrorResponseAdapter();
        }

        if (commandResponse instanceof SearchErrorResponse) {
            validateNotLive(commandResponse);
            return (Adapter<T>) thingsAdapters.getSearchErrorResponseAdapter();
        }

        if (commandResponse instanceof Acknowledgement) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) acknowledgementAdapters.getAcknowledgementAdapter();
        }
        if (commandResponse instanceof Acknowledgements) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return (Adapter<T>) acknowledgementAdapters.getAcknowledgementsAdapter();
        }

        throw UnknownSignalException.newBuilder(commandResponse.getName())
                .dittoHeaders(commandResponse.getDittoHeaders())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>> Adapter<T> resolveCommand(final Command<?> command,
            final TopicPath.Channel channel) {

        if (command instanceof MessageCommand) {
            validateChannel(channel, command, LIVE);
            return (Adapter<T>) thingsAdapters.getMessageCommandAdapter();
        }

        if (command instanceof MergeThing) {
            validateChannel(channel, command, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getMergeCommandAdapter();
        }
        if (command instanceof ThingModifyCommand) {
            validateChannel(channel, command, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getModifyCommandAdapter();
        }
        if (command instanceof RetrieveThings) {
            validateChannel(channel, command, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getRetrieveThingsCommandAdapter();
        }
        if (command instanceof ThingQueryCommand) {
            validateChannel(channel, command, LIVE, TWIN);
            return (Adapter<T>) thingsAdapters.getQueryCommandAdapter();
        }

        if (command instanceof ThingSearchCommand) {
            validateNotLive(command);
            return (Adapter<T>) thingsAdapters.getSearchCommandAdapter();
        }
        if (command instanceof StreamingSubscriptionCommand) {
            validateNotLive(command);
            return (Adapter<T>) streamingSubscriptionCommandAdapter;
        }

        if (command instanceof PolicyModifyCommand) {
            validateChannel(channel, command, NONE);
            return (Adapter<T>) policiesAdapters.getModifyCommandAdapter();
        }
        if (command instanceof PolicyQueryCommand) {
            validateChannel(channel, command, NONE);
            return (Adapter<T>) policiesAdapters.getQueryCommandAdapter();
        }

        throw UnknownSignalException.newBuilder(command.getName())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private void validateChannel(final TopicPath.Channel channel,
            final Signal<?> signal, final TopicPath.Channel... supportedChannels) {
        if (!Arrays.asList(supportedChannels).contains(channel)) {
            throw unknownChannelException(signal, channel);
        }
    }

    private void validateNotLive(final Signal<?> signal) {
        if (ProtocolAdapter.isLiveSignal(signal)) {
            throw unknownChannelException(signal, LIVE);
        }
    }

    private UnknownChannelException unknownChannelException(final Signal<?> signal,
            final TopicPath.Channel channel) {
        return UnknownChannelException.newBuilder(channel, signal.getType())
                .dittoHeaders(signal.getDittoHeaders())
                .build();
    }

}
