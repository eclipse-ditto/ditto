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
package org.eclipse.ditto.services.thingsearch.query.actors;

import java.util.stream.Stream;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidFilterException;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Throws an exception if the criteria contains negation (i. e., is not monotone)
 * but the HTTP API forbids negation.
 */
final class EnsureMonotonicityVisitor implements CriteriaVisitor<Void> {

    private final DittoHeaders dittoHeaders;
    private final boolean norIsForbidden;

    /**
     * Creates a test whether the criteria contains NOT for an incompatible API version in the command headers.
     *
     * @param dittoHeaders The command headers containing the API version to check.
     */
    private EnsureMonotonicityVisitor(final DittoHeaders dittoHeaders) {
        this.dittoHeaders = dittoHeaders;
        norIsForbidden = shouldForbidNorCriteria();
    }

    /**
     * Throws an exception if the criteria contains negation (i. e., is not monotone)
     * but the HTTP API forbids negation.
     *
     * @param criteria The criteria to check for negation.
     * @param dittoHeaders The command headers containing the API version to check.
     * @throws InvalidFilterException if criteria contains negation but dittoHeaders disallow it.
     */
    static void apply(final Criteria criteria, final DittoHeaders dittoHeaders) {
        criteria.accept(new EnsureMonotonicityVisitor(dittoHeaders));
    }

    @Override
    public Void visitAnd(final Stream<Void> conjuncts) {
        // force the stream to evaluate criteria on children
        conjuncts.count();
        return null;
    }

    @Override
    public Void visitAny() {
        return null;
    }

    @Override
    public Void visitExists(final ExistsFieldExpression fieldExpression) {
        return null;
    }

    @Override
    public Void visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return null;
    }

    @Override
    public Void visitNor(final Stream<Void> negativeDisjoints) {
        if (norIsForbidden) {
            final String message = String.format("The filter operation 'not' is not available in API versions >= %d. " +
                    "Please rephrase your search query without using 'not'.", getForbiddenSchemaVersion());
            throw InvalidFilterException.fromMessage(message, dittoHeaders);
        } else {
            // force the stream to evaluate criteria on children
            negativeDisjoints.count();
            return null;
        }
    }

    @Override
    public Void visitOr(final Stream<Void> disjoints) {
        // force the stream to evaluate criteria on children
        disjoints.count();
        return null;
    }

    private boolean shouldForbidNorCriteria() {
        // should forbid NorCriteria only for API >= 2
        return dittoHeaders.getSchemaVersion()
                .map(JsonSchemaVersion::toInt)
                .filter(v -> v >= JsonSchemaVersion.V_2.toInt())
                .isPresent();
    }

    private int getForbiddenSchemaVersion() {
        final JsonSchemaVersion defaultForbiddenVersion = JsonSchemaVersion.V_2;
        return dittoHeaders.getSchemaVersion().orElse(defaultForbiddenVersion).toInt();
    }

}
