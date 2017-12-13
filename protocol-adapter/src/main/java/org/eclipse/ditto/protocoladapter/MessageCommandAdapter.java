/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;

/**
 * Adapter for mapping a {@link MessageCommandAdapter} to and from an {@link Adaptable}.
 */
final class MessageCommandAdapter extends AbstractAdapter<MessageCommand> {

    private MessageCommandAdapter(final Map<String, JsonifiableMapper<MessageCommand>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns a new MessageCommandAdapter.
     *
     * @return the adapter.
     */
    public static MessageCommandAdapter newInstance() {
        return new MessageCommandAdapter(mappingStrategies());
    }

    private static Map<String, JsonifiableMapper<MessageCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<MessageCommand>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(SendClaimMessage.TYPE,
                adaptable -> SendClaimMessage.of(thingIdFrom(adaptable), MessageAdaptableHelper.messageFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendThingMessage.TYPE,
                adaptable -> SendThingMessage.of(thingIdFrom(adaptable), MessageAdaptableHelper.messageFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendFeatureMessage.TYPE,
                adaptable -> SendFeatureMessage.of(thingIdFrom(adaptable), featureIdForMessageFrom(adaptable),
                        MessageAdaptableHelper.messageFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand t) {
        return toAdaptable(t, TopicPath.Channel.LIVE);
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        if (adaptable.getTopicPath().getSubject().filter(KnownMessageSubjects.CLAIM_SUBJECT::equals).isPresent()) {
            return SendClaimMessage.TYPE;
        } else if (adaptable.containsHeaderForKey(MessageHeaderDefinition.FEATURE_ID.getKey())) {
            return SendFeatureMessage.TYPE;
        } else {
            return SendThingMessage.TYPE;
        }
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand command, final TopicPath.Channel channel) {
        return MessageAdaptableHelper.adaptableFrom(channel, command.getThingId(), command.toJson(),
                command.getResourcePath(), command.getMessage(), command.getDittoHeaders());
    }

}
