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
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.bson.Document;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.IsInternalViewVisitor;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Tests whether all resources requried to evaluate a search criteria are present.
 */
public class ContainsAllRequiredResourcesVisitor implements CriteriaVisitor<Predicate<Document>> {

    private static ContainsAllRequiredResourcesVisitor instance;

    private ContainsAllRequiredResourcesVisitor() {
        // only internally instantiable
    }

    /**
     * Gets the singleton instance of this {@link ContainsAllRequiredResourcesVisitor}.
     *
     * @return the singleton instance.
     */
    public static ContainsAllRequiredResourcesVisitor getInstance() {
        if (null == instance) {
            instance = new ContainsAllRequiredResourcesVisitor();
        }
        return instance;
    }

    /**
     * Tests whether a thing in the search result has all required resources.
     *
     * @param criteria The search criteria.
     * @param thingResult The thing in the search result combined from all visible fields.
     * @return Whether all fields required to evaluate the criteria are present.
     */
    public static boolean apply(final Criteria criteria, final Document thingResult) {
        return criteria.accept(getInstance()).test(thingResult);
    }

    @Override
    public Predicate<Document> visitAnd(final Stream<Predicate<Document>> conjuncts) {
        return thingResult -> conjuncts.allMatch(child -> child.test(thingResult));
    }

    @Override
    public Predicate<Document> visitAny() {
        return thingResult -> true;
    }

    @Override
    public Predicate<Document> visitExists(final ExistsFieldExpression fieldExpression) {
        return thingResult -> {
            final Predicate<Document> predicate = fieldExpression.accept(new IsInternalViewVisitor());
            final List<?> internalList = thingResult.get(PersistenceConstants.FIELD_INTERNAL, List.class);
            return internalList.stream()
                    .anyMatch(element -> element instanceof Document && predicate.test((Document) element));
        };
    }

    @Override
    public Predicate<Document> visitField(final FilterFieldExpression fieldExpression,
            final org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate predicate) {
        return thingResult -> {
            final java.util.function.Predicate<Document> javaPredicate =
                    fieldExpression.accept(new IsInternalViewVisitor());
            final List<?> internalList = thingResult.get(PersistenceConstants.FIELD_INTERNAL, List.class);
            return internalList.stream()
                    .anyMatch(element -> element instanceof Document && javaPredicate.test((Document) element));
        };
    }

    @Override
    public Predicate<Document> visitNor(final Stream<Predicate<Document>> negativeDisjoints) {
        return thingResult -> negativeDisjoints.anyMatch(child -> child.test(thingResult));
    }

    @Override
    public Predicate<Document> visitOr(final Stream<Predicate<Document>> disjoints) {
        return thingResult -> disjoints.anyMatch(child -> child.test(thingResult));
    }
}
