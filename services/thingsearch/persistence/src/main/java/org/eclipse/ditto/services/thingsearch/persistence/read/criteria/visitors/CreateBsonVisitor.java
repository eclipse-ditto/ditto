/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.AbstractFieldBsonCreator;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetExistsBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetFilterBsonVisitor;

/**
 * Creates the Bson object used for querying.
 */
public class CreateBsonVisitor implements CriteriaVisitor<Bson> {

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
        return criteria.accept(new CreateBsonVisitor(null));
    }

    /**
     * Creates the Bson object used for querying.
     *
     * @param criteria the criteria to create Bson for.
     * @param authorizationSubjectIds subject ids with which to restrict visibility, or null to not restrict visibility.
     * @return the Bson object
     */
    public static Bson apply(final Criteria criteria, List<String> authorizationSubjectIds) {
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
        final Function<String, Bson> predicateCreator = predicate.accept(CreateBsonPredicateVisitor.getInstance());
        return GetFilterBsonVisitor.apply(fieldExpression, predicateCreator, authorizationSubjectIds);
    }

    @Override
    public Bson visitAny() {
        return new BsonDocument();
    }

    @Override
    public Bson visitNor(final Stream<Bson> negativeDisjoints) {
        return Filters.nor(negativeDisjoints.collect(Collectors.toList()));
    }

    @Override
    public Bson visitOr(final Stream<Bson> disjoints) {
        return Filters.or(disjoints.collect(Collectors.toList()));
    }

    @Override
    public Bson visitAnd(final Stream<Bson> conjuncts) {
        return Filters.and(conjuncts.collect(Collectors.toList()));
    }
}
