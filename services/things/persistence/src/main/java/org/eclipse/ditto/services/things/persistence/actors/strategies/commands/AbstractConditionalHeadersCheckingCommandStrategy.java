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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.services.utils.headers.conditional.IfMatchPreconditionHeader;
import org.eclipse.ditto.services.utils.headers.conditional.IfNoneMatchPreconditionHeader;
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

    private static final ConditionalHeadersValidator VALIDATOR = ThingsConditionalHeadersValidatorProvider.getInstance();

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractConditionalHeadersCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    /**
     * Checks conditional headers on the (sub-)entity determined by the given {@code command} and {@code thing}.
     * Currently supports only {@link IfMatchPreconditionHeader} and {@link IfNoneMatchPreconditionHeader}
     *
     * @param context the context.
     * @param thing the thing, may be {@code null}.
     * @param nextRevision the next revision number of the ThingPersistenceActor.
     * @param command the command which addresses either the whole thing or a sub-entity
     * @return Either and error result if a precondition header does not meet the condition or the result of the
     * extending strategy.
     */
    @Override
    public Result apply(final Context context, @Nullable final Thing thing, final long nextRevision, final C command) {

        final EntityTag currentETagValue = determineETagEntity(command, thing)
                .flatMap(EntityTag::fromEntity)
                .orElse(null);

        context.getLog().debug("Validating conditional headers with currentETagValue <{}> on command <{}>.");
        try {
            VALIDATOR.checkConditionalHeaders(command, currentETagValue);
            context.getLog().debug("Validating conditional headers succeeded.");
        } catch (final DittoRuntimeException dre) {
            context.getLog().debug("Validating conditional headers failed with exception <{}>.", dre.getMessage());
            return ResultFactory.newErrorResult(dre);
        }

        return super.apply(context, thing, nextRevision, command);
    }


}
