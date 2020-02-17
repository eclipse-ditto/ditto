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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.protocoladapter.policies.DefaultPolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.things.DefaultThingCommandAdapterProvider;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Adapter for the Ditto protocol.
 */
public final class DittoProtocolAdapter implements ProtocolAdapter {

    private final HeaderTranslator headerTranslator;
    private final AdapterResolver adapterResolver;
    private final DefaultThingCommandAdapterProvider thingsAdapters;
    private final DefaultPolicyCommandAdapterProvider policiesAdapters;

    private DittoProtocolAdapter(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.thingsAdapters = new DefaultThingCommandAdapterProvider(errorRegistry, headerTranslator);
        this.policiesAdapters = new DefaultPolicyCommandAdapterProvider(errorRegistry, headerTranslator);
        this.adapterResolver = new DefaultAdapterResolver(thingsAdapters, policiesAdapters);
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

    @Override
    public Signal<?> fromAdaptable(final Adaptable adaptable) {
        return adapterResolver.getAdapter(adaptable).fromAdaptable(adaptable);
    }

    @Override
    public Adaptable toAdaptable(Command<?> command) {
        final TopicPath.Channel channel = determineChannel(command);
        return toAdaptable(command, channel);
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal) {
        final TopicPath.Channel channel = determineChannel(signal);
        if (signal instanceof MessageCommand) {
            checkChannel(channel, signal, LIVE);
            return toAdaptable((MessageCommand<?, ?>) signal);
        } else if (signal instanceof MessageCommandResponse) {
            checkChannel(channel, signal, LIVE);
            return toAdaptable((MessageCommandResponse<?, ?>) signal);
        } else if (signal instanceof Command) {
            return toAdaptable((Command<?>) signal, channel);
        } else if (signal instanceof CommandResponse) {
            return toAdaptable((CommandResponse<?>) signal, channel);
        } else if (signal instanceof Event) {
            return toAdaptable((Event<?>) signal, channel);
        }
        throw UnknownSignalException.newBuilder(signal.getName()).dittoHeaders(signal.getDittoHeaders()).build();
    }

    @Override
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse, final TopicPath.Channel channel) {
        if (commandResponse instanceof MessageCommandResponse) {
            checkChannel(channel, commandResponse, LIVE);
            return toAdaptable((MessageCommandResponse<?, ?>) commandResponse);
        } else if (commandResponse instanceof ThingCommandResponse) {
            checkChannel(channel, commandResponse, LIVE, TWIN);
            return toAdaptable((ThingCommandResponse<?>) commandResponse, channel);
        } else if (commandResponse instanceof PolicyCommandResponse) {
            checkChannel(channel, commandResponse, NONE);
            return toAdaptable((PolicyCommandResponse<?>) commandResponse);
        } else {
            throw UnknownCommandResponseException.newBuilder(commandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse, final TopicPath.Channel channel) {
        checkChannel(channel, thingCommandResponse, LIVE, TWIN);
        if (thingCommandResponse instanceof ThingQueryCommandResponse) {
            return toAdaptable((ThingQueryCommandResponse<?>) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingModifyCommandResponse) {
            return toAdaptable((ThingModifyCommandResponse<?>) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingErrorResponse) {
            return toAdaptable((ThingErrorResponse) thingCommandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(thingCommandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final PolicyCommandResponse<?> policyCommandResponse) {
        if (policyCommandResponse instanceof PolicyQueryCommandResponse) {
            return toAdaptable((PolicyQueryCommandResponse<?>) policyCommandResponse);
        } else if (policyCommandResponse instanceof PolicyModifyCommandResponse) {
            return toAdaptable((PolicyModifyCommandResponse<?>) policyCommandResponse);
        } else if (policyCommandResponse instanceof PolicyErrorResponse) {
            return toAdaptable((PolicyErrorResponse) policyCommandResponse);
        } else {
            throw UnknownCommandResponseException.newBuilder(policyCommandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final Command<?> command, final TopicPath.Channel channel) {
        if (command instanceof MessageCommand) {
            checkChannel(channel, command, LIVE);
            return toAdaptable((MessageCommand<?, ?>) command);
        } else if (command instanceof ThingModifyCommand) {
            checkChannel(channel, command, LIVE, TWIN);
            return toAdaptable((ThingModifyCommand<?>) command, channel);
        } else if (command instanceof ThingQueryCommand) {
            checkChannel(channel, command, LIVE, TWIN);
            return toAdaptable((ThingQueryCommand<?>) command, channel);
        } else if (command instanceof PolicyModifyCommand) {
            checkChannel(channel, command, NONE);
            return toAdaptable((PolicyModifyCommand<?>) command);
        } else if (command instanceof PolicyQueryCommand) {
            checkChannel(channel, command, NONE);
            return toAdaptable((PolicyQueryCommand<?>) command);
        } else {
            throw UnknownCommandException.newBuilder(command.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand, final TopicPath.Channel channel) {
        checkChannel(channel, thingQueryCommand, TWIN, LIVE);
        return thingsAdapters.getQueryCommandAdapter().toAdaptable(thingQueryCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final TopicPath.Channel channel) {
        checkChannel(channel, thingQueryCommandResponse, TWIN, LIVE);
        return thingsAdapters.getQueryCommandResponseAdapter().toAdaptable(thingQueryCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand, final TopicPath.Channel channel) {
        checkChannel(channel, thingModifyCommand, TWIN, LIVE);
        return thingsAdapters.getModifyCommandAdapter().toAdaptable(thingModifyCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse,
            final TopicPath.Channel channel) {
        checkChannel(channel, thingModifyCommandResponse, TWIN, LIVE);
        return thingsAdapters.getModifyCommandResponseAdapter().toAdaptable(thingModifyCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingErrorResponse thingErrorResponse, final TopicPath.Channel channel) {
        checkChannel(channel, thingErrorResponse, TWIN, LIVE);
        return thingsAdapters.getErrorResponseAdapter().toAdaptable(thingErrorResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final Event<?> event, final TopicPath.Channel channel) {
        if (event instanceof ThingEvent) {
            checkChannel(channel, event, TWIN, LIVE);
            return toAdaptable((ThingEvent<?>) event, channel);
        } else {
            throw UnknownEventException.newBuilder(event.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent) {
        final TopicPath.Channel channel = determineChannel(thingEvent);
        return toAdaptable(thingEvent, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent, final TopicPath.Channel channel) {
        checkChannel(channel, thingEvent, TWIN, LIVE);
        return thingsAdapters.getEventAdapter().toAdaptable(thingEvent, channel);
    }

    @Override
    public Adaptable toAdaptable(final PolicyQueryCommand<?> policyQueryCommand) {
        return policiesAdapters.getQueryCommandAdapter().toAdaptable(policyQueryCommand, NONE);
    }

    @Override
    public Adaptable toAdaptable(final PolicyQueryCommandResponse<?> policyQueryCommandResponse) {
        return policiesAdapters.getQueryCommandResponseAdapter().toAdaptable(policyQueryCommandResponse, NONE);
    }

    @Override
    public Adaptable toAdaptable(final PolicyModifyCommand<?> policyModifyCommand) {
        return policiesAdapters.getModifyCommandAdapter().toAdaptable(policyModifyCommand, NONE);
    }

    @Override
    public Adaptable toAdaptable(final PolicyModifyCommandResponse<?> policyModifyCommandResponse) {
        return policiesAdapters.getModifyCommandResponseAdapter().toAdaptable(policyModifyCommandResponse, NONE);
    }

    @Override
    public Adaptable toAdaptable(final PolicyErrorResponse policyErrorResponse) {
        return policiesAdapters.getErrorResponseAdapter().toAdaptable(policyErrorResponse, NONE);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand<?, ?> messageCommand) {
        return thingsAdapters.getMessageCommandAdapter().toAdaptable(messageCommand, LIVE);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse<?, ?> messageCommandResponse) {
        return thingsAdapters.getMessageCommandResponseAdapter()
                .toAdaptable(messageCommandResponse, LIVE);
    }

    @Override
    public HeaderTranslator headerTranslator() {
        return headerTranslator;
    }

    private TopicPath.Channel determineChannel(final Signal<?> signal) {
        // internally a twin command/event and live command/event are distinguished only  by the channel header i.e.
        // a twin and live command "look the same" except for the channel header
        final boolean isLiveSignal =
                signal.getDittoHeaders().getChannel().filter(LIVE.getName()::equals).isPresent();

        final boolean isMessageCommand = signal instanceof MessageCommand || signal instanceof MessageCommandResponse;
        final boolean isPolicyCommand = signal instanceof PolicyCommand || signal instanceof PolicyCommandResponse;

        return isPolicyCommand ? NONE // policy commands have no channel
                : isMessageCommand ? LIVE // messages does not have live channel in header
                : isLiveSignal ? LIVE  // live signals (live commands/events) use the live channel
                : TopicPath.Channel.TWIN; // all other commands use the twin channel
    }

    private void checkChannel(final TopicPath.Channel channel,
            final Signal<?> signal, final TopicPath.Channel... supportedChannels) {
        if (!Arrays.asList(supportedChannels).contains(channel)) {
            throw unknownChannelException(signal, channel);
        }
    }

    private UnknownChannelException unknownChannelException(final Signal<?> signal, final TopicPath.Channel channel) {
        return UnknownChannelException.newBuilder(channel, signal.getType())
                .dittoHeaders(signal.getDittoHeaders())
                .build();
    }
}
