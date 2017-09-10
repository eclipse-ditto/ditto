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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.messages.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.model.messages.MessageFormatInvalidException;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.model.messages.SubjectInvalidException;
import org.eclipse.ditto.model.messages.TimeoutInvalidException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * An {@link org.eclipse.ditto.signals.base.ErrorRegistry} aware of all {@link org.eclipse.ditto.model.messages.MessageException}s.
 */
@Immutable
public final class MessageErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private MessageErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code MessageErrorRegistry}.
     *
     * @return the command registry.
     */
    public static MessageErrorRegistry newInstance() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies = new HashMap<>();

        parseStrategies.put(AuthorizationSubjectBlockedException.ERROR_CODE,
                AuthorizationSubjectBlockedException::fromJson);
        parseStrategies.put(MessageFormatInvalidException.ERROR_CODE, MessageFormatInvalidException::fromJson);
        parseStrategies.put(MessageSendNotAllowedException.ERROR_CODE, MessageSendNotAllowedException::fromJson);
        parseStrategies.put(MessageTimeoutException.ERROR_CODE, MessageTimeoutException::fromJson);
        parseStrategies.put(SubjectInvalidException.ERROR_CODE, SubjectInvalidException::fromJson);
        parseStrategies.put(TimeoutInvalidException.ERROR_CODE, TimeoutInvalidException::fromJson);

        return new MessageErrorRegistry(parseStrategies);
    }

}
