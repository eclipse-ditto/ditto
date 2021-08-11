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
package org.eclipse.ditto.rql.model.predicates;

import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;

/**
 * Interface a predicate parser has to implement.
 */
public interface PredicateParser {

    /**
     * Parse the specified input.
     *
     * @param input the input that should be parsed.
     * @return the AST {@link RootNode} representing the root of the AST.
     * @throws NullPointerException if {@code input} is {@code null}.
     * @throws ParserException      if {@code input} could not be parsed.
     */
    RootNode parse(String input);

}
