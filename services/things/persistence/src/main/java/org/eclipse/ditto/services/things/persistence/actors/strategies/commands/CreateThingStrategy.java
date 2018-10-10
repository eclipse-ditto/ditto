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
import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newMutationResult;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.events.things.ThingCreated;

/**
 * This strategy handles the {@link CreateThingStrategy} command.
 */
@Immutable
public final class CreateThingStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<CreateThing, Thing> {

    private static final CreateThingStrategy INSTANCE = new CreateThingStrategy();

    /**
     * Constructs a new {@link CreateThingStrategy} object.
     */
    private CreateThingStrategy() {
        super(CreateThing.class);
    }

    public static CreateThingStrategy getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isDefined(final CreateThing command) {
        return CreateThing.class.isAssignableFrom(command.getClass());
    }

    @Override
    public boolean isDefined(final Context context, @Nullable final Thing thing, final CreateThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return !thingExists && Objects.equals(context.getThingId(), command.getId());
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final CreateThing command) {
        final DittoHeaders commandHeaders = command.getDittoHeaders();

        // Thing not yet created - do so ..
        Thing newThing;
        try {
            newThing =
                    handleCommandVersion(context, command.getImplementedSchemaVersion(), command.getThing(),
                            commandHeaders);
        } catch (final DittoRuntimeException e) {
            return newErrorResult(e);
        }

        // before persisting, check if the Thing is valid and reject if not:
        final Result validateThingError =
                validateThing(context, command.getImplementedSchemaVersion(), newThing, commandHeaders);
        if (validateThingError != null) {
            return validateThingError;
        }

        // for v2 upwards, set the policy-id to the thing-id if none is specified:
        final boolean isV2Upwards = !JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion());
        if (isV2Upwards && !newThing.getPolicyId().isPresent()) {
                newThing = newThing.setPolicyId(context.getThingId());
        }

        final Instant modified = Instant.now();
        // provide modified and revision only in the response, not in the event (it is defined by the persistence)
        final Thing newThingWithModifiedAndRevision = newThing.toBuilder()
                .setModified(modified)
                .setRevision(nextRevision)
                .build();
        final ThingCreated thingCreated = ThingCreated.of(newThing, nextRevision, modified, commandHeaders);
        final CreateThingResponse createThingResponse =
                CreateThingResponse.of(newThingWithModifiedAndRevision, commandHeaders);

        return newMutationResult(command, thingCreated, createThingResponse, true, false, this);
    }


    private Thing handleCommandVersion(final Context context, final JsonSchemaVersion version, final Thing thing,
            final DittoHeaders dittoHeaders) {

        if (JsonSchemaVersion.V_1.equals(version)) {
            return enhanceNewThingWithFallbackAcl(setLifecycleActive(thing),
                    dittoHeaders.getAuthorizationContext());
        }
        // default case handle as v2 and upwards:
        else {
            //acl is not allowed to be set in v2
            if (thing.getAccessControlList().isPresent()) {
                throw AclNotAllowedException.newBuilder(context.getThingId()).dittoHeaders(dittoHeaders).build();
            }

            // policyId is required for v2
            if (!thing.getPolicyId().isPresent()) {
                throw PolicyIdMissingException.fromThingIdOnCreate(context.getThingId(), dittoHeaders);
            }

            return setLifecycleActive(thing);
        }
    }

    private static Thing setLifecycleActive(final Thing thing) {
        if (ThingLifecycle.ACTIVE.equals(thing.getLifecycle().orElse(null))) {
            return thing;
        }
        return ThingsModelFactory.newThingBuilder(thing)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .build();
    }

    /**
     * Retrieves the Thing with first authorization subjects as fallback for the ACL of the Thing if the passed
     * {@code newThing} has no ACL set.
     *
     * @param newThing the new Thing to take as a "base" and to check for presence of ACL inside.
     * @param authContext the AuthorizationContext to take the first AuthorizationSubject as fallback from.
     * @return the really new Thing with guaranteed ACL.
     */
    private Thing enhanceNewThingWithFallbackAcl(final Thing newThing, final AuthorizationContext authContext) {
        final ThingBuilder.FromCopy newThingBuilder = ThingsModelFactory.newThingBuilder(newThing);

        final Boolean isAclEmpty = newThing.getAccessControlList()
                .map(AccessControlList::isEmpty)
                .orElse(true);
        if (isAclEmpty) {
            // do the fallback and use the first authorized subject and give all permissions to it:
            final AuthorizationSubject authorizationSubject = authContext.getFirstAuthorizationSubject()
                    .orElseThrow(() -> new NullPointerException("AuthorizationContext does not contain an " +
                            "AuthorizationSubject!"));
            newThingBuilder.setPermissions(authorizationSubject, Thing.MIN_REQUIRED_PERMISSIONS);
        }

        return newThingBuilder.build();
    }

    @Nullable
    private Result validateThing(final Context context, final JsonSchemaVersion version, final Thing thing,
            final DittoHeaders headers) {
        final Optional<AccessControlList> accessControlList = thing.getAccessControlList();
        if (JsonSchemaVersion.V_1.equals(version)) {
            if (accessControlList.isPresent()) {
                final Validator aclValidator =
                        AclValidator.newInstance(accessControlList.get(), Thing.MIN_REQUIRED_PERMISSIONS);
                // before persisting, check if the ACL is valid and reject if not:
                if (!aclValidator.isValid()) {
                    final AclInvalidException aclInvalidException =
                            AclInvalidException.newBuilder(context.getThingId())
                                .dittoHeaders(headers)
                                .build();
                    return newErrorResult(aclInvalidException);
                }
            } else {
                final AclInvalidException aclInvalidException =
                        AclInvalidException.newBuilder(context.getThingId())
                                .dittoHeaders(headers)
                                .build();
                return newErrorResult(aclInvalidException);
            }
        }
        return null;
    }

    @Override
    public Optional<Thing> determineETagEntity(final CreateThing command, @Nullable final Thing thing) {
        return Optional.ofNullable(thing);
    }
}
