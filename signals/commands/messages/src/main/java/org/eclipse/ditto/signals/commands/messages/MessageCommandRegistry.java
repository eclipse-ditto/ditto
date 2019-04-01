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

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link MessageCommand}s.
 */
public final class MessageCommandRegistry extends AbstractCommandRegistry<MessageCommand> {

    private MessageCommandRegistry(final Map<String, JsonParsable<MessageCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code MessageCommandRegistry}.
     *
     * @return the command registry.
     */
    public static MessageCommandRegistry newInstance() {
        final Map<String, JsonParsable<MessageCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(SendClaimMessage.TYPE, SendClaimMessage::fromJson);
        parseStrategies.put(SendThingMessage.TYPE, SendThingMessage::fromJson);
        parseStrategies.put(SendFeatureMessage.TYPE, SendFeatureMessage::fromJson);

        return new MessageCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return MessageCommand.TYPE_PREFIX;
    }

}
