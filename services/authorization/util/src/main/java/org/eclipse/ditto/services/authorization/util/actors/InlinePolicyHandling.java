/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.util.actors;

import java.util.Optional;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

import akka.actor.ActorRef;

/**
 * Mixin to handle inline policy in the first {@code CreateThing} command to a thing.
 */
// TODO: javadoc
interface InlinePolicyHandling extends CommandEnforcementSelfType {

    default boolean handleInitialCreateThing(final CreateThing createThing, final ActorRef sender) {
        if (createThing.getInitialPolicy().isPresent()) {
            checkForErrorsInCreateThingWithPolicy(createThing)
                    .map(error ->
                            replyToSender(error, sender))
                    .orElseGet(() ->
                            createThingWithInitialPolicy(createThing, createThing.getInitialPolicy().get(), sender));
        } else if (createThing.getThing().getPolicyId().isPresent()) {
            checkForErrorsInCreateThingWithPolicy(createThing)
                    .map(error ->
                            replyToSender(error, sender))
                    .orElseGet(() ->
                            enforceCreateThingForNonexistentThingWithPolicyId(createThing,
                                    createThing.getThing().getPolicyId().get(), sender));
        } else {
            // nothing to do with policy, simply forward the command
            forwardToThingsShardRegion(createThing, sender);
        }
        return true;
    }

    default boolean createThingWithInitialPolicy(final CreateThing createThing,
            final JsonObject initialPolicy,
            final ActorRef sender) {
        // TODO
        replyToSender(GatewayInternalErrorException.newBuilder().message("not implemented").build(), sender);
        return true;
    }

    static Optional<DittoRuntimeException> checkForErrorsInCreateThingWithPolicy(final CreateThing createThing) {
        return checkAclAbsenceInCreateThing(createThing)
                .map(Optional::of)
                .orElseGet(() -> checkPolicyIdValidityForCreateThing(createThing));
    }

    static Optional<DittoRuntimeException> checkAclAbsenceInCreateThing(final CreateThing createThing) {
        if (createThing.getThing().getAccessControlList().isPresent()) {
            final DittoRuntimeException error = AclNotAllowedException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    static Optional<DittoRuntimeException> checkPolicyIdValidityForCreateThing(final CreateThing createThing) {
        final Thing thing = createThing.getThing();
        final Optional<String> thingIdOpt = thing.getId();
        final Optional<String> policyIdOpt = thing.getPolicyId();
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(thingIdOpt);
        }

        if (!isValid) {
            final DittoRuntimeException error = PolicyIdNotAllowedException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }
}
