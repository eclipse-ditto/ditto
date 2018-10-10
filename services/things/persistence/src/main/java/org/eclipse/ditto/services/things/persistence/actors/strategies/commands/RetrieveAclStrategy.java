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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;

/**
 * This strategy handles the {@link RetrieveAcl} command.
 */
@Immutable
final class RetrieveAclStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<RetrieveAcl, AccessControlList> {

    /**
     * Constructs a new {@code RetrieveAclStrategy} object.
     */
    RetrieveAclStrategy() {
        super(RetrieveAcl.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAcl command) {

        final JsonObject aclJson = extractAcl(thing)
                .map(acl -> acl.toJson(command.getImplementedSchemaVersion()))
                .orElseGet(JsonFactory::newObject);

        return ResultFactory.newQueryResult(command, thing,
                RetrieveAclResponse.of(context.getThingId(), aclJson, command.getDittoHeaders()), this);
    }

    private Optional<AccessControlList> extractAcl(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getAccessControlList();
    }


    @Override
    public Optional<AccessControlList> determineETagEntity(final RetrieveAcl command, @Nullable final Thing thing) {
        return extractAcl(thing);
    }
}
