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
package org.eclipse.ditto.services.thingsearch.persistence;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_GLOBAL_READS;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_VALUE;
import static org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence.filterNotDeleted;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.services.utils.persistence.mongo.indices.IndexFactory;

/**
 * Defines the MongoDB indices used by the things-search persistence.
 */
public final class Indices {

    private Indices() {
        throw new AssertionError();
    }

    /**
     * Indices on {@link PersistenceConstants#THINGS_COLLECTION_NAME}.
     */
    public static final class Things {

        private static final Index KEY_VALUE = IndexFactory.newInstance("nfkv",
                keys(FIELD_NAMESPACE, FIELD_FEATURE_PATH_KEY, FIELD_PATH_KEY, FIELD_PATH_VALUE, FIELD_ID), false)
                .withPartialFilterExpression(filterNotDeleted());

        private static final Index ACL = IndexFactory.newInstance("acl",
                keys(FIELD_INTERNAL_ACL, FIELD_ID), false)
                .withPartialFilterExpression(filterNotDeleted());

        private static final Index GLOBAL_READS = IndexFactory.newInstance("gr",
                keys(FIELD_INTERNAL_GLOBAL_READS), false)
                .withPartialFilterExpression(filterNotDeleted());

        private static final Index DELETED = IndexFactory.newInstance("deleted",
                keys(FIELD_DELETED), false);

        /**
         * Gets all defined indices.
         *
         * @return the indices
         */
        public static List<Index> all() {
            return Collections.unmodifiableList(Arrays.asList(KEY_VALUE, ACL, GLOBAL_READS, DELETED));
        }

        private static List<String> keys(final String... keyNames) {
            return Collections.unmodifiableList(Arrays.asList(keyNames));
        }

        private Things() {
            throw new AssertionError();
        }
    }

}
