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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.AclEntryModified;

/**
 * Strategy that creates {@link Bson} for {@link AclEntryModified} events.
 */
public final class MongoAclEntryModifiedStrategy extends MongoEventToPersistenceStrategy<AclEntryModified> {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Bson> thingUpdates(final AclEntryModified event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return AclUpdatesFactory.createUpdateAclEntry(event.getAclEntry());
    }
}
