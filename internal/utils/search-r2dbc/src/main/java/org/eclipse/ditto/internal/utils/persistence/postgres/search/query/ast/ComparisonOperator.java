/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast;

/**
 * The binary comparison operators of the RQL predicate grammar ({@code eq}/{@code ne}/{@code gt}/{@code ge}/{@code lt}/
 * {@code le}). The SQL token is a fixed part of the AST grammar — never caller data.
 */
public enum ComparisonOperator {

    EQ("="),
    NE("<>"),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">=");

    private final String sql;

    ComparisonOperator(final String sql) {
        this.sql = sql;
    }

    String sql() {
        return sql;
    }

}
