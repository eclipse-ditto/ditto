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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;

/**
 * TODO javadoc
 */
final class AclEntryCreatedStrategy implements HandleStrategy<AclEntryCreated> {

    @Override
    public Thing handle(final AclEntryCreated event, final Thing thing, final long revision) {
        return thing.toBuilder()
                .setPermissions(event.getAclEntry())
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null))
                .build();
    }

}
