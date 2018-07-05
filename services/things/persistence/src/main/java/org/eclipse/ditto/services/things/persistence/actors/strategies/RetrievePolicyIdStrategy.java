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

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;

/**
 * This strategy handles the {@link RetrievePolicyId} command.
 */
@NotThreadSafe
public final class RetrievePolicyIdStrategy extends AbstractThingCommandStrategy<RetrievePolicyId> {

    /**
     * Constructs a new {@code RetrievePolicyIdStrategy} object.
     */
    public RetrievePolicyIdStrategy() {
        super(RetrievePolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, final RetrievePolicyId command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final Optional<String> optPolicyId = thing.getPolicyId();
        if (optPolicyId.isPresent()) {
            final String policyId = optPolicyId.get();
            return result(RetrievePolicyIdResponse.of(thingId, policyId, command.getDittoHeaders()));
        } else {
            return result(PolicyIdNotAccessibleException.newBuilder(thingId)
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }
}