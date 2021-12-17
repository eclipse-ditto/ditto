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
package org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.AbstractFieldBsonCreator;
import org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.GetExistsBsonVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.GetFilterBsonVisitor;

import com.mongodb.client.model.Filters;

/**
 * Creates the Bson object used for querying.
 */
public class CreateBsonVisitor implements CriteriaVisitor<Bson> {

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    @Nullable
    private final List<String> authorizationSubjectIds;

    private CreateBsonVisitor(@Nullable final List<String> authorizationSubjectIds) {
        this.authorizationSubjectIds = authorizationSubjectIds;
    }

    /**
     * Creates the Bson object used for querying with no restriction of visibility.
     *
     * @param criteria the criteria to create Bson for.
     * @return the Bson object
     */
    public static Bson sudoApply(final Criteria criteria) {
        // not adding the deleteAt filter here as this would cause a COLLSCAN for our stats-only sudoCount.
        return criteria.accept(new CreateBsonVisitor(null));
    }

    /**
     * Creates the Bson object used for querying.
     *
     * @param criteria the criteria to create Bson for.
     * @param authorizationSubjectIds subject ids with which to restrict visibility, or null to not restrict visibility.
     * @return the Bson object
     */
    public static Bson apply(final Criteria criteria, final List<String> authorizationSubjectIds) {
        checkNotNull(criteria, "criteria");
        checkNotNull(authorizationSubjectIds, "authorizationSubjectIds");
        final Bson baseFilter = criteria.accept(new CreateBsonVisitor(authorizationSubjectIds));
        final Bson globalReadableFilter = AbstractFieldBsonCreator.getGlobalReadBson(authorizationSubjectIds);

        // Put both per-attribute-filter and global-read filter in the query so that:
        // 1. Purely negated queries do not return results invisible to the authorization subjects, and
        // 2. MongoDB may choose to scan the global-read index when the key-value filter does not discriminate enough.
        return Filters.and(baseFilter, globalReadableFilter);
    }

    @Override
    public Bson visitExists(final ExistsFieldExpression fieldExpression) {
        return GetExistsBsonVisitor.apply(fieldExpression, authorizationSubjectIds);
    }

    @Override
    public Bson visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        final Function<String, Bson> predicateCreator = predicate.accept(
                CreateBsonPredicateVisitor.createInstance(
                        PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object())
                )
        );
        return GetFilterBsonVisitor.apply(fieldExpression, predicateCreator, authorizationSubjectIds);
    }

    @Override
    public Bson visitAny() {
        return new BsonDocument();
    }

    @Override
    public Bson visitNor(final List<Bson> negativeDisjoints) {
        return Filters.nor(negativeDisjoints);
    }

    @Override
    public Bson visitOr(final List<Bson> disjoints) {
        return Filters.or(disjoints);
    }

    @Override
    public Bson visitAnd(final List<Bson> conjuncts) {
        return Filters.and(conjuncts);
    }
}
