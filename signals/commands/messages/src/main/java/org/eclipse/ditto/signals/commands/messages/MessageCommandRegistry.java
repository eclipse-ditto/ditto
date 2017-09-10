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
package org.eclipse.ditto.signals.commands.messages;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link MessageCommand}s.
 */
@Immutable
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
