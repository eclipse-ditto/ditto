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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newErrorResult;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;

/**
 * This strategy handles the {@link ModifyThing} command for an already existing Thing.
 */
@Immutable
final class ModifyThingStrategy extends AbstractThingCommandStrategy<ModifyThing> {

    /**
     * Constructs a new {@code ModifyThingStrategy} object.
     */
    ModifyThingStrategy() {
        super(ModifyThing.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyThing command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingJsonObject = nonNullThing.toJson();
        ThingCommandSizeValidator.getInstance().ensureValidSize(
                thingJsonObject::getUpperBoundForStringSize,
                () -> thingJsonObject.toString().length(),
                command::getDittoHeaders);

        final Instant eventTs = getEventTimestamp();

        return handleModifyExistingWithV2Command(context, nonNullThing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent<?>> handleModifyExistingWithV2Command(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final ModifyThing command,
            @Nullable final Metadata metadata) {

        // ensure the Thing contains a policy ID
        final Thing thingWithPolicyId = containsPolicyId(command)
                ? command.getThing()
                : copyPolicyId(context, thing, command.getThing());

        return applyModifyCommand(context, thing, eventTs, nextRevision,
                ModifyThing.of(command.getEntityId(), thingWithPolicyId, null, command.getDittoHeaders()),
                metadata);
    }

    private Result<ThingEvent<?>> applyModifyCommand(final Context<ThingId> context, final Thing thing,
            final Instant eventTs, final long nextRevision, final ModifyThing command,
            @Nullable final Metadata metadata) {

        // make sure that the ThingModified-Event contains all data contained in the resulting existingThing (this is
        // required e.g. for updating the search-index)
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Thing modifiedThing = applyThingModifications(command.getThing(), thing, eventTs, nextRevision);

        final ThingEvent<?> event =
                ThingModified.of(modifiedThing, nextRevision, eventTs, dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyThingResponse.modified(context.getState(), dittoHeaders), modifiedThing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    /**
     * Applies the modifications from {@code thingWithModifications} to {@code builder}. The modified thing
     * overwrites the existing thing of {@code builder}.
     *
     * @param thingWithModifications the thing containing the modifications.
     * @param existingThing the existing thing.
     * @param eventTs the timestamp of the modification event.
     * @param nextRevision the next revision number.
     * @return the modified Thing.
     */
    private static Thing applyThingModifications(final Thing thingWithModifications, final Thing existingThing,
            final Instant eventTs, final long nextRevision) {

        final ThingBuilder.FromCopy builder = existingThing.toBuilder()
                .setModified(eventTs)
                .setRevision(nextRevision)
                .removeAllAttributes()
                .removeAllFeatures()
                .removeDefinition();

        thingWithModifications.getPolicyEntityId().ifPresent(builder::setPolicyId);
        thingWithModifications.getDefinition().ifPresent(builder::setDefinition);
        thingWithModifications.getAttributes().ifPresent(builder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(builder::setFeatures);

        return builder.build();
    }

    private static boolean containsPolicyId(final ModifyThing command) {
        return command.getThing().getPolicyEntityId().isPresent();
    }

    private static Thing copyPolicyId(final CommandStrategy.Context<ThingId> ctx, final Thing from, final Thing to) {
        return to.toBuilder()
                .setPolicyId(from.getPolicyEntityId().orElseGet(() -> {
                    ctx.getLog()
                            .error("Thing <{}> is schema version 2 and should therefore contain a policyId",
                                    ctx.getState());
                    return null;
                }))
                .build();
    }

    @Override
    public Result<ThingEvent<?>> unhandled(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyThing command) {
        return newErrorResult(new ThingNotAccessibleException(context.getState(), command.getDittoHeaders()), command);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyThing command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyThing thingCommand, @Nullable final Thing newEntity) {
        return Optional.of(getEntityOrThrow(newEntity)).flatMap(EntityTag::fromEntity);
    }

}
