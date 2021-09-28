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
package org.eclipse.ditto.rql.query.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.PredicateParser;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.things.ModelBasedThingsFieldExpressionFactory;

/**
 * The place for query filter manipulations
 */
public final class QueryFilterCriteriaFactory {

    private final CriteriaFactory criteriaFactory;
    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final PredicateParser predicateParser;

    private QueryFilterCriteriaFactory(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory,
            final PredicateParser predicateParser) {

        this.criteriaFactory = criteriaFactory;
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.predicateParser = predicateParser;
    }

    /**
     * Retrieve the unique model-based query filter criteria factory.
     *
     * @param fieldExpressionFactory the ThingsFieldExpressionFactory to use.
     * @param predicateParser the PredicateParser to use for parsing RQL strings.
     * @return the model-based query filter criteria factory.
     */
    public static QueryFilterCriteriaFactory of(final ThingsFieldExpressionFactory fieldExpressionFactory,
            final PredicateParser predicateParser) {
        return new QueryFilterCriteriaFactory(CriteriaFactory.getInstance(), fieldExpressionFactory, predicateParser);
    }

    /**
     * Retrieve the unique model-based query filter criteria factory.
     *
     * @param predicateParser the PredicateParser to use for parsing RQL strings.
     * @return the model-based query filter criteria factory.
     */
    public static QueryFilterCriteriaFactory modelBased(final PredicateParser predicateParser) {
        return of(ModelBasedThingsFieldExpressionFactory.getInstance(), predicateParser);
    }

    /**
     * Retrieve the unique model-based query filter criteria factory.
     *
     * @param predicateParser the PredicateParser to use for parsing RQL strings.
     * @param placeholders the {@link Placeholder}s to accept when parsing the fields of RQL strings.
     * @return the model-based query filter criteria factory.
     * @since 2.2.0
     */
    public static QueryFilterCriteriaFactory modelBased(final PredicateParser predicateParser,
            final Placeholder<?>... placeholders) {
        return of(ModelBasedThingsFieldExpressionFactory.createInstance(placeholders), predicateParser);
    }

    /**
     * Creates a filter criterion based on a filter string which includes only items in the given namespaces
     *
     * @param filter the filter string
     * @param dittoHeaders the corresponding command headers
     * @param namespaces the namespaces
     * @return a filter criterion based on the filter string which includes only items related to the given namespaces
     */
    public Criteria filterCriteriaRestrictedByNamespaces(final String filter, final DittoHeaders dittoHeaders,
            final Set<String> namespaces) {
        final Criteria filterCriteria = filterCriteria(filter, dittoHeaders);
        return restrictByNamespace(namespaces, filterCriteria);
    }

    /**
     * Creates a criterion from the given filter string by parsing it. Headers are passed through for eventual error
     * information.
     *
     * @param filter the filter string
     * @param headers the corresponding command headers
     * @return a criterion built from given filter or null if filter is null.
     * @throws InvalidRqlExpressionException if the filter string cannot be mapped to a valid criterion
     */
    public Criteria filterCriteria(final String filter, final DittoHeaders headers) {
        return null == filter ? criteriaFactory.any() : mapCriteria(filter, headers);
    }

    /**
     * @return the criteria factory.
     */
    public CriteriaFactory toCriteriaFactory() {
        return criteriaFactory;
    }

    private Criteria restrictByNamespace(final Set<String> namespaces, Criteria filterCriteria) {
        return criteriaFactory.and(Arrays.asList(namespaceFilterCriteria(namespaces), filterCriteria));
    }

    private Criteria namespaceFilterCriteria(final Set<String> namespaces) {
        ConditionChecker.checkNotNull(namespaces);
        return criteriaFactory.fieldCriteria(
                fieldExpressionFactory.filterByNamespace(),
                criteriaFactory.in(new ArrayList<>(namespaces)));
    }

    private Criteria mapCriteria(final String filter, final DittoHeaders dittoHeaders) {
        try {
            final ParameterPredicateVisitor visitor =
                    new ParameterPredicateVisitor(criteriaFactory, fieldExpressionFactory);

            final RootNode rootNode = predicateParser.parse(filter);
            visitor.visit(rootNode);

            final Criteria criteria;
            if (visitor.getCriteria().size() > 1) {
                criteria = criteriaFactory.and(visitor.getCriteria());
            } else if (visitor.getCriteria().size() == 1) {
                criteria = visitor.getCriteria().get(0);
            } else {
                criteria = criteriaFactory.any();
            }
            return criteria;
        } catch (final ParserException | IllegalArgumentException e) {
            throw InvalidRqlExpressionException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
