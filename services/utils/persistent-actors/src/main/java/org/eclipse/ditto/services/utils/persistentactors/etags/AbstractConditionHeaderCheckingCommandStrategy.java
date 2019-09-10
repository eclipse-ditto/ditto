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
package org.eclipse.ditto.services.utils.persistentactors.etags;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Responsible to check conditional (http) headers based on the thing's current eTag value.
 *
 * @param <C> The type of the handled command.
 * @param <E> The type of the addressed entity.
 */
@Immutable
public abstract class AbstractConditionHeaderCheckingCommandStrategy<C extends Command, S extends Entity, I, E>
        extends AbstractCommandStrategy<C, S, I, Result<E>>
        implements ETagEntityProvider<C, S> {

    protected AbstractConditionHeaderCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    protected abstract ConditionalHeadersValidator getValidator();

    /**
     * Checks conditional headers on the (sub-)entity determined by the given {@code command} and {@code thing}.
     * Currently supports only {@link org.eclipse.ditto.services.utils.headers.conditional.IfMatchPreconditionHeader} and {@link org.eclipse.ditto.services.utils.headers.conditional.IfNoneMatchPreconditionHeader}
     *
     * @param context the context.
     * @param entity the entity, may be {@code null}.
     * @param nextRevision the next revision number of the entity.
     * @param command the command which addresses either the whole entity or a sub-entity
     * @return Either and error result if a precondition header does not meet the condition or the result of the
     * extending strategy.
     */
    @Override
    public Result<E> apply(final Context<I> context, @Nullable final S entity, final long nextRevision,
            final C command) {

        final EntityTag currentETagValue = determineETagEntity(command, entity)
                .flatMap(EntityTag::fromEntity)
                .orElse(null);

        context.getLog().debug("Validating conditional headers with currentETagValue <{}> on command <{}>.",
                currentETagValue, command);
        try {
            getValidator().checkConditionalHeaders(command, currentETagValue);
            context.getLog().debug("Validating conditional headers succeeded.");
        } catch (final DittoRuntimeException dre) {
            context.getLog().debug("Validating conditional headers failed with exception <{}>.", dre.getMessage());
            return ResultFactory.newErrorResult(dre);
        }

        return super.apply(context, entity, nextRevision, command);
    }

    @Override
    public boolean isDefined(final Context<I> context, @Nullable final S entity, final C command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        return Optional.ofNullable(entity)
                .flatMap(Entity::getEntityId)
                .filter(thingId -> Objects.equals(thingId, command.getEntityId()))
                .isPresent();
    }
}
