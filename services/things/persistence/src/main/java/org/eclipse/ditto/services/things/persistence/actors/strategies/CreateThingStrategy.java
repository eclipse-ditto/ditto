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

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.concurrent.NotThreadSafe;

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
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.events.things.ThingCreated;

/**
 * This strategy handles the {@link CreateThing} command.
 */
@NotThreadSafe
public final class CreateThingStrategy extends AbstractReceiveStrategy<CreateThing> {

    /**
     * Constructs a new {@code CreateThingStrategy} object.
     */
    public CreateThingStrategy() {
        super(CreateThing.class);
    }

    @Override
    public BiFunction<Context, CreateThing, Boolean> getPredicate() {
        return (ctx, command) -> Objects.equals(ctx.getThingId(), command.getId());
    }

    @Override
    protected Result doApply(final Context context, final CreateThing command) {
        final Thing thing = context.getThing();
        final String thingId = context.getThingId();
        final long nextRevision = context.nextRevision();
        final DittoHeaders commandHeaders = command.getDittoHeaders();

        // Thing not yet created - do so ..
        final Thing newThing;
        try {
            newThing = handleCommandVersion(command.getImplementedSchemaVersion(), thingId, command.getThing(),
                    commandHeaders);
        } catch (final DittoRuntimeException e) {
            return ImmutableResult.of(e);
        }

        // before persisting, check if the Thing is valid and reject if not:
        if (!isValidThing(command.getImplementedSchemaVersion(), newThing)) {

            // TODO check if this is always the correct exception
            return ImmutableResult.of(AclInvalidException.newBuilder(thingId)
                    .dittoHeaders(commandHeaders)
                    .build());
        }

        final ThingCreated thingCreated;
        if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
            thingCreated = ThingCreated.of(newThing, nextRevision, eventTimestamp(), commandHeaders);
        }
        // default case handle as v2 and upwards:
        else {
            thingCreated =
                    ThingCreated.of(newThing.setPolicyId(newThing.getPolicyId().orElse(thingId)), nextRevision,
                            eventTimestamp(), commandHeaders);
        }

        context.log().debug("Created new Thing with ID <{}>.", thingId);
        return ImmutableResult.of(thingCreated, CreateThingResponse.of(thing, thingCreated.getDittoHeaders()));

        //TODO include in result
        // becomeThingCreatedHandler();
    }

    private Thing handleCommandVersion(final JsonSchemaVersion version, final String thingId, final Thing thing,
            final DittoHeaders dittoHeaders) {

        if (JsonSchemaVersion.V_1.equals(version)) {
            return enhanceNewThingWithFallbackAcl(enhanceThingWithLifecycle(thing),
                    dittoHeaders.getAuthorizationContext());
        }
        // default case handle as v2 and upwards:
        else {
            //acl is not allowed to be set in v2
            if (thing.getAccessControlList().isPresent()) {
                throw AclNotAllowedException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
            }

            // policyId is required for v2
            if (!thing.getPolicyId().isPresent()) {
                throw PolicyIdMissingException.fromThingIdOnCreate(thingId, dittoHeaders);
            }

            return enhanceThingWithLifecycle(thing);
        }
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

    private boolean isValidThing(final JsonSchemaVersion version, final Thing thing) {
        final Optional<AccessControlList> accessControlList = thing.getAccessControlList();
        if (JsonSchemaVersion.V_1.equals(version)) {
            if (accessControlList.isPresent()) {
                final Validator aclValidator =
                        AclValidator.newInstance(accessControlList.get(), Thing.MIN_REQUIRED_PERMISSIONS);
                // before persisting, check if the ACL is valid and reject if not:
                return aclValidator.isValid();
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public BiFunction<Context, CreateThing, Result> getUnhandledFunction() {
        return (ctx, command) -> {
            throw new IllegalArgumentException(
                    MessageFormat.format(ThingPersistenceActor.UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
        };
    }

    private static Thing enhanceThingWithLifecycle(final Thing thing) {
        final ThingBuilder.FromCopy thingBuilder = ThingsModelFactory.newThingBuilder(thing);
        if (!thing.getLifecycle().isPresent()) {
            thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);
        }

        return thingBuilder.build();
    }

}
