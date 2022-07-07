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
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.internal.utils.persistentactors.MetadataFromSignal;
import org.eclipse.ditto.internal.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.api.commands.sudo.ThingSudoCommand;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Abstract base class for {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.things.api.commands.sudo.ThingSudoCommand} are handled which are no ThingCommands.
 */
@Immutable
abstract class AbstractThingCommandStrategy<C extends Command<C>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Thing, ThingId, ThingEvent<?>> {

    private static final ConditionalHeadersValidator VALIDATOR =
            ThingsConditionalHeadersValidatorProvider.getInstance();

    protected AbstractThingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
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

        final var loggerWithCorrelationId = context.getLog().withCorrelationId(command);
        final var thingConditionFailed = command.getDittoHeaders()
                .getCondition()
                .flatMap(condition -> ThingConditionValidator.validate(command, condition, entity));
        final Boolean liveChannelConditionPassed = command.getDittoHeaders()
                .getLiveChannelCondition()
                .map(condition -> ThingConditionValidator.validate(command, condition, entity).isEmpty())
                .orElse(false);

        final Result<ThingEvent<?>> result;
        if (thingConditionFailed.isPresent()) {
            final var conditionFailedException = thingConditionFailed.get();
            loggerWithCorrelationId.debug("Validating condition failed with exception <{}>.",
                    conditionFailedException.getMessage());
            result = ResultFactory.newErrorResult(conditionFailedException, command);
        } else if (command.getDittoHeaders().getLiveChannelCondition().isPresent()) {
            final var enhancedHeaders = command.getDittoHeaders()
                    .toBuilder()
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(),
                            liveChannelConditionPassed.toString())
                    .build();
            result = super.apply(context, entity, nextRevision, command.setDittoHeaders(enhancedHeaders));
        } else {
            result = super.apply(context, entity, nextRevision, command);
        }
        return result;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final Thing entity, final C command) {

        if (command instanceof WithOptionalEntity withOptionalEntity) {
            final Metadata existingRelativeMetadata = Optional.ofNullable(entity)
                    .flatMap(Thing::getMetadata)
                    .flatMap(m -> m.getValue(command.getResourcePath()))
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(Metadata::newMetadata)
                    .orElse(null);
            final MetadataFromSignal relativeMetadata =
                    MetadataFromSignal.of(command, withOptionalEntity, existingRelativeMetadata);
            return Optional.ofNullable(relativeMetadata.get());
        }
        return Optional.empty();
    }

    @Override
    public boolean isDefined(final C command) {
        return command instanceof ThingCommand || command instanceof ThingSudoCommand;
    }
}
