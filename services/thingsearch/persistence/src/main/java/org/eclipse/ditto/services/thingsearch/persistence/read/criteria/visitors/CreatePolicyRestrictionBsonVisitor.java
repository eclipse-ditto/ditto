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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetPolicyRestrictionBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetUnwoundExistsFilterBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.PolicyRestrictedFieldExpression;

/**
 * Builds the BSON query including "granted" and "revoked" fields based on the passed {@code
 * authorizationSubjectsPredicateFunction} which is invoked after the "policiesBasedSearchIndex" {@code granted} and
 * {@code revoked} fields were "joined" in the aggregation pipeline.
 */
public class CreatePolicyRestrictionBsonVisitor implements CriteriaVisitor<Optional<Bson>> {

    private final Bson grantedBson;
    private final Bson notRevokedBson;

    /**
     * Creates a visitor to create policy-restriction Bson objects.
     *
     * @param authorizationSubjectsPredicate the predicate returning the "subject ids" of the search request.
     */
    private CreatePolicyRestrictionBsonVisitor(final Predicate authorizationSubjectsPredicate) {
        grantedBson = CreateBsonPredicateVisitor.apply(authorizationSubjectsPredicate, PersistenceConstants.FIELD_GRANTS_GRANTED);
        notRevokedBson =
                Filters.not(CreateBsonPredicateVisitor.apply(authorizationSubjectsPredicate, PersistenceConstants.FIELD_GRANTS_REVOKED));
    }

    /**
     * Builds the BSON query including "granted" and "revoked" fields based on the passed {@code
     * authorizationSubjectsPredicateFunction} which is invoked after the "policiesBasedSearchIndex" {@code granted} and
     * {@code revoked} fields were "joined" in the aggregation pipeline.
     *
     * @param criteria the criteria to create policy-restricted bson for.
     * @param authorizationSubjectsPredicate the predicate returning the "subject ids" of the search request.
     * @return the BSON to use in the aggregation pipeline after joining the "policiesBasedSearchIndex" entries.
     */
    public static Optional<Bson> apply(final Criteria criteria, final Predicate authorizationSubjectsPredicate) {
        return criteria.accept(new CreatePolicyRestrictionBsonVisitor(authorizationSubjectsPredicate));
    }

    @Override
    public Optional<Bson> visitAnd(final Stream<Optional<Bson>> conjuncts) {
        return visitAndOrNor(conjuncts);
    }

    @Override
    public Optional<Bson> visitAny() {
        return Optional.of(new BsonDocument());
    }

    @Override
    public Optional<Bson> visitExists(final ExistsFieldExpression fieldExpression) {
        return visitFieldExpression(fieldExpression);
    }

    @Override
    public Optional<Bson> visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return visitFieldExpression(fieldExpression);
    }

    @Override
    public Optional<Bson> visitNor(final Stream<Optional<Bson>> negativeDisjoints) {
        return visitAndOrNor(negativeDisjoints);
    }

    @Override
    public Optional<Bson> visitOr(final Stream<Optional<Bson>> disjoints) {
        return visitAndOrNor(disjoints);
    }

    private Optional<Bson> visitAndOrNor(final Stream<Optional<Bson>> components) {
        final List<Bson> filters = components
                .flatMap(filter -> filter.map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toList());
        if (filters.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(Filters.or(filters));
        }

    }

    private Optional<Bson> visitFieldExpression(final FieldExpression fieldExpression) {
        return onPolicyRestrictedFieldExpression(fieldExpression, Optional.empty(), policyRestrictedFieldExpression -> {
            final Bson existsExpression = GetUnwoundExistsFilterBsonVisitor.apply(policyRestrictedFieldExpression);
            final Bson policyRestrictionBson = GetPolicyRestrictionBsonVisitor.apply(policyRestrictedFieldExpression);
            final Bson filter = Filters.and(existsExpression, policyRestrictionBson, grantedBson, notRevokedBson);
            return Optional.of(filter);
        });
    }

    private <T> T onPolicyRestrictedFieldExpression(final Object input, final T defaultOutput,
            final Function<PolicyRestrictedFieldExpression, T> processor) {
        if (input instanceof PolicyRestrictedFieldExpression) {
            return processor.apply((PolicyRestrictedFieldExpression) input);
        } else {
            return defaultOutput;
        }
    }
}
