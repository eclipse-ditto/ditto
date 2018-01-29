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
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_VALUE;

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
        private static final Index KEY_VALUE = IndexFactory.newInstance("internal_key_value_33",
                Collections.unmodifiableList(Arrays.asList(FIELD_PATH_KEY, FIELD_PATH_VALUE)), false);

        private static final Index FEATURE_ID = IndexFactory.newInstance("internal_featureId_33",
                Collections.singletonList(FIELD_FEATURE_PATH_KEY), false);

        private static final Index ACL = IndexFactory.newInstance("internal_acl_33",
                Collections.singletonList(FIELD_INTERNAL_ACL), false);

        private static final Index DELETED = IndexFactory.newInstance("internal_deleted_34",
                Collections.singletonList(FIELD_DELETED), false);

        private static final Index GLOBAL_READS = IndexFactory.newInstance("internal_global_reads_34",
                Collections.singletonList("__internal.gr"), false);

        private static final Index POLICY_ID = IndexFactory.newInstance("policy_id_34",
                Collections.singletonList("_policyId"), false);


        /**
         * Gets all defined indices.
         *
         * @return the indices
         */
        public static List<Index> all() {
            return Collections.unmodifiableList(
                    Arrays.asList(KEY_VALUE, FEATURE_ID, ACL, DELETED, GLOBAL_READS, POLICY_ID));
        }

        private Things() {
            throw new AssertionError();
        }
    }


}
