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
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

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

import akka.japi.pf.FI;

/**
 * This strategy handles the {@link ModifyThing} command for an already existing Thing.
 */
@NotThreadSafe
final class ModifyThingStrategy extends AbstractThingCommandStrategy<ModifyThing> {

    /**
     * Constructs a new {@code ModifyThingStrategy} object.
     */
    public ModifyThingStrategy() {
        super(ModifyThing.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
            handleModifyExistingWithV1Command(command);
        } else {
            // from V2 upwards, use this logic:
            handleModifyExistingWithV2Command(command);
        }
    }

    private void handleModifyExistingWithV1Command(final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(thing().getImplementedSchemaVersion())) {
            handleModifyExistingV1WithV1Command(command);
        } else {
            handleModifyExistingV2WithV1Command(command);
        }
    }

    private void handleModifyExistingV1WithV1Command(final ModifyThing command) {
        // if the ACL was modified together with the Thing, an additional check is necessary
        final boolean isCommandAclEmpty = command.getThing()
                .getAccessControlList()
                .map(AccessControlList::isEmpty)
                .orElse(true);
        if (!isCommandAclEmpty) {
            applyModifyCommand(command);
        } else {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<AccessControlList> existingAccessControlList = thing().getAccessControlList();
            if (existingAccessControlList.isPresent()) {
                // special apply - take the ACL of the persisted thing instead of the new one in the command:
                final Thing modifiedThingWithOldAcl = ThingsModelFactory.newThingBuilder(command.getThing())
                        .removeAllPermissions()
                        .setPermissions(existingAccessControlList.get())
                        .build();
                final ThingModified thingModified =
                        ThingModified.of(modifiedThingWithOldAcl, nextRevision(), eventTimestamp(), dittoHeaders);
                persistAndApplyEvent(thingModified,
                        event -> notifySender(ModifyThingResponse.modified(thingId, dittoHeaders)));
            } else {
                log.error("Thing <{}> has no ACL entries even though it is of schema version 1. " +
                        "Persisting the event nevertheless to not block the user because of an " +
                        "unknown internal state.", thingId);
                final ThingModified thingModified =
                        ThingModified.of(command.getThing(), nextRevision(), eventTimestamp(), dittoHeaders);
                persistAndApplyEvent(thingModified,
                        event -> notifySender(ModifyThingResponse.modified(thingId, dittoHeaders)));
            }
        }
    }

    private void handleModifyExistingV2WithV1Command(final ModifyThing command) {
        // remove any acl information from command and add the current policy Id
        final Thing thingWithoutAcl = removeACL(copyPolicyId(thing(), command.getThing()));
        final ThingModified thingModified =
                ThingModified.of(thingWithoutAcl, nextRevision(), eventTimestamp(), command.getDittoHeaders());
        persistAndApplyEvent(thingModified,
                event -> notifySender(ModifyThingResponse.modified(thingId, command.getDittoHeaders())));
    }

    private void handleModifyExistingWithV2Command(final ModifyThing command) {
        if (JsonSchemaVersion.V_1.equals(thing().getImplementedSchemaVersion())) {
            handleModifyExistingV1WithV2Command(command);
        } else {
            handleModifyExistingV2WithV2Command(command);
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent
     * via API v2 and targets a Thing with API version V1.
     */
    private void handleModifyExistingV1WithV2Command(final ModifyThing command) {
        if (containsPolicy(command)) {
            applyModifyCommand(command);
        } else {
            notifySender(getSender(),
                    PolicyIdMissingException.fromThingIdOnUpdate(thingId, command.getDittoHeaders()));
        }
    }

    /**
     * Handles a {@link ModifyThing} command that was sent
     * via API v2 and targets a Thing with API version V2.
     */
    private void handleModifyExistingV2WithV2Command(final ModifyThing command) {
        // ensure the Thing contains a policy ID
        final Thing thingWithPolicyId =
                containsPolicyId(command) ? command.getThing() : copyPolicyId(thing(), command.getThing());
        applyModifyCommand(ModifyThing.of(command.getThingId(),
                thingWithPolicyId,
                null,
                command.getDittoHeaders()));
    }

    private void applyModifyCommand(final ModifyThing command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        // make sure that the ThingModified-Event contains all data contained in the resulting thing (this is
        // required e.g. for updating the search-index)
        final long nextRevision = nextRevision();
        final ThingBuilder.FromCopy modifiedThingBuilder = thing().toBuilder()
                .setRevision(nextRevision)
                .setModified(null);
        mergeThingModifications(command.getThing(), modifiedThingBuilder);
        final ThingModified thingModified = ThingModified.of(modifiedThingBuilder.build(), nextRevision,
                eventTimestamp(), dittoHeaders);

        persistAndApplyEvent(thingModified,
                event -> notifySender(ModifyThingResponse.modified(thingId, dittoHeaders)));
    }

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code builder}. Merge is implemented very
     * simple: All first level fields of {@code thingWithModifications} overwrite the first level fields of {@code
     * builder}. If a field does not exist in {@code thingWithModifications}, a maybe existing field in {@code
     * builder} remains unchanged.
     *
     * @param thingWithModifications the thing containing the modifications.
     * @param builder the builder to be modified.
     */
    private void mergeThingModifications(final Thing thingWithModifications, final ThingBuilder.FromCopy builder) {
        thingWithModifications.getPolicyId().ifPresent(builder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(builder::setPermissions);
        thingWithModifications.getAttributes().ifPresent(builder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(builder::setFeatures);
    }

    private boolean containsPolicy(final ModifyThing command) {
        return containsInitialPolicy(command) || containsPolicyId(command);
    }

    private boolean containsInitialPolicy(final ModifyThing command) {
        return command.getInitialPolicy().isPresent();
    }

    private boolean containsPolicyId(final ModifyThing command) {
        return command.getThing().getPolicyId().isPresent();
    }

    private Thing copyPolicyId(final Thing from, final Thing to) {
        return to.toBuilder()
                .setPolicyId(from.getPolicyId().orElseGet(() -> {
                    log.error("Thing <{}> is schema version 2 and should therefore contain a policyId", thingId);
                    return null;
                }))
                .build();
    }

    private Thing removeACL(final Thing thing) {
        return thing.toBuilder()
                .removeAllPermissions()
                .build();
    }

    @Override
    public FI.UnitApply<ModifyThing> getUnhandledFunction() {
        return command -> notifySender(new ThingNotAccessibleException(thingId, command.getDittoHeaders()));
    }

}
