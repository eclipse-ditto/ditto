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

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;

/**
 * This strategy handles the {@link RetrievePolicyId} command.
 */
@ThreadSafe
public final class RetrievePolicyIdStrategy extends AbstractCommandStrategy<RetrievePolicyId> {

    /**
     * Constructs a new {@code RetrievePolicyIdStrategy} object.
     */
    RetrievePolicyIdStrategy() {
        super(RetrievePolicyId.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrievePolicyId command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThingOrThrow();
        final Optional<String> optPolicyId = thing.getPolicyId();
        if (optPolicyId.isPresent()) {
            final String policyId = optPolicyId.get();
            return newResult(RetrievePolicyIdResponse.of(thingId, policyId, command.getDittoHeaders()));
        } else {
            return newResult(PolicyIdNotAccessibleException.newBuilder(thingId)
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }
}