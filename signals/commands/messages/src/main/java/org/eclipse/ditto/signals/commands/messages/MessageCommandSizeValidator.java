/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.messages;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.MessagePayloadSizeTooLargeException;
import org.eclipse.ditto.signals.commands.base.AbstractCommandSizeValidator;

/**
 * Command size validator for message commands.
 */
public final class MessageCommandSizeValidator
        extends AbstractCommandSizeValidator<MessagePayloadSizeTooLargeException> {

    /**
     * System property name of the property defining the max Message payload size in bytes.
     */
    public static final String DITTO_LIMITS_MESSAGES_MAX_SIZE_BYTES = "ditto.limits.messages.max-size.bytes";

    @Nullable private static MessageCommandSizeValidator instance;

    private MessageCommandSizeValidator(@Nullable final Long maxSize) {
        super(maxSize);
    }

    @Override
    protected MessagePayloadSizeTooLargeException newInvalidSizeException(final long maxSize, final long actualSize,
            final DittoHeaders headers) {
        return MessagePayloadSizeTooLargeException.newBuilder(actualSize, maxSize).dittoHeaders(headers).build();
    }

    /**
     * The singleton instance for this command size validator. Will be initialized at first call.
     *
     * @return Singleton instance
     */
    public static MessageCommandSizeValidator getInstance() {
        if (null == instance) {
            final long maxSize =
                    Long.parseLong(System.getProperty(DITTO_LIMITS_MESSAGES_MAX_SIZE_BYTES, DEFAULT_LIMIT));
            instance = (maxSize > 0) ? new MessageCommandSizeValidator(maxSize) : new MessageCommandSizeValidator(null);
        }
        return instance;
    }

}
