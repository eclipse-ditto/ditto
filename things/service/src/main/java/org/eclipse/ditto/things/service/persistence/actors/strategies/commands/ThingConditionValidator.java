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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

@Immutable
final class ThingConditionValidator {

    private static final ThingConditionValidator INSTANCE = new ThingConditionValidator();

    private ThingConditionValidator() {
    }

    /**
     * Returns the ThingConditionValidator instance.
     *
     * @return the ThingConditionValidator instance.
     */
    public static ThingConditionValidator getInstance() {
        return INSTANCE;
    }

    /**
     * Validates if the given condition matches the actual thing state.
     *
     * @param command the command used for checking if validation should be applied.
     * @param condition the condition which should be verified against the thing.
     * @param entity the actual thing entity.
     * @return either void or the ThingConditionFailedException in case the condition couldN't be validated.
     */
    public Either<Void, ThingConditionFailedException> validate(final Command<?> command,
            @Nullable final String condition, @Nullable final Thing entity) {
        checkNotNull(command, "Command");

        if (isDefined(command, entity) && (condition != null && entity != null)) {
                final DittoHeaders dittoHeaders = command.getDittoHeaders();
                final Criteria criteria = QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance())
                        .filterCriteria(condition, dittoHeaders);
                final Predicate<Thing> predicate = ThingPredicateVisitor.apply(criteria);
                if (!predicate.test(entity)) {
                    return new Right<>(ThingConditionFailedException.newBuilder(condition)
                            .dittoHeaders(dittoHeaders)
                            .build());
                }
        }
        return new Left<>(null);
    }

    /**
     * Checks if the validation should be applied.
     *
     * @param command the command used for checking if validation should be applied.
     * @param entity the entity to check the condition against.
     * @return @{code true} if the command should be applied otherwise @{code false}.
     */
    private boolean isDefined(final Command<?> command, @Nullable final Thing entity) {
        return !(command instanceof CreateThing) && entity != null;
    }

}
