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


import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionUtil;

/**
 * Constants for Search Persistence.
 */
public final class PersistenceConstants {

    /**
     * The thing collection name.
     */
    public static final String THINGS_COLLECTION_NAME = "thingEntities";

    /**
     * The policies based search collection name.
     */
    public static final String POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME = "policiesBasedSearchIndex";

    /**
     * The collection name for the collection storing state about things sync.
     */
    public static final String THINGS_SYNC_STATE_COLLECTION_NAME = "thingsSearchSyncStateThings";

    /**
     * The collection name for the collection storing state about policies sync.
     */
    public static final String POLICIES_SYNC_STATE_COLLECTION_NAME = "thingsSearchSyncStatePolicies";

    public static final String SLASH = "/";

    /**
     * Field name for revision.
     */
    public static final String FIELD_REVISION = "__rev";

    /**
     * Field name for deleted field.
     */
    public static final String FIELD_DELETED = "__deleted";

    /**
     * Field name for _id.
     */
    public static final String FIELD_ID = FieldExpressionUtil.FIELD_ID;

    /**
     * Field name for policy id.
     */
    public static final String FIELD_POLICY_ID = "_policyId";

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
     * Field name for attributes with path.
     */
    public static final String FIELD_ATTRIBUTES_WITH_PATH = FIELD_ATTRIBUTES + SLASH;

    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES = "features";

    /**
     * Field name for feature properties.
     */
    public static final String FIELD_PROPERTIES = "properties";

    /**
     * Field name for feature properties with starting path.
     */

    public static final String FIELD_PROPERTIES_WITH_STARTING_PATH = SLASH + FIELD_PROPERTIES;

    /**
     * Field name for feature properties with starting and ending path.
     */
    public static final String FIELD_PROPERTIES_WITH_STARTING_ENDING_PATH =
            FIELD_PROPERTIES_WITH_STARTING_PATH +
                    SLASH;

    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES_WITH_PATH = FIELD_FEATURES + SLASH;

    /**
     * Field name for attributes.
     */
    public static final String FIELD_ATTRIBUTE_PREFIX = "attribute";

    /**
     * Field name for attributes.
     */
    public static final String FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH = FIELD_ATTRIBUTE_PREFIX + SLASH;

    /**
     * Field name for feature properties.
     */
    public static final String FIELD_FEATURE_PROPERTIES_PREFIX = "features/properties";

    /**
     * Field name for feature properties.
     */
    public static final String FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH =
            FIELD_FEATURE_PROPERTIES_PREFIX + SLASH;

    /**
     * Field name for internal flat attributes.
     */
    public static final String FIELD_INTERNAL = "__internal";

    /**
     * Field name for ACL.
     */
    public static final String FIELD_ACL = "acl";

    /**
     * Holds the field name for the internal acl field.
     */
    public static final String FIELD_INTERNAL_ACL = FIELD_INTERNAL + "." + FIELD_ACL;

    /**
     * Field name of attribute's value.
     */
    public static final String FIELD_INTERNAL_VALUE = "v";

    /**
     * Path to an attribute's value.
     */
    public static final String FIELD_PATH_ATTRIBUTES_VALUE = FIELD_INTERNAL + "." + FIELD_INTERNAL_VALUE;

    /**
     * Field name for revision.
     */
    public static final String FIELD_REVISION_VARIABLE = "$" + FIELD_REVISION;

    /**
     * Field name for deleted field.
     */
    public static final String FIELD_DELETED_VARIABLE = "$" + FIELD_DELETED;

    /**
     * Holds the variable name for attributes in aggregations.
     */
    public static final String FIELD_ATTRIBUTES_VARIABLE = "$" + FIELD_ATTRIBUTES;

    /**
     * Holds the features variable name used in aggregations.
     */
    public static final String FIELD_FEATURES_VARIABLE = "$" + FIELD_FEATURES;
    /**
     * Field name for features.
     */
    public static final String FIELD_FEATURES_PREFIX = "features/";

    /**
     * Holds the internal variable name used in aggregations.
     */
    public static final String FIELD_INTERNAL_VARIABLE = "$" + FIELD_INTERNAL;

    /**
     * Holds the feature variable name in aggregations.
     */
    public static final String FIELD_INTERNAL_FEATURE_VARIABLE = FIELD_INTERNAL_VARIABLE + ".f";

