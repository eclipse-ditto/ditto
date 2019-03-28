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
package org.eclipse.ditto.model.rqlparser;

import org.eclipse.ditto.model.rql.predicates.PredicateParser;
import org.eclipse.ditto.model.rql.predicates.ast.RootNode;
import org.eclipse.ditto.model.rqlparser.internal.RqlPredicateParser$;

/**
 * RQL Parser parsing predicates in the RQL "standard" according to https://github.com/persvr/rql.
 */
public class RqlPredicateParser implements PredicateParser {

    private static final PredicateParser PARSER = RqlPredicateParser$.MODULE$;

    @Override
    public RootNode parse(final String input) {
        return PARSER.parse(input);
    }
}
