/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newResult;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * This strategy handles the {@link ModifyThing} command for an already existing Thing.
 */
@Immutable
final class ModifyThingStrategy extends AbstractCommandStrategy<ModifyThing> {

    /**
     * Constructs a new {@code ModifyThingStrategy} object.
     */
    ModifyThingStrategy() {
        super(ModifyThing.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
            return handleModifyExistingWithV1Command(context, command);
        }

        // from V2 upwards, use this logic:
        return handleModifyExistingWithV2Command(context, command);
    }

    private static Result handleModifyExistingWithV1Command(final Context context, final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(context.getThingOrThrow().getImplementedSchemaVersion())) {
            return handleModifyExistingV1WithV1Command(context, command);
        } else {
            return handleModifyExistingV2WithV1Command(context, command);
        }
    }

    private static Result handleModifyExistingV1WithV1Command(final Context context, final ModifyThing command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThingOrThrow();
        final long nextRevision = context.getNextRevision();

        // if the ACL was modified together with the Thing, an additional check is necessary
        final boolean isCommandAclEmpty = command.getThing()
                .getAccessControlList()
                .map(AccessControlList::isEmpty)
                .orElse(true);
        if (!isCommandAclEmpty) {
            return applyModifyCommand(context, command);
        } else {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<AccessControlList> existingAccessControlList = thing.getAccessControlList();
            if (existingAccessControlList.isPresent()) {
                // special apply - take the ACL of the persisted thing instead of the new one in the command:
                final Thing modifiedThingWithOldAcl = ThingsModelFactory.newThingBuilder(command.getThing())
                        .removeAllPermissions()
                        .setPermissions(existingAccessControlList.get())
                        .build();
                final ThingModified thingModified =
                        ThingModified.of(modifiedThingWithOldAcl, nextRevision, getEventTimestamp(), dittoHeaders);
                return newResult(thingModified, ModifyThingResponse.modified(thingId, dittoHeaders));
            } else {
                context.getLog().error("Thing <{}> has no ACL entries even though it is of schema version 1. " +
                        "Persisting the event nevertheless to not block the user because of an " +
                        "unknown internal state.", thingId);
                final ThingModified thingModified =
                        ThingModified.of(command.getThing(), nextRevision, getEventTimestamp(), dittoHeaders);
                return newResult(thingModified, ModifyThingResponse.modified(thingId, dittoHeaders));
            }
        }
    }

    private static Result handleModifyExistingV2WithV1Command(final Context context, final ModifyThing command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThingOrThrow();
        final long nextRevision = context.getNextRevision();
        // remove any acl information from command and add the current policy Id
        final Thing thingWithoutAcl = removeACL(copyPolicyId(context, thing, command.getThing()));
        final ThingModified thingModified =
                ThingModified.of(thingWithoutAcl, nextRevision, getEventTimestamp(), command.getDittoHeaders());
        return newResult(thingModified, ModifyThingResponse.modified(thingId, command.getDittoHeaders()));
    }

    private static Thing removeACL(final Thing thing) {
        return thing.toBuilder()
                .removeAllPermissions()
                .build();
    }

    private static Result handleModifyExistingWithV2Command(final Context context, final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(context.getThingOrThrow().getImplementedSchemaVersion())) {
            return handleModifyExistingV1WithV2Command(context, command);
        } else {
            return handleModifyExistingV2WithV2Command(context, command);
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent
     * via API v2 and targets a Thing with API version V1.
     */
    private static Result handleModifyExistingV1WithV2Command(final Context context, final ModifyThing command) {
        if (containsPolicy(command)) {
            return applyModifyCommand(context, command);
        } else {
            return newResult(
                    PolicyIdMissingException.fromThingIdOnUpdate(context.getThingId(), command.getDittoHeaders()));
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent
     * via API v2 and targets a Thing with API version V2.
     */
    private static Result handleModifyExistingV2WithV2Command(final Context context, final ModifyThing command) {
        // ensure the Thing contains a policy ID
        final Thing thingWithPolicyId = containsPolicyId(command)
                ? command.getThing()
                : copyPolicyId(context, context.getThingOrThrow(), command.getThing());

        return applyModifyCommand(context,
                ModifyThing.of(command.getThingId(), thingWithPolicyId, null, command.getDittoHeaders()));
    }

    private static Result applyModifyCommand(final Context context, final ModifyThing command) {
        // make sure that the ThingModified-Event contains all data contained in the resulting existingThing (this is
        // required e. g. for updating the search-index)
        final long nextRevision = context.getNextRevision();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return newResult(
                ThingModified.of(mergeThingModifications(command.getThing(), context.getThingOrThrow(), nextRevision),
                        nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyThingResponse.modified(context.getThingId(), dittoHeaders));
    }

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code builder}. Merge is implemented very
     * simple: All first level fields of {@code thingWithModifications} overwrite the first level fields of
     * {@code builder}. If a field does not exist in {@code thingWithModifications}, a maybe existing field in
     * {@code builder} remains unchanged.
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

    private static boolean containsPolicy(final ModifyThing command) {
        return containsInitialPolicy(command) || containsPolicyId(command);
    }

    private static boolean containsInitialPolicy(final ModifyThing command) {
        return command.getInitialPolicy().isPresent();
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
    protected Result unhandled(final Context context, final ModifyThing command) {
        return newResult(new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

}