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

import java.util.Optional;

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
final class RetrievePolicyIdStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<RetrievePolicyId, String> {

    /**
     * Constructs a new {@code RetrievePolicyIdStrategy} object.
     */
    RetrievePolicyIdStrategy() {
        super(RetrievePolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrievePolicyId command) {

        return extractPolicyId(thing)
                .map(policyId -> RetrievePolicyIdResponse.of(context.getThingId(), policyId, command.getDittoHeaders()))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(PolicyIdNotAccessibleException.newBuilder(context.getThingId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build()));
    }

    private Optional<String> extractPolicyId(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getPolicyId();
    }

    @Override
    public Optional<String> determineETagEntity(final RetrievePolicyId command, @Nullable final Thing thing) {
        return extractPolicyId(thing);
    }
}
