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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Responsible to add the eTag header value to the command response.
 *
 * @param <T> The type of the handled command.
 */
@Immutable
public abstract class AbstractETagAppendingCommandStrategy<T extends Command<T>> extends AbstractCommandStrategy<T> {

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractETagAppendingCommandStrategy(final Class<T> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public Result apply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final T command) {

        final T commandWithAppendedETagHeader = appendETagHeader(thing, nextRevision, command);
        return super.apply(context, thing, nextRevision, commandWithAppendedETagHeader);
    }

    private T appendETagHeader(@Nullable final Thing thing, final long nextRevision, final T command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (dittoHeaders.get(DittoHeaderDefinition.ETAG.getKey()) == null) {

            final Optional<CharSequence> etagValueOpt = determineETagValue(thing, nextRevision, command);
            if (etagValueOpt.isPresent()) {
                final DittoHeaders newDittoHeaders = dittoHeaders.toBuilder().eTag(etagValueOpt.get()).build();
                return command.setDittoHeaders(newDittoHeaders);
            }
        }

        return command;
    }

    /**
     * Determines the eTag value of the object carried in this command.
     *
     * @param thing The thing that is modified.
     * @param nextRevision The next revision this thing would have.
     * @param command The received command.
     * @return An optional of the eTag header value. Optional can be empty if no eTag header should be added.
     */
    protected abstract Optional<CharSequence> determineETagValue(@Nullable final Thing thing, final long nextRevision,
            final T command);
}
