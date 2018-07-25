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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;

/**
 * This strategy handles the {@link RetrievePolicyId} command.
 */
@Immutable
final class RetrievePolicyIdStrategy extends AbstractCommandStrategy<RetrievePolicyId> {

    /**
     * Constructs a new {@code RetrievePolicyIdStrategy} object.
     */
    RetrievePolicyIdStrategy() {
        super(RetrievePolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrievePolicyId command) {

        return getThingOrThrow(thing).getPolicyId()
                .map(policyId -> RetrievePolicyIdResponse.of(context.getThingId(), policyId, command.getDittoHeaders()))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(PolicyIdNotAccessibleException.newBuilder(context.getThingId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build()));
    }

}
