/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.condition;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

/**
 * Responsible to check conditional requests based on the thing's current state and the specified condition header.
 *
 * @param <C> the type of the handled commands
 * @param <S> the type of the addressed entity
 * @param <K> the type of the context
 * @param <E> the type of the emitted events
 */
@Immutable
public abstract class AbstractConditionCheckingCommandStrategy<
        C extends Command<?>,
        S extends Entity<?>,
        K,
        E extends Event<?>> extends AbstractConditionHeaderCheckingCommandStrategy<C, S, K, E> {

    /**
     * Construct a command-strategy with condition header checking.
     *
     * @param theMatchingClass final class of the command to handle.
     */
    protected AbstractConditionCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    /**
     * Checks condition header on the (sub-)entity determined by the given {@code command} and {@code thing}.
     *
     * @param context the context.
     * @param entity the entity, may be {@code null}.
     * @param nextRevision the next revision number of the entity.
     * @param command the command which addresses either the whole entity or a sub-entity
     * @return Either and error result if the specified condition does not meet the condition or the result of the
     * extending strategy.
     */
    @Override
    public Result<E> apply(final Context<K> context, @Nullable final S entity, final long nextRevision,
            final C command) {

        final String condition = command.getDittoHeaders().getCondition().orElse(null);

        if (condition != null && entity != null) {
            context.getLog().withCorrelationId(command)
                    .debug("Validating condition <{}> on command <{}>.", condition, command);

            try {
                checkCondition((Thing) entity, command, condition);
                context.getLog().withCorrelationId(command)
                        .debug("Validating condition succeeded.");
            } catch (final DittoRuntimeException dre) {
                context.getLog().withCorrelationId(command)
                        .debug("Validating condition failed with exception <{}>.", dre.getMessage());
                return ResultFactory.newErrorResult(dre, command);
            }
        }

        return super.apply(context, entity, nextRevision, command);
    }

    @Override
    public boolean isDefined(final Context<K> context, @Nullable final S entity, final C command) {
        checkNotNull(context, "Context");
        checkNotNull(command, "Command");

        return !(command instanceof CreateThing) && command instanceof ThingCommand && entity instanceof Thing;
    }

    private void checkCondition(final Thing entity, final C command, final String condition) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Criteria criteria = QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance())
                .filterCriteria(condition, dittoHeaders);
        final Predicate<Thing> predicate = ThingPredicateVisitor.apply(criteria);
        if (!predicate.test(entity)) {
            throw ThingConditionFailedException.newBuilder(condition)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
