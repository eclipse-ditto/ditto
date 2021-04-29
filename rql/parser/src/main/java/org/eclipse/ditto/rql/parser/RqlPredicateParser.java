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
package org.eclipse.ditto.rql.parser;

import org.eclipse.ditto.rql.model.predicates.PredicateParser;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.parser.internal.RqlPredicateParser$;

/**
 * RQL Parser parsing predicates in the RQL "standard" according to https://github.com/persvr/rql.
 */
public class RqlPredicateParser implements PredicateParser {

    private static final RqlPredicateParser INSTANCE = new RqlPredicateParser();
    private static final PredicateParser PARSER = RqlPredicateParser$.MODULE$;

    private RqlPredicateParser() {
        // private
    }

    /**
     * Returns the RqlPredicateParser instance.
     *
     * @return the instance.
     */
    public static RqlPredicateParser getInstance() {
        return INSTANCE;
    }

    @Override
    public RootNode parse(final String input) {
        return PARSER.parse(input);
    }
}
