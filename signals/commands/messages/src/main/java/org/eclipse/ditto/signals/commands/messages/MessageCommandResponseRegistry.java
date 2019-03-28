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
package org.eclipse.ditto.signals.commands.messages;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandResponseRegistry} aware of all {@link
 * MessageCommandResponse}s.
 */
@Immutable
public final class MessageCommandResponseRegistry extends AbstractCommandResponseRegistry<MessageCommandResponse> {

    private MessageCommandResponseRegistry(final Map<String, JsonParsable<MessageCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code MessageCommandResponseRegistry}.
     *
     * @return the command registry.
     */
    public static MessageCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<MessageCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(SendClaimMessageResponse.TYPE, SendClaimMessageResponse::fromJson);
        parseStrategies.put(SendMessageAcceptedResponse.TYPE, SendMessageAcceptedResponse::fromJson);
        parseStrategies.put(SendThingMessageResponse.TYPE, SendThingMessageResponse::fromJson);
        parseStrategies.put(SendFeatureMessageResponse.TYPE, SendFeatureMessageResponse::fromJson);

        return new MessageCommandResponseRegistry(parseStrategies);
    }

}
