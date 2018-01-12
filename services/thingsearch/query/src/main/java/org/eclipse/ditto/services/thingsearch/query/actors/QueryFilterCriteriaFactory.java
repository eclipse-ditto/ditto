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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.thingsearchparser.ParserException;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.rql.RqlPredicateParser;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidFilterException;

/**
 * The place for query filter manipulations
 */
public final class QueryFilterCriteriaFactory {

    private final CriteriaFactory criteriaFactory;
    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final RqlPredicateParser rqlPredicateParser;

    public QueryFilterCriteriaFactory(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory) {
        this.criteriaFactory = criteriaFactory;
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.rqlPredicateParser = new RqlPredicateParser();
    }

    /**
     * Returns the criteria factory with which this query filter criteria factory was created.
     *
     * @return the criteria factory.
     */
    public CriteriaFactory getCriteriaFactory() {
        return criteriaFactory;
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
     * Creates a filter criterion based on a filter string which includes only items related to the given auth
     * subjects
     *
     * @param filter the filter string
     * @param dittoHeaders the corresponding command headers
     * @param authorisationSubjectIds the auth subjects
     * @return a filter criterion based on the filter string which includes only items related to the given auth
     * subjects
     */
    public Criteria filterCriteriaRestrictedByAcl(final String filter, final DittoHeaders dittoHeaders,
            final List<String> authorisationSubjectIds) {
        final Criteria filterCriteria = filterCriteria(filter, dittoHeaders);
        return restrictByAcl(authorisationSubjectIds, filterCriteria);
    }

    /**
     * Creates a filter criterion based on a filter string which includes items related to the given auth
     * subjects and namespaces
     *
     * @param filter the filter string
     * @param dittoHeaders the corresponding command headers
     * @param authorisationSubjectIds the auth subjects
     * @param namespaces the namespaces
     * @return a filter criterion based on the filter string which includes only items related elated to the given auth
     * subjects and namespaces
     */
    public Criteria filterCriteriaRestrictedByAclAndNamespaces(final String filter, final DittoHeaders dittoHeaders,
            final List<String> authorisationSubjectIds, final Set<String> namespaces) {
        final Criteria filterCriteria = filterCriteria(filter, dittoHeaders);
        return restrictByNamespace(namespaces, restrictByAcl(authorisationSubjectIds, filterCriteria));
    }

    /**
     * Creates a criterion from the given filter string by parsing it. Headers are passed through for eventual error
     * information.
     *
     * @param filter the filter string
     * @param headers the corresponding command headers
     * @return a criterion built from given filter or null if filter is null.
     * @throws InvalidFilterException if the filter string cannot be mapped to a valid criterion
     */
    public Criteria filterCriteria(final String filter, final DittoHeaders headers) {
        return null == filter ? criteriaFactory.any() : mapCriteria(filter, headers);
    }

    private Criteria restrictByAcl(final List<String> authorisationSubjectIds, Criteria filterCriteria) {
        return criteriaFactory.and(Arrays.asList(aclFilterCriteria(authorisationSubjectIds), filterCriteria));
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

    private Criteria aclFilterCriteria(final List<String> authorisationSubjectIds) {
        ConditionChecker.checkNotNull(authorisationSubjectIds);
        return criteriaFactory.fieldCriteria(fieldExpressionFactory.filterByAcl(),
                criteriaFactory.in(authorisationSubjectIds));
    }

    private Criteria mapCriteria(final String filter, final DittoHeaders dittoHeaders) {
        try {
            final ParameterPredicateVisitor visitor =
                    new ParameterPredicateVisitor(criteriaFactory, fieldExpressionFactory);

            final RootNode rootNode = rqlPredicateParser.parse(filter);
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
            throw InvalidFilterException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}