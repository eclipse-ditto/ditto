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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
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

    private final AdapterResolver thingsAdapters;
    private final AdapterResolver policiesAdapters;
    private final HeaderTranslator headerTranslator;

    private DittoProtocolAdapter(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.headerTranslator = headerTranslator;
        this.thingsAdapters = new ThingsAdapters(errorRegistry, headerTranslator);
        this.policiesAdapters = new PoliciesAdapters(errorRegistry, headerTranslator);
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance with the given header translator.
     *
     * @param headerTranslator translator between external and Ditto headers.
     */
    public static DittoProtocolAdapter of(final HeaderTranslator headerTranslator) {
        checkNotNull(headerTranslator, "headerTranslator");
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
        final TopicPath topicPath = adaptable.getTopicPath();
        final Adapter<? extends Signal> adapter;
        if (TopicPath.Group.THINGS.equals(topicPath.getGroup())) { // /things
            adapter = thingsAdapters.getAdapter(adaptable);
        } else if (TopicPath.Group.POLICIES.equals(topicPath.getGroup())) {  // /policies
            adapter = policiesAdapters.getAdapter(adaptable);
        } else {
            throw UnknownTopicPathException.newBuilder(topicPath).build();
        }
        return adapter.fromAdaptable(adaptable);
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal) {
        final boolean isLive = isLiveSignal(signal);
        final TopicPath.Channel channel = isLive ? TopicPath.Channel.LIVE : TopicPath.Channel.TWIN;
        if (signal instanceof MessageCommand) {
            return toAdaptable((MessageCommand<?, ?>) signal);
        } else if (signal instanceof MessageCommandResponse) {
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

    private static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    @Override
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse, final TopicPath.Channel channel) {
        if (commandResponse instanceof MessageCommandResponse && channel == TopicPath.Channel.LIVE) {
            return toAdaptable((MessageCommandResponse) commandResponse);
        } else if (commandResponse instanceof ThingCommandResponse) {
            return toAdaptable((ThingCommandResponse) commandResponse, channel);
        } else if (commandResponse instanceof PolicyCommandResponse) {
            return toAdaptable((PolicyCommandResponse) commandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(commandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse, final TopicPath.Channel channel) {
        if (thingCommandResponse instanceof ThingQueryCommandResponse) {
            return toAdaptable((ThingQueryCommandResponse) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingModifyCommandResponse) {
            return toAdaptable((ThingModifyCommandResponse) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingErrorResponse) {
            return toAdaptable((ThingErrorResponse) thingCommandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(thingCommandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final PolicyCommandResponse<?> policyCommandResponse,
            final TopicPath.Channel channel) {
        if (policyCommandResponse instanceof PolicyQueryCommandResponse) {
            return toAdaptable((PolicyQueryCommandResponse) policyCommandResponse, channel);
        } else if (policyCommandResponse instanceof PolicyModifyCommandResponse) {
            return toAdaptable((PolicyModifyCommandResponse) policyCommandResponse, channel);
        } else if (policyCommandResponse instanceof PolicyErrorResponse) {
            return toAdaptable((PolicyErrorResponse) policyCommandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(policyCommandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final Command<?> command, final TopicPath.Channel channel) {
        if (command instanceof MessageCommand && channel == TopicPath.Channel.LIVE) {
            return toAdaptable((MessageCommand) command);
        } else if (command instanceof ThingModifyCommand) {
            return toAdaptable((ThingModifyCommand) command, channel);
        } else if (command instanceof ThingQueryCommand) {
            return toAdaptable((ThingQueryCommand) command, channel);
        } else if (command instanceof PolicyModifyCommand) {
            return toAdaptable((PolicyModifyCommand) command, channel);
        } else if (command instanceof PolicyQueryCommand) {
            return toAdaptable((PolicyQueryCommand) command, channel);
        } else {
            throw UnknownCommandException.newBuilder(command.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand, final TopicPath.Channel channel) {
        // TODO DG avoid ugly cast
        final Adapter<ThingModifyCommand> modifyCommandAdapter =
                (Adapter<ThingModifyCommand>) thingsAdapters.getModifyCommandAdapter();
        return modifyCommandAdapter.toAdaptable(thingModifyCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final PolicyModifyCommand<?> policyModifyCommand, final TopicPath.Channel channel) {
        return (((Adapter<PolicyModifyCommand>) policiesAdapters.getModifyCommandAdapter())).toAdaptable(
                policyModifyCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse,
            final TopicPath.Channel channel) {
        return (((Adapter<ThingModifyCommandResponse>) thingsAdapters.getModifyCommandResponseAdapter())).toAdaptable(
                thingModifyCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final PolicyModifyCommandResponse<?> policyModifyCommandResponse,
            final TopicPath.Channel channel) {
        return (((Adapter<PolicyModifyCommandResponse>) policiesAdapters.getModifyCommandResponseAdapter())).toAdaptable(
                policyModifyCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand, final TopicPath.Channel channel) {
        return (((Adapter<ThingQueryCommand>) thingsAdapters.getQueryCommandAdapter())).toAdaptable(thingQueryCommand
                , channel);
    }

    @Override
    public Adaptable toAdaptable(final PolicyQueryCommand<?> policyQueryCommand, final TopicPath.Channel channel) {
        return (((Adapter<PolicyQueryCommand>) policiesAdapters.getQueryCommandAdapter())).toAdaptable(
                policyQueryCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final TopicPath.Channel channel) {
        return (((Adapter<ThingQueryCommandResponse>) thingsAdapters.getQueryCommandResponseAdapter())).toAdaptable(
                thingQueryCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final PolicyQueryCommandResponse<?> policyQueryCommandResponse,
            final TopicPath.Channel channel) {
        return (((Adapter<PolicyQueryCommandResponse>) policiesAdapters.getQueryCommandResponseAdapter())).toAdaptable(
                policyQueryCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingErrorResponse thingErrorResponse, final TopicPath.Channel channel) {
        return (((Adapter<ThingErrorResponse>) thingsAdapters.getErrorResponseAdapter())).toAdaptable(
                thingErrorResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final PolicyErrorResponse policyErrorResponse,
            final TopicPath.Channel channel) {
        return (((Adapter<PolicyErrorResponse>) policiesAdapters.getErrorResponseAdapter())).toAdaptable(
                policyErrorResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final Event<?> event, final TopicPath.Channel channel) {
        if (event instanceof ThingEvent) {
            return toAdaptable((ThingEvent) event, channel);
        } else {
            throw UnknownEventException.newBuilder(event.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent, final TopicPath.Channel channel) {
        return (((Adapter<ThingEvent<?>>) thingsAdapters.getEventAdapter())).toAdaptable(thingEvent, channel);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand<?, ?> messageCommand) {
        return (((Adapter<MessageCommand>) thingsAdapters.getMessageCommandAdapter())).toAdaptable(messageCommand);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse<?, ?> messageCommandResponse) {
        return (((Adapter<MessageCommandResponse>) thingsAdapters.getMessageCommandResponseAdapter())).toAdaptable(
                messageCommandResponse);
    }

    @Override
    public HeaderTranslator headerTranslator() {
        return headerTranslator;
    }

}
