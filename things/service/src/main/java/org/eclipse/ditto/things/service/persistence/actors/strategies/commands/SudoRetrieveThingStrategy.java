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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link SudoRetrieveThing} command.
 */
@Immutable
final class SudoRetrieveThingStrategy extends AbstractThingCommandStrategy<SudoRetrieveThing> {

    /**
     * Constructs a new {@code SudoRetrieveThingStrategy} object.
     */
    SudoRetrieveThingStrategy() {
        super(SudoRetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context<ThingId> context, @Nullable final Thing thing,
            final SudoRetrieveThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !t.isDeleted())
                .orElse(false);
        // when thing is null, there is nothing to retrieve.
        final boolean shouldRetrieveDeleted = thing != null && command.getDittoHeaders().shouldRetrieveDeleted();

        return Objects.equals(context.getState(), command.getEntityId()) && (thingExists || shouldRetrieveDeleted);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final SudoRetrieveThing command,
            @Nullable final Metadata metadata) {

        final Thing theThing = getEntityOrThrow(thing);

        final JsonSchemaVersion jsonSchemaVersion = determineSchemaVersion(command, theThing);
        final JsonObject thingJson = command.getSelectedFields()
                .map(selectedFields -> {
                    final Features features = thing.getFeatures().orElse(ThingsModelFactory.emptyFeatures());
                    final JsonFieldSelector expandedFieldSelector =
                            ThingsModelFactory.expandFeatureIdWildcards(features, selectedFields);
                    return theThing.toJson(jsonSchemaVersion, expandedFieldSelector, FieldType.regularOrSpecial());
                })
                .orElseGet(() -> theThing.toJson(jsonSchemaVersion, FieldType.regularOrSpecial()));

        return ResultFactory.newQueryResult(command,
                appendETagHeaderIfProvided(command, SudoRetrieveThingResponse.of(thingJson, command.getDittoHeaders()),
                        thing));
    }

    private static JsonSchemaVersion determineSchemaVersion(final SudoRetrieveThing command, final Thing thing) {
        return command.useOriginalSchemaVersion()
                ? thing.getImplementedSchemaVersion()
                : command.getImplementedSchemaVersion();
    }

    @Override
    public Result<ThingEvent<?>> unhandled(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final SudoRetrieveThing command) {
        return ResultFactory.newErrorResult(
                new ThingNotAccessibleException(context.getState(), command.getDittoHeaders()), command);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final SudoRetrieveThing command,
            @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final SudoRetrieveThing command, @Nullable final Thing newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
