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
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;

/**
 * TODO javadoc
 */
final class AclEntryDeletedStrategy implements HandleStrategy<AclEntryDeleted> {

    @Override
    public Thing handle(final AclEntryDeleted event, final Thing thing, final long revision) {
        return thing.toBuilder()
                .removePermissionsOf(event.getAuthorizationSubject())
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null))
                .build();
    }

}
