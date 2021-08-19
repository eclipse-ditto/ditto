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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.internal.utils.persistentactors.MetadataFromSignal;
import org.eclipse.ditto.internal.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import scala.util.Either;

/**
 * Abstract base class for {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.things.api.commands.sudo.SudoCommand} are handled which are no ThingCommands.
 */
@Immutable
abstract class AbstractThingCommandStrategy<C extends Command<C>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Thing, ThingId, ThingEvent<?>> {

    private static final ConditionalHeadersValidator VALIDATOR =
            ThingsConditionalHeadersValidatorProvider.getInstance();

    private final ThingConditionValidator thingConditionValidator;

    protected AbstractThingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
        this.thingConditionValidator = ThingConditionValidator.getInstance();
    }

    @Override
    protected ConditionalHeadersValidator getValidator() {
        return VALIDATOR;
    }

    /**
     * Execute a command strategy after it is determined applicable.
     *
     * @param context context of the persistent actor.
     * @param entity entity of the persistent actor.
     * @param nextRevision the next revision to allocate to events.
     * @param command the incoming command.
     * @return result of the command strategy.
     */
    @Override
    public Result<ThingEvent<?>> apply(final Context<ThingId> context, @Nullable final Thing entity,
            final long nextRevision, final C command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String condition = dittoHeaders.getCondition().orElse(null);

        context.getLog().withCorrelationId(command)
                .debug("Validating condition <{}> on command <{}>.", condition, command);
        final Either<Void, ThingConditionFailedException> validate =
                thingConditionValidator.validate(command, condition, entity);

        if (validate.isRight()) {
            final ThingConditionFailedException thingConditionFailedException = validate.right().get();

            context.getLog().withCorrelationId(command)
                    .debug("Validating condition failed with exception <{}>.",
                            thingConditionFailedException.getMessage());
            return ResultFactory.newErrorResult(thingConditionFailedException, command);
        }
        context.getLog().withCorrelationId(command).debug("Validating condition succeeded.");

        return super.apply(context, entity, nextRevision, command);
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final Thing entity, final C command) {

        if (command instanceof WithOptionalEntity) {
            final Metadata existingRelativeMetadata = Optional.ofNullable(entity)
                    .flatMap(Thing::getMetadata)
                    .flatMap(m -> m.getValue(command.getResourcePath()))
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(Metadata::newMetadata)
                    .orElse(null);
            final MetadataFromSignal relativeMetadata =
                    MetadataFromSignal.of(command, (WithOptionalEntity) command, existingRelativeMetadata);
            return Optional.ofNullable(relativeMetadata.get());
        }
        return Optional.empty();
    }

    @Override
    public boolean isDefined(final C command) {
        return command instanceof ThingCommand;
    }

}
