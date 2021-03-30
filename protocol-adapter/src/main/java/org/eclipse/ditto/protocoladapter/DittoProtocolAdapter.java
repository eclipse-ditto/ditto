/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.TWIN;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.protocoladapter.acknowledgements.DefaultAcknowledgementsAdapterProvider;
import org.eclipse.ditto.protocoladapter.policies.DefaultPolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.AcknowledgementAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.things.DefaultThingCommandAdapterProvider;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.announcements.policies.PolicyAnnouncement;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.commands.thingsearch.SearchErrorResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingMerged;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

/**
 * Adapter for the Ditto protocol.
 */
public final class DittoProtocolAdapter implements ProtocolAdapter {

    private final HeaderTranslator headerTranslator;
    private final ThingCommandAdapterProvider thingsAdapters;
    private final PolicyCommandAdapterProvider policiesAdapters;
    private final AcknowledgementAdapterProvider acknowledgementAdapters;
    private final AdapterResolver adapterResolver;

    private DittoProtocolAdapter(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.thingsAdapters = new DefaultThingCommandAdapterProvider(errorRegistry, headerTranslator);
        this.policiesAdapters = new DefaultPolicyCommandAdapterProvider(errorRegistry, headerTranslator);
        this.acknowledgementAdapters = new DefaultAcknowledgementsAdapterProvider(errorRegistry, headerTranslator);
        this.adapterResolver = new DefaultAdapterResolver(thingsAdapters, policiesAdapters, acknowledgementAdapters);
    }

