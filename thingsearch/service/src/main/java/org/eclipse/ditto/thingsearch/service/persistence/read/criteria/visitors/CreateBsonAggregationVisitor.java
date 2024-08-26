/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.GetFilterBsonVisitor;

/**
 * Creates the Bson object used for querying.
 */
public class CreateBsonAggregationVisitor extends CreateBsonVisitor {

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();


    private CreateBsonAggregationVisitor(@Nullable final List<String> authorizationSubjectIds) {
        super(authorizationSubjectIds);
    }

    /**
     * Creates the Bson object used for querying with no restriction of visibility.
     *
     * @param criteria the criteria to create Bson for.
     * @return the Bson object
     */
    public static Bson sudoApply(final Criteria criteria) {
        return criteria.accept(new CreateBsonAggregationVisitor(null));
    }

    @Override
    public Bson visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        final Function<String, Bson> predicateCreator = predicate.accept(
                CreateBsonAggregationPredicateVisitor.createInstance(
                        PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object())
                )
        );
        return GetFilterBsonVisitor.apply(fieldExpression, predicateCreator, null);
    }


}
