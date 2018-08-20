package org.eclipse.ditto.services.policies.persistence.actors;/*
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

import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

public abstract class AbstractETagAppendingReceiveStrategy<T extends Command<T>>
        extends AbstractReceiveStrategy<T> {

    /**
     * Constructs a new {@code AbstractReceiveStrategy} object.
     *
     * @param theMatchingClass the class of the message this strategy reacts to.
     * @param theLogger the logger to use for logging.
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractETagAppendingReceiveStrategy(final Class<T> theMatchingClass,
            final DiagnosticLoggingAdapter theLogger) {
        super(theMatchingClass, theLogger);
    }

    @Override
    protected void preApply(final T message) {
        final T messageWithETagHeader = appendETagHeader(message);
        super.preApply(messageWithETagHeader);
    }

    private T appendETagHeader(final T message) {
        final DittoHeaders dittoHeaders = message.getDittoHeaders();
        if (dittoHeaders.get(DittoHeaderDefinition.ETAG.getKey()) == null) {

            final Optional<CharSequence> etagValueOpt = determineETagValue(message);
            if (etagValueOpt.isPresent()) {
                final DittoHeaders newDittoHeaders = dittoHeaders.toBuilder().eTag(etagValueOpt.get()).build();
                return message.setDittoHeaders(newDittoHeaders);
            }
        }

        return message;
    }

    /**
     * Determines the eTag value of the object carried in this command.
     *
     * @param command The received command.
     * @return An optional of the eTag header value. Optional can be empty if no eTag header should be added.
     */
    protected abstract Optional<CharSequence> determineETagValue(final T command);
}