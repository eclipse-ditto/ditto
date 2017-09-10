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
package org.eclipse.ditto.services.policies.persistence.actors;


import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;

import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * Mock implementation of the Policies Actor for testing.
 */
public final class PoliciesActorMock extends UntypedActor {

    Policy policy;

    /**
     * Creates a new dummy actor which does respond to a {@link ModifyPolicy} message with a {@link
     * ModifyPolicyResponse} for testing purpose.
     *
     * @return the dummy actor.
     */
    public static Props props() {
        return Props.create(PoliciesActorMock.class, PoliciesActorMock::new);
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof ModifyPolicy) {
            final ModifyPolicy modifyPolicy = (ModifyPolicy) message;
            policy = modifyPolicy.getPolicy();
            getSender().tell(ModifyPolicyResponse.created(modifyPolicy.getId(), policy, modifyPolicy.getDittoHeaders()),
                    getSelf());
        } else if (message instanceof SudoRetrievePolicy) {
            final SudoRetrievePolicy sudoRetrievePolicy = (SudoRetrievePolicy) message;
            getSender().tell(
                    SudoRetrievePolicyResponse.of(policy.getId().get(), policy, sudoRetrievePolicy.getDittoHeaders()),
                    getSelf());
        }
    }
}
