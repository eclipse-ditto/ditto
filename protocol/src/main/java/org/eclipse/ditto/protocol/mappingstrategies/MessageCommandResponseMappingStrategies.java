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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendMessageAcceptedResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for message command responses.
 */
final class MessageCommandResponseMappingStrategies
        extends AbstractMessageMappingStrategies<MessageCommandResponse<?, ?>> {

    private static final MessageCommandResponseMappingStrategies INSTANCE =
            new MessageCommandResponseMappingStrategies();

    private MessageCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    static MessageCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<MessageCommandResponse<?, ?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<MessageCommandResponse<?, ?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(SendClaimMessageResponse.TYPE,
                adaptable -> SendClaimMessageResponse.of(thingIdFrom(adaptable), messageFrom(adaptable),
                        getHttpStatus(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendThingMessageResponse.TYPE,
                adaptable -> SendThingMessageResponse.of(thingIdFrom(adaptable), messageFrom(adaptable),
                        getHttpStatus(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendFeatureMessageResponse.TYPE,
                adaptable -> SendFeatureMessageResponse.of(thingIdFrom(adaptable), featureIdForMessageFrom(adaptable),
                        messageFrom(adaptable), getHttpStatus(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendMessageAcceptedResponse.TYPE,
                adaptable -> SendMessageAcceptedResponse.newInstance(thingIdFrom(adaptable),
                        messageHeadersFrom(adaptable), getHttpStatus(adaptable), dittoHeadersFrom(adaptable)));
        return mappingStrategies;
    }

    protected static String featureIdForMessageFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getPath()
                .getFeatureId()
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

}
