/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.etags;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * Responsible to check conditional (http) headers based on the thing's current eTag value.
 *
 * @param <C> the type of the handled commands
 * @param <S> the type of the addressed entity
 * @param <K> the type of the context
 * @param <E> the type of the emitted events
 */
@Immutable
public abstract class AbstractConditionHeaderCheckingCommandStrategy<
        C extends Command<?>,
        S extends Entity<?>,
        K,
        E extends Event<?>> extends AbstractCommandStrategy<C, S, K, E> implements ETagEntityProvider<C, S> {

    /**
     * Construct a command-strategy with condition header checking.
     *
     * @param theMatchingClass final class of the command to handle.
     */
    protected AbstractConditionHeaderCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    /**
     * @return the conditional header validator.
     */
    protected abstract ConditionalHeadersValidator getValidator();

    /**
     * Checks conditional headers on the (sub-)entity determined by the given {@code command} and {@code thing}.
     * Currently, supports only {@link org.eclipse.ditto.internal.utils.headers.conditional.IfMatchPreconditionHeader}
     * and {@link org.eclipse.ditto.internal.utils.headers.conditional.IfNoneMatchPreconditionHeader}
     *
     * @param context the context.
     * @param entity the entity, may be {@code null}.
     * @param nextRevision the next revision number of the entity.
     * @param command the command which addresses either the whole entity or a sub-entity
     * @return Either and error result if a precondition header does not meet the condition or the result of the
     * extending strategy.
     */
    @Override
    public Result<E> apply(final Context<K> context, @Nullable final S entity, final long nextRevision,
            final C command) {

        final EntityTag currentETagValue = previousEntityTag(command, entity).orElse(null);

        context.getLog().withCorrelationId(command)
                .debug("Validating conditional headers with currentETagValue <{}> on command <{}>.",
                        currentETagValue, command);

        final C adjustedCommand;
        try {
            getValidator().checkConditionalHeaders(command, currentETagValue);
            adjustedCommand = getValidator().applyIfEqualHeader(command, entity);
            context.getLog().withCorrelationId(adjustedCommand)
                    .debug("Validating conditional headers succeeded.");
        } catch (final DittoRuntimeException dre) {
            context.getLog().withCorrelationId(command)
                    .debug("Validating conditional headers failed with exception <{}>.", dre.getMessage());
            return ResultFactory.newErrorResult(dre, command);
        }

        return super.apply(context, entity, nextRevision, adjustedCommand);
    }

    @Override
    public boolean isDefined(final Context<K> context, @Nullable final S entity, final C command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        return Optional.ofNullable(entity)
                .flatMap(Entity::getEntityId)
                .filter(entityId -> commandHasEntityIdAndIsEqual(entityId, command))
                .isPresent();
    }

    private static <C extends Command<?>> boolean commandHasEntityIdAndIsEqual(EntityId entityId, C command) {
        if (command instanceof WithEntityId withEntityId) {
            return Objects.equals(entityId, withEntityId.getEntityId());
        } else {
            return false;
        }
    }

}
