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
 * A value- or boolean-valued SQL expression (a column, bind, comparison, boolean tree, jsonb operator, function call,
 * {@code EXISTS} probe, …). The distinction from a bare {@link SqlNode} is intent: expressions appear in {@code SELECT}
 * columns, {@code WHERE} predicates, {@code ORDER BY} keys and as operands of other expressions.
 */
public interface SqlExpression extends SqlNode {
}