    /**
     * Holds the key variable name used in aggregations.
     */
    public static final String FIELD_INTERNAL_KEY_VARIABLE = FIELD_INTERNAL_VARIABLE + ".k";

    /**
     * Holds the variable name for the acl used in aggregations.
     */
    public static final String FIELD_INTERNAL_ACL_VARIABLE = FIELD_INTERNAL_VARIABLE + ".acl";

    /**
     * Field name for global reads.
     */
    public static final String FIELD_GLOBAL_READS = "gr";

    /**
     * Field name for global reads in the internal array.
     */
    public static final String FIELD_INTERNAL_GLOBAL_READS = FIELD_INTERNAL + "." + FIELD_GLOBAL_READS;

    /**
     * Path to an attribute's value.
     */
    public static final String FIELD_PATH_VALUE = FIELD_INTERNAL + "." + FIELD_INTERNAL_VALUE;

    /**
     * Field name of attribute's key.
     */
    public static final String FIELD_INTERNAL_KEY = "k";

    /**
     * Field name of feature id.
     */
    public static final String FIELD_INTERNAL_FEATURE_ID = "f";

    /**
     * Path to an attributes/properties key.
     */
    public static final String FIELD_PATH_KEY = FIELD_INTERNAL + "." + FIELD_INTERNAL_KEY;

    /**
     * Path to an feature id key.
     */
    public static final String FIELD_FEATURE_PATH_KEY = FIELD_INTERNAL + "." + FIELD_INTERNAL_FEATURE_ID;

    /**
     * Field name for policy read grants.
     */
    public static final String FIELD_GRANTED = "granted";

    /**
     * Field name for policy read revokes.
     */
    public static final String FIELD_REVOKED = "revoked";

    /**
     * Field name for policy resource.
     */
    public static final String FIELD_RESOURCE = "resource";

    /**
     * Field name for policy grants.
     */
    public static final String FIELD_GRANTS = "grants";

    /**
     * Field name for the granted attribute resource.
     */
    public static final String FIELD_GRANTS_RESOURCE = FIELD_GRANTS + "." + FIELD_RESOURCE;

    /**
     * Field name for users with access to a particular granted resource.
     */
    public static final String FIELD_GRANTS_GRANTED = FIELD_GRANTS + "." + FIELD_GRANTED;

    /**
     * Field name for users with access to a particular revoked resource.
     */
    public static final String FIELD_GRANTS_REVOKED = FIELD_GRANTS + "." + FIELD_REVOKED;

    /**
     * Field name for policy grants.
     */
    public static final String FIELD_GRANTS_VARIABLE = "$" + FIELD_GRANTS;

    /**
     * Variable holding the value to lookup the policies index.
     */
    public static final String POLICY_INDEX_ID = "policyIndexId";

    /**
     * ID variable used in mongo aggregations.
     */
    public static final String ID_VARIABLE = "$" + FIELD_ID;

    /**
     * Namespace variable used in mongo aggregations.
     */
    public static final String NAMESPACE_VARIABLE = "$" + FIELD_NAMESPACE;

    /**
     * Condition used in mongo aggregation.
     */
    public static final String IF_NULL_CONDITION = "$ifNull";

    /**
     * Projection type first used in mongo aggregations.
     */
    public static final String FIRST_PROJECTION = "$first";

    /**
     * Projection type push used in mongo aggregations.
     */
    public static final String PUSH_PROJECTION = "$push";

    /**
     * Grouping type sum used in mongo aggregations.
     */
    public static final String SUM_GROUPING = "$sum";

    /**
     * Field holding the count for things within a mongodb aggregation.
     */
    public static final String COUNT_RESULT_NAME = "thingsCount";
    public static final String SET = "$set";
    public static final String UNSET = "$unset";
    public static final String PUSH = "$push";
    public static final String PULL = "$pull";
    public static final String EACH = "$each";
    public static final String REGEX = "$regex";
    public static final String EXISTS = "$exists";
    public static final String CONCAT = "$concat";
    public static final String DOT = ".";
    public static final String REGEX_FIELD_END = "(/|\\z)";
    public static final String REGEX_FIELD_START = "^";
    public static final String REGEX_START_THING_ID = "^(?i)";

    private PersistenceConstants() {
    }

}
