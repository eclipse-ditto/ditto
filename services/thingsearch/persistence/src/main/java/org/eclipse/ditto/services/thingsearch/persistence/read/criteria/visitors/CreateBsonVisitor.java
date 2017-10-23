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
package org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetExistsBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetFilterBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Creates the Bson object used for the PolicyRestrictedSearchAggregation.
 */
public class CreateBsonVisitor implements CriteriaVisitor<Bson> {

    private CreateBsonVisitor() {
        // only internally instantiable
    }

    /**
     * Creates the Bson object used for the PolicyRestrictedSearchAggregation.
     *
     * @param criteria the criteria to create Bson for.
     * @return the Bson object
     */
    public static Bson apply(final Criteria criteria) {
        return criteria.accept(new CreateBsonVisitor());
    }

    @Override
    public Bson visitAnd(final Stream<Bson> conjuncts) {
        return Filters.and(conjuncts.collect(Collectors.toList()));
    }

    @Override
    public Bson visitAny() {
        return new BsonDocument();
    }

    @Override
    public Bson visitExists(final ExistsFieldExpression fieldExpression) {
        return GetExistsBsonVisitor.apply(fieldExpression);
    }

    @Override
    public Bson visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return GetFilterBsonVisitor.apply(fieldExpression, predicate.accept(CreateBsonPredicateVisitor.getInstance()));
    }

    @Override
    public Bson visitNor(final Stream<Bson> negativeDisjoints) {
        return Filters.nor(negativeDisjoints.collect(Collectors.toList()));
    }

    @Override
    public Bson visitOr(final Stream<Bson> disjoints) {
        return Filters.or(disjoints.collect(Collectors.toList()));
    }
}
