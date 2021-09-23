/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.rql.query.things;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.rql.model.predicates.ast.ExistsNode;
import org.eclipse.ditto.rql.model.predicates.ast.LogicalNode;
import org.eclipse.ditto.rql.model.predicates.ast.MultiComparisonNode;
import org.eclipse.ditto.rql.model.predicates.ast.Node;
import org.eclipse.ditto.rql.model.predicates.ast.PredicateVisitor;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.model.predicates.ast.SingleComparisonNode;

/**
 * Visitor for extracting fields of an RQL expression.
 *
 * @since 2.1.0
 */
@NotThreadSafe
public final class FieldNamesPredicateVisitor implements PredicateVisitor {

    private final Set<String> fieldNames;

    private FieldNamesPredicateVisitor() {
        fieldNames = new HashSet<>();
    }

    public static FieldNamesPredicateVisitor getNewInstance() {
        return new FieldNamesPredicateVisitor();
    }

    /**
     * Returns all names from all fields visited by this visitor.
     *
     * @return all field names.
     */
    public Set<String> getFieldNames() {
        return Collections.unmodifiableSet(fieldNames);
    }

    @Override
    public void visit(final RootNode node) {
        node.getChildren().forEach(child -> child.accept(this));
    }

    @Override
    public void visit(final LogicalNode node) {
        node.getChildren().forEach(child -> child.accept(this));
    }

    @Override
    public void visit(final SingleComparisonNode node) {
        fieldNames.add(node.getComparisonProperty());
    }

    @Override
    public void visit(final MultiComparisonNode node) {
        fieldNames.add(node.getComparisonProperty());
    }

    @Override
    public void visit(final ExistsNode node) {
        fieldNames.add(node.getProperty());
    }

    @Override
    public void visit(final Node node) {
        // do nothing
    }

}

