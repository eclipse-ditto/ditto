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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.EACH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.EXISTS;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.PULL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.PUSH;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;

/**
 * Factory to create updates on the {@link AccessControlList} of a {@link org.eclipse.ditto.model.things.Thing}.
 */
final class AclUpdatesFactory {

    private static final Bson PULL_ACL = new Document(PULL,
            new Document(FIELD_INTERNAL, new Document(FIELD_ACL, new Document(EXISTS, Boolean.TRUE))));

    private AclUpdatesFactory() {
        throw new AssertionError();
    }


    /**
     * Creates all ACL updates for a whole {@code AccessControlList}.
     *
     * @param acl the acl to be set
     * @return the updates as Bson.
     */
    static List<Bson> createUpdateAclEntries(final AccessControlList acl) {
        final List<String> newAclEntries = extractReadEntries(acl);
        final Bson pushBson = createAclPushAndRevisionBson(newAclEntries);

        return Arrays.asList(PULL_ACL, pushBson);
    }

    /**
     * Creates an {@code AclUpdate} as Bson for a single {@code AclEntry}.
     *
     * @param aclEntry the entry to be updated/inserted
     * @return The updates as Bson.
     */
    static List<Bson> createUpdateAclEntry(final AclEntry aclEntry) {
        final Bson pullExistingEntry = createPullExistingAclEntryBson(aclEntry.getAuthorizationSubject().getId());

        if (aclEntry.contains(Permission.READ)) {
            final Bson pushBson = createAclPushAndRevisionBson(
                    Collections.singletonList(aclEntry.getAuthorizationSubject().getId()));
            return Arrays.asList(pullExistingEntry, pushBson);
        } else {
            return Collections.singletonList(pullExistingEntry);
        }
    }

    /**
     * Creates an {@code AclUpdate} to delete the {@code AclEntry} associated with the passed in {@code id}.
     *
     * @param id the id of the acl entry
     * @return a wrapper holding the update info
     */
    static Bson deleteAclEntry(final String id) {
        return createPullExistingAclEntryBson(id);
    }

    private static List<String> extractReadEntries(final AccessControlList acl) {
        return acl.getEntriesSet() //
                .stream() //
                .filter(entry -> entry.contains(Permission.READ)) //
                .map(entry -> entry.getAuthorizationSubject().getId()) //
                .collect(Collectors.toList());
    }

    private static Document createAclPushAndRevisionBson(final List<String> newAclEntries) {
        final List<Document> pushEntries =
                newAclEntries.stream() //
                        .map(id -> new Document().append(FIELD_ACL, id)) //
                        .collect(Collectors.toList());

        return new Document(PUSH, new Document(FIELD_INTERNAL, new Document(EACH, pushEntries)));
    }

    private static Document createPullExistingAclEntryBson(final String entryId) {
        return new Document(PULL, new Document(FIELD_INTERNAL, new Document(FIELD_ACL, entryId)));
    }

}
