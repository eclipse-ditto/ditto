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

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributesResponse;
import org.eclipse.ditto.things.model.signals.events.AttributesCreated;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link ModifyAttributes} command.
 */
@Immutable
final class ModifyAttributesStrategy extends AbstractThingModifyCommandStrategy<ModifyAttributes> {

    /**
     * Constructs a new {@code ModifyAttributesStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    ModifyAttributesStrategy(final ActorSystem actorSystem) {
        super(ModifyAttributes.class, actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyAttributes command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutAttributesJsonObject = nonNullThing.removeAttributes().toJson();
        final JsonObject attributesJsonObject = command.getAttributes().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutAttributes = thingWithoutAttributesJsonObject.getUpperBoundForStringSize();
                    final long attributesLength = attributesJsonObject.getUpperBoundForStringSize()
                            + "attributes".length() + 5L;
                    return lengthWithOutAttributes + attributesLength;
                },
                () -> {
                    final long lengthWithOutAttributes = thingWithoutAttributesJsonObject.toString().length();
                    final long attributesLength = attributesJsonObject.toString().length()
                            + "attributes".length() + 5L;
                    return lengthWithOutAttributes + attributesLength;
                },
                command::getDittoHeaders);

        return nonNullThing.getAttributes()
                .map(attributes -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    @Override
    protected CompletionStage<ModifyAttributes> performWotValidation(final ModifyAttributes command,
            @Nullable final Thing previousThing,
            @Nullable final Thing previewThing
    ) {
        return wotThingModelValidator.validateThingAttributes(
                Optional.ofNullable(previousThing).flatMap(Thing::getDefinition).orElse(null),
                command.getAttributes(),
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttributes command, @Nullable final Thing thing, @Nullable final Metadata metadata) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CompletionStage<ModifyAttributes> validatedStage = buildValidatedStage(command, thing);
        final CompletionStage<ThingEvent<?>> eventStage = validatedStage
                .thenApply(ModifyAttributes::getAttributes)
                .thenApply(attributes ->
                        AttributesModified.of(thingId, attributes, nextRevision, getEventTimestamp(), dittoHeaders,
                                metadata)
                );
        final CompletionStage<WithDittoHeaders> responseStage = validatedStage.thenApply(modifyAttributes ->
                appendETagHeaderIfProvided(modifyAttributes, ModifyAttributesResponse.modified(thingId,
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision)), thing)
        );

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttributes command, @Nullable final Thing thing, @Nullable final Metadata metadata) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CompletionStage<ModifyAttributes> validatedStage = buildValidatedStage(command, thing);
        final CompletionStage<ThingEvent<?>> eventStage = validatedStage
                .thenApply(ModifyAttributes::getAttributes)
                .thenApply(attributes ->
                        AttributesCreated.of(thingId, attributes, nextRevision, getEventTimestamp(), dittoHeaders,
                                metadata)
                );
        final CompletionStage<WithDittoHeaders> responseStage = validatedStage.thenApply(modifyAttributes ->
                appendETagHeaderIfProvided(modifyAttributes,
                        ModifyAttributesResponse.created(thingId, modifyAttributes.getAttributes(),
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                        thing)
        );

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyAttributes command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(Thing::getAttributes).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyAttributes command, @Nullable final Thing newEntity) {
        return Optional.of(command.getAttributes()).flatMap(EntityTag::fromEntity);
    }
}
