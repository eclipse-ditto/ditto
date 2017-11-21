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

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;

/**
 * Strategy that creates {@link Bson} for {@link AclEntryDeleted} events.
 */
public final class MongoAclEntryDeletedStrategy extends MongoEventToPersistenceStrategy<AclEntryDeleted> {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Bson> thingUpdates(final AclEntryDeleted event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        final AuthorizationSubject authorizationSubject = event.getAuthorizationSubject();
        return Collections.singletonList(AclUpdatesFactory.deleteAclEntry(authorizationSubject.getId()));
    }
}
