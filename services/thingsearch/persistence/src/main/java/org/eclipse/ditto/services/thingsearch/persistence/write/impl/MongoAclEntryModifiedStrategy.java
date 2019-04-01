/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
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
