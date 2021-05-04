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
package org.eclipse.ditto.rql.model.predicates.ast;


/**
 * Base interface for all predicate nodes in an AST. Provides the basic method for the visitor pattern, too.
 */
public interface Node {

    /**
     * PredicateVisitor Pattern. Takes a visitor as parameter and calls the corresponding visit method with itself as
     * parameter.
     *
     * @param predicateVisitor the visitor which should be called.
     */
    void accept(PredicateVisitor predicateVisitor);
}
