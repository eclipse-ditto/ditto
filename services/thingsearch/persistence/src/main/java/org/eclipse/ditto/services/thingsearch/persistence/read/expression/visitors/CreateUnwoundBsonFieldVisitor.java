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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READS;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_FEATURE_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_VALUE;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionUtil;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FieldExpressionVisitor;

/**
 * Creates the Bson object to filter out irrelevant fields.
 */
public class CreateUnwoundBsonFieldVisitor implements FieldExpressionVisitor<Optional<Bson>> {

    private static final String DOT = ".";

    private final Predicate predicate;


    /**
     * Create a {@code CreateUnwoundBsonFieldVisitor}.
     *
     * @param predicate Predicate of the {@code FieldExpression} if it has one, or {@code null} otherwise.
     */
    public CreateUnwoundBsonFieldVisitor(final Predicate predicate) {
        this.predicate = predicate;
    }

    private <T> T withOrWithoutPredicate(final Function<Predicate, T> withPredicate,
            final Supplier<T> withoutPredicate) {

        return predicate != null
                ? withPredicate.apply(predicate)
                : withoutPredicate.get();
    }

    @Override
    public Optional<Bson> visitAttribute(final String key) {
        return Optional.of(withOrWithoutPredicate(
                predicate -> {
                    final String attributeKeyWithPrefix = FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key;
                    final Bson keyRestrictionBson = Filters.eq(FIELD_PATH_KEY, attributeKeyWithPrefix);
                    return Filters.and(keyRestrictionBson,
                            CreateBsonPredicateVisitor.apply(predicate, FIELD_PATH_VALUE));
                },
                () -> Filters.regex(FIELD_PATH_KEY,
                        FieldExpressionUtil.wrapExistsRegex(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key))
        ));
    }

    @Override
    public Optional<Bson> visitFeature(final String featureId) {
        final Bson existsFeatureFilter = Filters.eq(FIELD_INTERNAL + DOT + FIELD_INTERNAL_FEATURE_ID, featureId);
        return Optional.of(withOrWithoutPredicate(predicate -> existsFeatureFilter, () -> existsFeatureFilter));
    }

    @Override
    public Optional<Bson> visitFeatureIdProperty(final String featureId, final String property) {
        return Optional.of(withOrWithoutPredicate(
                predicate -> Filters.and(
                        Filters.eq(FIELD_PATH_KEY, FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property),
                        Filters.eq(FIELD_FEATURE_PATH_KEY, featureId),
                        CreateBsonPredicateVisitor.apply(predicate, FIELD_PATH_VALUE)),
                () -> Filters.and(Filters.regex(FIELD_PATH_KEY,
                        FieldExpressionUtil.wrapExistsRegex(
                                FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property)),
                        Filters.eq(FIELD_FEATURE_PATH_KEY, featureId))
        ));
    }

    @Override
    public Optional<Bson> visitFeatureProperty(final String property) {
        return Optional.of(withOrWithoutPredicate(
                predicate -> Filters.and(
                        Filters.eq(FIELD_PATH_KEY, FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property),
                        CreateBsonPredicateVisitor.apply(predicate, FIELD_PATH_VALUE)),
                () -> Filters.regex(FIELD_PATH_KEY,
                        FieldExpressionUtil.wrapExistsRegex(
                                FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + property))
        ));
    }

    @Override
    public Optional<Bson> visitSimple(final String fieldName) {
        // simple expressions (e. g., thingId, policyId) play no role unwound.
        return Optional.empty();
    }

    @Override
    public Optional<Bson> visitAcl() {
        return withOrWithoutPredicate(
                predicate -> Optional.of(CreateBsonPredicateVisitor.apply(predicate, FIELD_INTERNAL + DOT + FIELD_ACL)),
                Optional::empty);
    }

    @Override
    public Optional<Bson> visitGlobalReads() {
        return withOrWithoutPredicate(
                predicate -> Optional.of(
                        CreateBsonPredicateVisitor.apply(predicate, FIELD_INTERNAL + DOT + FIELD_GLOBAL_READS)),
                Optional::empty);
    }
}
