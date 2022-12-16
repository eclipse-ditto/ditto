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
package org.eclipse.ditto.thingsearch.service.persistence;


import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;

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
    public static final String THINGS_COLLECTION_NAME = "search";

    /**
     * The collection name for the background sync collection storing background sync progress.
     */
    public static final String BACKGROUND_SYNC_COLLECTION_NAME = "searchSync";

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
     * Field name for policy id in referenced policy tag.
     */
    public static final String FIELD_REFERENCED_POLICY_ID = "id";

    /**
     * Field name for policy revision.
     */
    public static final String FIELD_POLICY_REVISION = "__policyRev";

    /**
     * Field name for the array of all referenced policies. This field includes the actual thing policy as well as all
     * policies that are imported by the thing policy.
     */
    public static final String FIELD_REFERENCED_POLICIES = "__referencedPolicies";

    /**
     * Field name for count.
     */
    public static final String FIELD_COUNT = "count";

    /**
     * Field name for _namespace.
     */
    public static final String FIELD_NAMESPACE = "_namespace";

    /**
     * Field name for _metadata.
     */
    public static final String FIELD_METADATA = "_metadata";

    /**
     * Field name for attributes.
     */
    public static final String FIELD_ATTRIBUTES = "attributes";

    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES = "features";

    /**
     * Field name for feature ID.
     */
    public static final String FIELD_FEATURE_ID = "id";

    /**
     * Field name for feature properties.
     */
    public static final String FIELD_PROPERTIES = "properties";

    /**
     * Field name for feature definition.
     */
    public static final String FIELD_DEFINITION = "definition";

    /**
     * Part of the path between feature ID and property key.
     */
    public static final String PROPERTIES = SLASH + FIELD_PROPERTIES + SLASH;

    /**
     * Field name for feature desiredProperties.
     */
    public static final String FIELD_DESIRED_PROPERTIES = "desiredProperties";

    /**
     * Part of the path between feature ID and desiredProperty key.
     */
    public static final String DESIRED_PROPERTIES = SLASH + FIELD_DESIRED_PROPERTIES + SLASH;

    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES_PATH = SLASH + FIELD_FEATURES + SLASH;

    /**
     * Prefix for the path to attributes.
     */
    public static final String FIELD_ATTRIBUTES_PATH = SLASH + FIELD_ATTRIBUTES + SLASH;

    /**
     * Prefix for the path to _metadata.
     */
    public static final String FIELD_METADATA_PATH = SLASH + FIELD_METADATA + SLASH;

    /**
     * Field name for subject IDs with partial READ permission and are not revoked at root.
     */
    public static final String FIELD_GLOBAL_READ = "gr";

    /**
     * Field name for hierarchical attributes for sorting.
     */
    public static final String FIELD_THING = "t";

    /**
     * Field name for the "f" array containing features for wildcard-feature queries.
     */
    public static final String FIELD_F_ARRAY = "f";

    /**
     * Field name for policies.
     */
    public static final String FIELD_POLICY = "p";

    /**
     * Field name for policies of features.
     */
    public static final String FIELD_FEATURE_POLICY = FIELD_F_ARRAY + DOT + FIELD_POLICY;

    /**
     * Special character used as prefix for grant (g) and revoke (r) fields in index document to avoid conflict
     * actual fields in a thing. The character is part of the restricted fields (see
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#CONTROL_CHARS}).
     */
    private static final String FIELD_PERMISSION_PREFIX = Character.valueOf((char) 183).toString();

    /**
     * Field name for policy read grants.
     */
    public static final String FIELD_GRANTED = FIELD_PERMISSION_PREFIX + "g";

    /**
     * Field name for policy read revokes.
     */
    public static final String FIELD_REVOKED = FIELD_PERMISSION_PREFIX + "r";

    /**
     * Mark a document for deletion.
     */
    public static final String FIELD_DELETE_AT = "deleteAt";

    /**
     * Field name for the last modified timestamp under FIELD_SORTING.
     */
    public static final String FIELD_MODIFIED = "_modified";

    /**
     * Expression of the full path of the last modified timestamp under FIELD_SORTING.
     */
    public static final String FIELD_PATH_MODIFIED = FIELD_THING + DOT + FIELD_MODIFIED;

    private PersistenceConstants() {
    }

}
