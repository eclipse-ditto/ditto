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
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionUtil;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.PolicyRestrictedFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.PolicyRestrictedFieldExpressionVisitor;

/**
 * Gets a Bson which allows filtering on the policy resource path matching the expression.
 */
public class GetPolicyRestrictionBsonVisitor implements PolicyRestrictedFieldExpressionVisitor<Bson> {

    private GetPolicyRestrictionBsonVisitor() {
        // only internally instantiable
    }

    /**
     * Gets a Bson which allows filtering on the policy resource path matching the expression.
     *
     * @param expression the expression matching the policy resource path.
     * @return the Bson.
     */
    public static Bson apply(final PolicyRestrictedFieldExpression expression) {
        return expression.acceptPolicyRestrictedVisitor(new GetPolicyRestrictionBsonVisitor());
    }

    @Override
    public Bson visitAttribute(final String key) {
        return Filters.regex(PersistenceConstants.FIELD_GRANTS_RESOURCE, FieldExpressionUtil.wrapExistsRegex(
                PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key));
    }

    @Override
    public Bson visitFeature(final String featureId) {
        return Filters.eq(
                PersistenceConstants.FIELD_GRANTS_RESOURCE, PersistenceConstants.FIELD_FEATURES_PREFIX + featureId);
    }

    @Override
    public Bson visitFeatureIdProperty(final String featureId, final String property) {
        return Filters.regex(PersistenceConstants.FIELD_GRANTS_RESOURCE, FieldExpressionUtil.wrapExistsRegex(
                PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property));
    }

    @Override
    public Bson visitFeatureProperty(final String property) {
        return Filters.regex(PersistenceConstants.FIELD_GRANTS_RESOURCE,
                FieldExpressionUtil.wrapExistsRegex(
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property));
    }
}