    private DittoProtocolAdapter(final HeaderTranslator headerTranslator,
            final ThingCommandAdapterProvider thingsAdapters, final PolicyCommandAdapterProvider policiesAdapters,
            final AcknowledgementAdapterProvider acknowledgementAdapters, final AdapterResolver adapterResolver) {
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.thingsAdapters = checkNotNull(thingsAdapters, "thingsAdapters");
        this.policiesAdapters = checkNotNull(policiesAdapters, "policiesAdapters");
        this.acknowledgementAdapters = checkNotNull(acknowledgementAdapters, "acknowledgementAdapters");
        this.adapterResolver = checkNotNull(adapterResolver, "adapterResolver");
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance with the given header translator.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return new DittoProtocolAdapter
     */
    public static DittoProtocolAdapter of(final HeaderTranslator headerTranslator) {
        return new DittoProtocolAdapter(GlobalErrorRegistry.getInstance(), headerTranslator);
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance.
     *
     * @return the instance.
     */
    public static DittoProtocolAdapter newInstance() {
        return new DittoProtocolAdapter(GlobalErrorRegistry.getInstance(), getHeaderTranslator());
    }

    /**
     * Creates a default header translator for this protocol adapter.
     *
     * @return the default header translator.
     */
    public static HeaderTranslator getHeaderTranslator() {
        return HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values());
    }

    /**
     * Factory method used in tests.
     *
     * @param headerTranslator translator between external and Ditto headers
     * @param thingCommandAdapterProvider command adapters for thing commands
     * @param policyCommandAdapterProvider command adapters for policy commands
     * @param acknowledgementAdapters adapters for acknowledgements.
     * @param adapterResolver resolves the correct adapter from a command
     * @return new instance of {@link DittoProtocolAdapter}
     */
    static DittoProtocolAdapter newInstance(final HeaderTranslator headerTranslator,
            final ThingCommandAdapterProvider thingCommandAdapterProvider,
            final PolicyCommandAdapterProvider policyCommandAdapterProvider,
            final AcknowledgementAdapterProvider acknowledgementAdapters,
            final AdapterResolver adapterResolver) {
        return new DittoProtocolAdapter(headerTranslator, thingCommandAdapterProvider, policyCommandAdapterProvider,
                acknowledgementAdapters, adapterResolver
        );
    }

    @Override
    public Signal<?> fromAdaptable(final Adaptable adaptable) {
        return adapterResolver.getAdapter(adaptable).fromAdaptable(adaptable);
    }

    @Override
    public Adaptable toAdaptable(final Command<?> command) {
        final TopicPath.Channel channel = ProtocolAdapter.determineChannel(command);
        return toAdaptable(command, channel);
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal) {
        final TopicPath.Channel channel = ProtocolAdapter.determineChannel(signal);
        return toAdaptable(signal, channel);
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal, final TopicPath.Channel channel) {
        if (signal instanceof MessageCommand) {
            validateChannel(channel, signal, LIVE);
            return toAdaptable((MessageCommand<?, ?>) signal);
        } else if (signal instanceof MessageCommandResponse) {
            validateChannel(channel, signal, LIVE);
            return toAdaptable((MessageCommandResponse<?, ?>) signal);
        } else if (signal instanceof ThingSearchCommand) {
            return toAdaptable((ThingSearchCommand<?>) signal, channel);
        } else if (signal instanceof Command) {
            return toAdaptable((Command<?>) signal, channel);
        } else if (signal instanceof CommandResponse) {
            return toAdaptable((CommandResponse<?>) signal, channel);
        } else if (signal instanceof Event) {
            return toAdaptable((Event<?>) signal, channel);
        } else if (signal instanceof PolicyAnnouncement) {
            return adaptPolicyAnnouncement((PolicyAnnouncement<?>) signal);
        }
        throw UnknownSignalException.newBuilder(signal.getName()).dittoHeaders(signal.getDittoHeaders()).build();
    }

    @Override
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse, final TopicPath.Channel channel) {
        if (commandResponse instanceof MessageCommandResponse) {
            validateChannel(channel, commandResponse, LIVE);
            return toAdaptable((MessageCommandResponse<?, ?>) commandResponse);
        } else if (commandResponse instanceof ThingCommandResponse) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return toAdaptable((ThingCommandResponse<?>) commandResponse, channel);
        } else if (commandResponse instanceof PolicyCommandResponse) {
            validateChannel(channel, commandResponse, NONE);
            return toAdaptable((PolicyCommandResponse<?>) commandResponse);
        } else if (commandResponse instanceof Acknowledgement) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return toAdaptable((Acknowledgement) commandResponse, channel);
        } else if (commandResponse instanceof Acknowledgements) {
            validateChannel(channel, commandResponse, LIVE, TWIN);
            return toAdaptable((Acknowledgements) commandResponse, channel);
        } else if (commandResponse instanceof SearchErrorResponse) {
            return toAdaptable((SearchErrorResponse) commandResponse);
        }
        throw UnknownCommandResponseException.newBuilder(commandResponse.getName()).build();
    }

    @Override
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse, final TopicPath.Channel channel) {
        validateChannel(channel, thingCommandResponse, LIVE, TWIN);
        if (thingCommandResponse instanceof ThingQueryCommandResponse) {
            return toAdaptable((ThingQueryCommandResponse<?>) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingModifyCommandResponse) {
            return toAdaptable((ThingModifyCommandResponse<?>) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingErrorResponse) {
            return toAdaptable((ThingErrorResponse) thingCommandResponse, channel);
        }
        throw UnknownCommandResponseException.newBuilder(thingCommandResponse.getName()).build();
    }

    private Adaptable toAdaptable(final PolicyCommandResponse<?> policyCommandResponse) {
        if (policyCommandResponse instanceof PolicyQueryCommandResponse) {
            return toAdaptable((PolicyQueryCommandResponse<?>) policyCommandResponse);
        } else if (policyCommandResponse instanceof PolicyModifyCommandResponse) {
            return toAdaptable((PolicyModifyCommandResponse<?>) policyCommandResponse);
        } else if (policyCommandResponse instanceof PolicyErrorResponse) {
            return toAdaptable((PolicyErrorResponse) policyCommandResponse);
        }
        throw UnknownCommandResponseException.newBuilder(policyCommandResponse.getName()).build();
    }

    @Override
    public Adaptable toAdaptable(final Command<?> command, final TopicPath.Channel channel) {
        if (command instanceof MessageCommand) {
            validateChannel(channel, command, LIVE);
            return toAdaptable((MessageCommand<?, ?>) command);
        } else if (command instanceof ThingModifyCommand) {
            validateChannel(channel, command, LIVE, TWIN);
            return toAdaptable((ThingModifyCommand<?>) command, channel);
        } else if (command instanceof ThingSearchCommand) {
            return toAdaptable((ThingSearchCommand<?>) command, channel);
        } else if (command instanceof ThingQueryCommand) {
            validateChannel(channel, command, LIVE, TWIN);
            return toAdaptable((ThingQueryCommand<?>) command, channel);
        } else if (command instanceof PolicyModifyCommand) {
            validateChannel(channel, command, NONE);
            return toAdaptable((PolicyModifyCommand<?>) command);
        } else if (command instanceof PolicyQueryCommand) {
            validateChannel(channel, command, NONE);
            return toAdaptable((PolicyQueryCommand<?>) command);
        }
        throw UnknownCommandException.newBuilder(command.getName()).build();
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand, final TopicPath.Channel channel) {
        validateChannel(channel, thingQueryCommand, TWIN, LIVE);
        return thingsAdapters.getQueryCommandAdapter().toAdaptable(thingQueryCommand, channel);
    }

    public Adaptable toAdaptable(final ThingSearchCommand<?> thingSearchCommand, final TopicPath.Channel channel) {
        validateChannel(channel, thingSearchCommand, TWIN);
        return thingsAdapters.getSearchCommandAdapter().toAdaptable(thingSearchCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final TopicPath.Channel channel) {
        validateChannel(channel, thingQueryCommandResponse, TWIN, LIVE);
        return thingsAdapters.getQueryCommandResponseAdapter().toAdaptable(thingQueryCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand, final TopicPath.Channel channel) {
        validateChannel(channel, thingModifyCommand, TWIN, LIVE);
        if (thingModifyCommand instanceof MergeThing) {
            return thingsAdapters.getMergeCommandAdapter().toAdaptable((MergeThing) thingModifyCommand, channel);
        } else {
            return thingsAdapters.getModifyCommandAdapter().toAdaptable(thingModifyCommand, channel);
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse,
            final TopicPath.Channel channel) {
        validateChannel(channel, thingModifyCommandResponse, TWIN, LIVE);
        if (thingModifyCommandResponse instanceof MergeThingResponse) {
            return thingsAdapters.getMergeCommandResponseAdapter()
                    .toAdaptable((MergeThingResponse) thingModifyCommandResponse, channel);
        } else {
            return thingsAdapters.getModifyCommandResponseAdapter().toAdaptable(thingModifyCommandResponse, channel);
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingErrorResponse thingErrorResponse, final TopicPath.Channel channel) {
        validateChannel(channel, thingErrorResponse, TWIN, LIVE);
        return thingsAdapters.getErrorResponseAdapter().toAdaptable(thingErrorResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final Event<?> event, final TopicPath.Channel channel) {
        if (event instanceof ThingEvent) {
            validateChannel(channel, event, TWIN, LIVE);
            return toAdaptable((ThingEvent<?>) event, channel);
        } else if (event instanceof SubscriptionEvent) {
            validateChannel(channel, event, TWIN);
            return toAdaptable((SubscriptionEvent<?>) event, channel);
        }
        throw UnknownEventException.newBuilder(event.getName()).build();
    }

    @Override
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent, final TopicPath.Channel channel) {
        validateChannel(channel, thingEvent, TWIN, LIVE);
        if (thingEvent instanceof ThingMerged) {
            return thingsAdapters.getMergedEventAdapter().toAdaptable((ThingMerged) thingEvent, channel);
        } else {
            return thingsAdapters.getEventAdapter().toAdaptable(thingEvent, channel);
        }
    }

    private Adaptable adaptPolicyAnnouncement(final PolicyAnnouncement<?> announcement) {
        return policiesAdapters.getAnnouncementAdapter().toAdaptable(announcement);
    }

    public Adaptable toAdaptable(final SubscriptionEvent<?> subscriptionEvent, final TopicPath.Channel channel) {
        validateNotLive(subscriptionEvent);
        return thingsAdapters.getSubscriptionEventAdapter().toAdaptable(subscriptionEvent, channel);
    }

    private Adaptable toAdaptable(final PolicyQueryCommand<?> policyQueryCommand) {
        validateNotLive(policyQueryCommand);
        return policiesAdapters.getQueryCommandAdapter().toAdaptable(policyQueryCommand, NONE);
    }

    private Adaptable toAdaptable(final PolicyQueryCommandResponse<?> policyQueryCommandResponse) {
        validateNotLive(policyQueryCommandResponse);
        return policiesAdapters.getQueryCommandResponseAdapter().toAdaptable(policyQueryCommandResponse, NONE);
    }

    private Adaptable toAdaptable(final PolicyModifyCommand<?> policyModifyCommand) {
        validateNotLive(policyModifyCommand);
        return policiesAdapters.getModifyCommandAdapter().toAdaptable(policyModifyCommand, NONE);
    }

    private Adaptable toAdaptable(final PolicyModifyCommandResponse<?> policyModifyCommandResponse) {
        validateNotLive(policyModifyCommandResponse);
        return policiesAdapters.getModifyCommandResponseAdapter().toAdaptable(policyModifyCommandResponse, NONE);
    }

    private Adaptable toAdaptable(final PolicyErrorResponse policyErrorResponse) {
        validateNotLive(policyErrorResponse);
        return policiesAdapters.getErrorResponseAdapter().toAdaptable(policyErrorResponse, NONE);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand<?, ?> messageCommand) {
        return thingsAdapters.getMessageCommandAdapter().toAdaptable(messageCommand, LIVE);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse<?, ?> messageCommandResponse) {
        return thingsAdapters.getMessageCommandResponseAdapter().toAdaptable(messageCommandResponse, LIVE);
    }

    @Override
    public HeaderTranslator headerTranslator() {
        return headerTranslator;
    }

    private Adaptable toAdaptable(final Acknowledgement acknowledgement, final TopicPath.Channel channel) {
        return acknowledgementAdapters.getAcknowledgementAdapter().toAdaptable(acknowledgement, channel);
    }

    private Adaptable toAdaptable(final Acknowledgements acknowledgements, final TopicPath.Channel channel) {
        return acknowledgementAdapters.getAcknowledgementsAdapter().toAdaptable(acknowledgements, channel);
    }

    private Adaptable toAdaptable(final SearchErrorResponse errorResponse) {
        final DittoHeaders responseHeaders =
                ProtocolFactory.newHeadersWithJsonContentType(errorResponse.getDittoHeaders());

        final Payload payload = Payload.newBuilder(JsonPointer.empty())
                .withStatus(errorResponse.getHttpStatus())
                .withValue(errorResponse.toJson(errorResponse.getImplementedSchemaVersion())
                        .getValue(CommandResponse.JsonFields.PAYLOAD)
                        .orElse(JsonFactory.nullObject())) // only use the error payload
                .build();

        final TopicPath errorTopicPath = ProtocolFactory.newTopicPathBuilderFromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .none()
                .search()
                .error()
                .build();

        return Adaptable.newBuilder(errorTopicPath)
                .withPayload(payload)
                .withHeaders(DittoHeaders.of(getHeaderTranslator().toExternalHeaders(responseHeaders)))
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

    private UnknownChannelException unknownChannelException(final Signal<?> signal, final TopicPath.Channel channel) {
        return UnknownChannelException.newBuilder(channel, signal.getType())
                .dittoHeaders(signal.getDittoHeaders())
                .build();
    }
}
