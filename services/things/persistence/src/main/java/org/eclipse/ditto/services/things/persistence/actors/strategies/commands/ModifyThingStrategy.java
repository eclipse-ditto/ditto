/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newErrorResult;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * This strategy handles the {@link ModifyThing} command for an already existing Thing.
 */
@Immutable
final class ModifyThingStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyThing, Thing> {

    /**
     * Constructs a new {@code ModifyThingStrategy} object.
     */
    ModifyThingStrategy() {
        super(ModifyThing.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyThing command) {

        final Thing nonNullThing = getThingOrThrow(thing);

        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> nonNullThing.toJsonString().length(),
                command::getDittoHeaders);

        if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
            return handleModifyExistingWithV1Command(context, nonNullThing, nextRevision, command);
        }

        // from V2 upwards, use this logic:
        return handleModifyExistingWithV2Command(context, nonNullThing, nextRevision, command);
    }

    private Result handleModifyExistingWithV1Command(final Context context, final Thing thing, final long nextRevision,
            final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(thing.getImplementedSchemaVersion())) {
            return handleModifyExistingV1WithV1Command(context, thing, nextRevision, command);
        } else {
            return handleModifyExistingV2WithV1Command(context, thing, nextRevision, command);
        }
    }

    private Result handleModifyExistingV1WithV1Command(final Context context,
            final Thing thing, final long nextRevision,
            final ModifyThing command) {
        final String thingId = context.getThingId();

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

                final ThingModified thingModified = ThingModified.of(mergedThing, nextRevision, getEventTimestamp(),
                        dittoHeaders);
                return ResultFactory.newMutationResult(command, thingModified, ModifyThingResponse.modified(thingId, dittoHeaders), this);
            } else {
                context.getLog().error("Thing <{}> has no ACL entries even though it is of schema version 1. " +
                        "Persisting the event nevertheless to not block the user because of an " +
                        "unknown internal state.", thingId);
                final ThingModified thingModified =
                        ThingModified.of(command.getThing(), nextRevision, getEventTimestamp(), dittoHeaders);
                return ResultFactory.newMutationResult(command, thingModified, ModifyThingResponse.modified(thingId, dittoHeaders), this);
            }
        }
    }

    private Result handleModifyExistingV2WithV1Command(final Context context,
            final Thing thing, final long nextRevision,
            final ModifyThing command) {
        final String thingId = context.getThingId();
        // remove any acl information from command and add the current policy Id
        final Thing thingWithoutAcl = removeACL(copyPolicyId(context, thing, command.getThing()));
        final ThingModified thingModified =
                ThingModified.of(thingWithoutAcl, nextRevision, getEventTimestamp(), command.getDittoHeaders());
        return ResultFactory.newMutationResult(command, thingModified, ModifyThingResponse.modified(thingId, command.getDittoHeaders()),
                this);
    }

    private static Thing removeACL(final Thing thing) {
        return thing.toBuilder()
                .removeAllPermissions()
                .build();
    }

    private Result handleModifyExistingWithV2Command(final Context context, final Thing thing,
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
    private Result handleModifyExistingV1WithV2Command(final Context context,
            final Thing thing, final long nextRevision, final ModifyThing command) {
        if (containsPolicyId(command)) {
            final Thing thingWithoutAcl = thing.toBuilder().removeAllPermissions().build();
            return applyModifyCommand(context, thingWithoutAcl, nextRevision, command);
        } else {
            return newErrorResult(
                    PolicyIdMissingException.fromThingIdOnUpdate(context.getThingId(), command.getDittoHeaders()));
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent via API v2 and targets a Thing with API version V2.
     */
    private Result handleModifyExistingV2WithV2Command(final Context context,
            final Thing thing, final long nextRevision,
            final ModifyThing command) {
        // ensure the Thing contains a policy ID
        final Thing thingWithPolicyId = containsPolicyId(command)
                ? command.getThing()
                : copyPolicyId(context, thing, command.getThing());

        return applyModifyCommand(context, thing, nextRevision,
                ModifyThing.of(command.getThingId(), thingWithPolicyId, null, command.getDittoHeaders()));
    }

    private Result applyModifyCommand(final Context context, final Thing thing,
            final long nextRevision, final ModifyThing command) {
        // make sure that the ThingModified-Event contains all data contained in the resulting existingThing (this is
        // required e. g. for updating the search-index)
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                ThingModified.of(mergeThingModifications(command.getThing(), thing, nextRevision),
                        nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyThingResponse.modified(context.getThingId(), dittoHeaders), this);
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

        thingWithModifications.getPolicyId().ifPresent(builder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(builder::setPermissions);
        thingWithModifications.getAttributes().ifPresent(builder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(builder::setFeatures);

        return builder.build();
    }

    private static boolean containsPolicyId(final ModifyThing command) {
        return command.getThing().getPolicyId().isPresent();
    }

    private static Thing copyPolicyId(final CommandStrategy.Context ctx, final Thing from, final Thing to) {
        return to.toBuilder()
                .setPolicyId(from.getPolicyId().orElseGet(() -> {
                    ctx.getLog()
                            .error("Thing <{}> is schema version 2 and should therefore contain a policyId",
                                    ctx.getThingId());
                    return null;
                }))
                .build();
    }

    @Override
    protected Result unhandled(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyThing command) {
        return newErrorResult(new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

    @Override
    public Optional<Thing> determineETagEntity(final ModifyThing thingCommand, @Nullable final Thing thing) {
        return Optional.of(getThingOrThrow(thing));
    }
}
