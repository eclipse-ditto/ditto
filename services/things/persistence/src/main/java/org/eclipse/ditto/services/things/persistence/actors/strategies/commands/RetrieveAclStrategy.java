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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;

/**
 * This strategy handles the {@link RetrieveAcl} command.
 */
@Immutable
final class RetrieveAclStrategy extends AbstractCommandStrategy<RetrieveAcl> {

    /**
     * Constructs a new {@code RetrieveAclStrategy} object.
     */
    RetrieveAclStrategy() {
        super(RetrieveAcl.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAcl command) {

        final JsonObject aclJson = getThingOrThrow(thing).getAccessControlList()
                .map(acl -> acl.toJson(command.getImplementedSchemaVersion()))
                .orElseGet(JsonFactory::newObject);

        return ResultFactory.newResult(
                RetrieveAclResponse.of(context.getThingId(), aclJson, command.getDittoHeaders()));
    }

}
