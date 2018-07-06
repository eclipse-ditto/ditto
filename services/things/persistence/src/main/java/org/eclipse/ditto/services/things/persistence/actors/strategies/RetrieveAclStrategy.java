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

import static org.eclipse.ditto.services.things.persistence.actors.strategies.ResultFactory.newResult;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;

/**
 * This strategy handles the {@link RetrieveAcl} command.
 */
@NotThreadSafe
final class RetrieveAclStrategy extends AbstractCommandStrategy<RetrieveAcl> {

    /**
     * Constructs a new {@code RetrieveAclStrategy} object.
     */
    RetrieveAclStrategy() {
        super(RetrieveAcl.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveAcl command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
        final JsonObject aclJson = acl.toJson(command.getImplementedSchemaVersion());
        return newResult(RetrieveAclResponse.of(thingId, aclJson, command.getDittoHeaders()));
    }

}
