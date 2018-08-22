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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.headers.conditional.ETagValueGenerator;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Responsible to check conditional (http) headers based on the thing's current eTag value.
 *
 * @param <C> The type of the handled command.
 * @param <E> The type of the addressed entity.
 */
@Immutable
public abstract class AbstractConditionalHeadersCheckingCommandStrategy<C extends Command<C>, E> extends
        AbstractCommandStrategy<C> implements ETagEntityProvider<C, E> {

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractConditionalHeadersCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public Result apply(final Context context, @Nullable final Thing thing, final long nextRevision, final C command) {
        final Result checkConditionalHeadersResult = checkConditionalHeaders(command, thing);
        if (checkConditionalHeadersResult != null) {
            return checkConditionalHeadersResult;
        }

        return super.apply(context, thing, nextRevision, command);
    }

    /**
     * Checks conditional headers on the (sub-)entity determined by the given {@code command} and {@code thing}.
     * @param command the command which addresses either the whole thing or a sub-entity
     * @param thing the thing, may be {@code null}.
     *
     * @return {@code null} in case of success, an (error) {@link Result} otherwise.
     */
    @Nullable
    private Result checkConditionalHeaders(final C command, @Nullable final Thing thing) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CharSequence currentETagValue = determineETagEntity(command, thing)
                .flatMap(ETagValueGenerator::generate)
                .orElse(null);

        //TODO: check conditional headers

        return null;
    }

}
