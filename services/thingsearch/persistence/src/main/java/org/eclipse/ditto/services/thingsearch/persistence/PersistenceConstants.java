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
package org.eclipse.ditto.services.thingsearch.persistence;


import org.eclipse.ditto.model.query.expression.FieldExpressionUtil;

/**
 * Constants for Search Persistence.
 */
public final class PersistenceConstants {

    /**
     * A dot.
     */
    public static final String DOT = ".";

    /**
     * A slash.
     */
    public static final String SLASH = "/";

    /**
     * The thing collection name.
     */
    public static final String THINGS_COLLECTION_NAME = "searchThings";

    /**
     * The collection name for the collection storing state about things sync.
     */
    public static final String THINGS_SYNC_STATE_COLLECTION_NAME = "searchThingsSyncThings";

    /**
     * The collection name for the collection storing state about policies sync.
     */
    public static final String POLICIES_SYNC_STATE_COLLECTION_NAME = "searchThingsSyncPolicies";

    /**
     * Field name for revision.
     */
    public static final String FIELD_REVISION = "_revision";


    /**
     * Field name for _id.
     */
    public static final String FIELD_ID = FieldExpressionUtil.FIELD_ID;

    /**
     * Field name for policy id.
     */
    public static final String FIELD_POLICY_ID = "policyId";

    /**
     * Field name for policy revision.
     */
    public static final String FIELD_POLICY_REVISION = "__policyRev";

    /**
     * Field name for count.
     */
    public static final String FIELD_COUNT = "count";

    /**
     * Field name for _namespace.
     */
    public static final String FIELD_NAMESPACE = "_namespace";

    /**
     * Field name for attributes.
     */
    public static final String FIELD_ATTRIBUTES = "attributes";

    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES = "features";

    /**
     * Field name for feature properties.
     */
    public static final String FIELD_PROPERTIES = "properties";

    /**
     * Part of the path between feature ID and property key.
     */
    public static final String PROPERTIES = SLASH + FIELD_PROPERTIES + SLASH;

    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES_PATH = SLASH + FIELD_FEATURES + SLASH;

    /**
     * Field name for attributes.
     */
    public static final String FIELD_ATTRIBUTE_PREFIX = "attributes";

    /**
     * Field name for attributes.
     */
    public static final String FIELD_ATTRIBUTES_PATH = SLASH + FIELD_ATTRIBUTE_PREFIX + SLASH;

    /**
     * Field name for subject IDs with partial READ permission and are not revoked at root.
     */
    public static final String FIELD_GLOBAL_READ = "gr";

    /**
     * Field name for hierarchical attributes for sorting.
     */
    public static final String FIELD_SORTING = "s";

    /**
     * Field name for internal flat attributes.
     */
    public static final String FIELD_INTERNAL = "d";

    /**
     * Field name of attribute's value.
     */
    public static final String FIELD_INTERNAL_VALUE = "v";

    /**
     * Path to an attribute's value.
     */
    public static final String FIELD_PATH_VALUE = FIELD_INTERNAL + DOT + FIELD_INTERNAL_VALUE;

    /**
     * Field name of a key of an attribute or feature property.
     */
    public static final String FIELD_INTERNAL_KEY = "k";

    /**
     * Path to an attributes/properties key.
     */
    public static final String FIELD_PATH_KEY = FIELD_INTERNAL + DOT + FIELD_INTERNAL_KEY;

    /**
     * Field name for policy read grants.
     */
    public static final String FIELD_GRANTED = "g";

    /**
     * Full path of the granted field.
     */
    public static final String FIELD_GRANTED_PATH = FIELD_INTERNAL + DOT + FIELD_GRANTED;

    /**
     * Field name for policy read revokes.
     */
    public static final String FIELD_REVOKED = "r";

    /**
     * Mark a document for deletion.
     */
    public static final String FIELD_DELETE_AT = "deleteAt";

    private PersistenceConstants() {
    }

}
