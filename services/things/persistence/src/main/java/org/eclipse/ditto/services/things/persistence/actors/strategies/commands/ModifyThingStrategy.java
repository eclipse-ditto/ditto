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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newErrorResult;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

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
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyThing command) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingJsonObject = nonNullThing.toJson();
        ThingCommandSizeValidator.getInstance().ensureValidSize(
                thingJsonObject::getUpperBoundForStringSize,
                () -> thingJsonObject.toString().length(),
                command::getDittoHeaders);

        if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
            return handleModifyExistingWithV1Command(context, nonNullThing, nextRevision, command);
        }

        // from V2 upwards, use this logic:
        return handleModifyExistingWithV2Command(context, nonNullThing, nextRevision, command);
    }

    private Result<ThingEvent> handleModifyExistingWithV1Command(final Context<ThingId> context, final Thing thing,
            final long nextRevision, final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(thing.getImplementedSchemaVersion())) {
            return handleModifyExistingV1WithV1Command(context, thing, nextRevision, command);
        } else {
            return handleModifyExistingV2WithV1Command(context, thing, nextRevision, command);
        }
    }

    private Result<ThingEvent> handleModifyExistingV1WithV1Command(final Context<ThingId> context,
            final Thing thing, final long nextRevision, final ModifyThing command) {
        final ThingId thingId = context.getState();

        // if the ACL was modified together with the Thing, an additional check is necessary
        final boolean isCommandAclEmpty = command.getThing()
                .getAccessControlList()
                .map(AccessControlList::isEmpty)
                .orElse(true);

        if (!isCommandAclEmpty) {
            return applyModifyCommand(context, thing, nextRevision, command);
        } else {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<AccessControlList> existingAccessControlList = thing.getAccessControlList();
            if (existingAccessControlList.isPresent()) {
                // special apply - take the ACL of the persisted thing instead of the new one in the command:
                final Thing newThingWithoutAcl = command.getThing().toBuilder().removeAllPermissions().build();
                final Thing mergedThing = mergeThingModifications(newThingWithoutAcl, thing, nextRevision);

                final ThingEvent thingModified =
                        ThingModified.of(mergedThing, nextRevision, getEventTimestamp(), dittoHeaders);
                final WithDittoHeaders response =
                        appendETagHeaderIfProvided(command, ModifyThingResponse.modified(thingId, dittoHeaders),
                                mergedThing);
                return ResultFactory.newMutationResult(command, thingModified, response);
            } else {
                context.getLog().error("Thing <{}> has no ACL entries even though it is of schema version 1. " +
                        "Persisting the event nevertheless to not block the user because of an " +
                        "unknown internal state.", thingId);
                final Thing modifiedThing = command.getThing().toBuilder().setRevision(nextRevision).build();
                final ThingEvent thingModified =
                        ThingModified.of(modifiedThing, nextRevision, getEventTimestamp(), dittoHeaders);
                final WithDittoHeaders response =
                        appendETagHeaderIfProvided(command, ModifyThingResponse.modified(thingId, dittoHeaders),
                                modifiedThing);
                return ResultFactory.newMutationResult(command, thingModified, response);
            }
        }
    }

    private Result<ThingEvent> handleModifyExistingV2WithV1Command(final Context<ThingId> context,
            final Thing thing, final long nextRevision,
            final ModifyThing command) {
        final ThingId thingId = context.getState();
        // remove any acl information from command and add the current policy Id
        final Thing thingWithoutAcl = removeACL(copyPolicyId(context, thing, command.getThing()), nextRevision);
        final ThingEvent thingModified =
                ThingModified.of(thingWithoutAcl, nextRevision, getEventTimestamp(), command.getDittoHeaders());
        final WithDittoHeaders response =
                appendETagHeaderIfProvided(command, ModifyThingResponse.modified(thingId, command.getDittoHeaders()),
                        thingWithoutAcl);
        return ResultFactory.newMutationResult(command, thingModified, response);
    }

    private static Thing removeACL(final Thing thing, final long nextRevision) {
        return thing.toBuilder()
                .removeAllPermissions()
                .setRevision(nextRevision)
                .build();
    }

    private Result<ThingEvent> handleModifyExistingWithV2Command(final Context<ThingId> context, final Thing thing,
            final long nextRevision, final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(thing.getImplementedSchemaVersion())) {
            return handleModifyExistingV1WithV2Command(context, thing, nextRevision, command);
        } else {
            return handleModifyExistingV2WithV2Command(context, thing, nextRevision, command);
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent via API v2 and targets a Thing with API version V1.
     */
    private Result<ThingEvent> handleModifyExistingV1WithV2Command(final Context<ThingId> context,
            final Thing thing, final long nextRevision, final ModifyThing command) {
        if (containsPolicyId(command)) {
            final Thing thingWithoutAcl = thing.toBuilder().removeAllPermissions().build();
            return applyModifyCommand(context, thingWithoutAcl, nextRevision, command);
        } else {
            return newErrorResult(
                    PolicyIdMissingException.fromThingIdOnUpdate(context.getState(),
                            command.getDittoHeaders()));
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent via API v2 and targets a Thing with API version V2.
     */
    private Result<ThingEvent> handleModifyExistingV2WithV2Command(final Context<ThingId> context,
            final Thing thing, final long nextRevision,
            final ModifyThing command) {
        // ensure the Thing contains a policy ID
        final Thing thingWithPolicyId = containsPolicyId(command)
                ? command.getThing()
                : copyPolicyId(context, thing, command.getThing());

        return applyModifyCommand(context, thing, nextRevision,
                ModifyThing.of(command.getThingEntityId(), thingWithPolicyId, null, command.getDittoHeaders()));
    }

    private Result<ThingEvent> applyModifyCommand(final Context<ThingId> context, final Thing thing,
            final long nextRevision, final ModifyThing command) {
        // make sure that the ThingModified-Event contains all data contained in the resulting existingThing (this is
        // required e. g. for updating the search-index)
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Thing modifiedThing = mergeThingModifications(command.getThing(), thing, nextRevision);

        final ThingEvent event =
                ThingModified.of(modifiedThing, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyThingResponse.modified(context.getState(), dittoHeaders), modifiedThing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code builder}. Merge is implemented very
     * simple: All first level fields of {@code thingWithModifications} overwrite the first level fields of {@code
     * builder}. If a field does not exist in {@code thingWithModifications}, a maybe existing field in {@code builder}
     * remains unchanged.
     *
     * @param thingWithModifications the thing containing the modifications.
     */
    private static Thing mergeThingModifications(final Thing thingWithModifications, final Thing existingThing,
            final long nextRevision) {

        final ThingBuilder.FromCopy builder = existingThing.toBuilder()
                .setRevision(nextRevision)
                .setModified(null);

        thingWithModifications.getPolicyEntityId().ifPresent(builder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(builder::setPermissions);
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
    public Result<ThingEvent> unhandled(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyThing command) {
        return newErrorResult(new ThingNotAccessibleException(context.getState(), command.getDittoHeaders()));
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
