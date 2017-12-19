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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;

/**
 * Adapter for mapping a {@link MessageCommandResponseAdapter} to and from an {@link Adaptable}.
 */
final class MessageCommandResponseAdapter extends AbstractAdapter<MessageCommandResponse> {

    private MessageCommandResponseAdapter(
            final Map<String, JsonifiableMapper<MessageCommandResponse>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns a new MessageCommandResponseAdapter.
     *
     * @return the adapter.
     */
    public static MessageCommandResponseAdapter newInstance() {
        return new MessageCommandResponseAdapter(mappingStrategies());
    }

    private static Map<String, JsonifiableMapper<MessageCommandResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<MessageCommandResponse>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(SendClaimMessageResponse.TYPE,
                adaptable -> SendClaimMessageResponse.of(thingIdFrom(adaptable),
                        MessageAdaptableHelper.messageFrom(adaptable),
                        statusCodeFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendThingMessageResponse.TYPE,
                adaptable -> SendThingMessageResponse.of(thingIdFrom(adaptable),
                        MessageAdaptableHelper.messageFrom(adaptable),
                        statusCodeFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendFeatureMessageResponse.TYPE,
                adaptable -> SendFeatureMessageResponse.of(thingIdFrom(adaptable), featureIdForMessageFrom(adaptable),
                        MessageAdaptableHelper.messageFrom(adaptable), statusCodeFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendMessageAcceptedResponse.TYPE,
                adaptable -> SendMessageAcceptedResponse.newInstance(thingIdFrom(adaptable),
                        MessageAdaptableHelper.messageHeadersFrom(adaptable),statusCodeFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse t) {
        return toAdaptable(t, TopicPath.Channel.LIVE);
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        if (adaptable.getTopicPath().getSubject().filter(KnownMessageSubjects.CLAIM_SUBJECT::equals).isPresent()) {
            return SendClaimMessageResponse.TYPE;
        } else if (!adaptable.getHeaders().map(DittoHeaders::isResponseRequired).orElse(true)) {
            return SendMessageAcceptedResponse.TYPE;
        } else if (adaptable.containsHeaderForKey(MessageHeaderDefinition.FEATURE_ID.getKey())) {
            return SendFeatureMessageResponse.TYPE;
        } else {
            return SendThingMessageResponse.TYPE;
        }
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse command, final TopicPath.Channel channel) {

        return MessageAdaptableHelper.adaptableFrom(channel, command.getThingId(), command.toJson(),
                command.getResourcePath(), command.getMessage(), command.getDittoHeaders());
    }

}
