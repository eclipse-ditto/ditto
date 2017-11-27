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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.SET;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.UNSET;

import java.util.Date;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.mapping.ThingDocumentMapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;

/**
 * Factory to create updates on thing level.
 */
final class ThingUpdateFactory {

    private ThingUpdateFactory() {
        throw new AssertionError();
    }

    /**
     * Creates an upudate to delete a whole thing.
     *
     * @return the update Bson
     */
    static Bson createDeleteThingUpdate() {
        return new Document(SET, new Document(FIELD_DELETED, new Date()));
    }

    /**
     * Creates an update to update a whole thing.
     *
     * @param indexLengthRestrictionEnforcer the restriction helper to enforce size restrictions.
     * @param thing the thing to be set
     * @return the update Bson
     */
    static Bson createUpdateThingUpdate(final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer, final Thing thing) {
        return toUpdate(ThingDocumentMapper.toDocument(indexLengthRestrictionEnforcer.enforceRestrictions(thing)));
    }

    private static Document toUpdate(final Document document) {
        return new Document(SET, document).append(UNSET, new Document(FIELD_DELETED, 1));
    }

}
