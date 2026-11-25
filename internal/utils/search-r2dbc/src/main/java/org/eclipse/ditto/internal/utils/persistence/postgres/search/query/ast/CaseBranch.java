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
 * One {@code WHEN <condition> THEN <result>} branch of a {@link Case}.
 *
 * @param condition the boolean condition.
 * @param result the value produced when the condition holds.
 */
public record CaseBranch(SqlExpression condition, SqlExpression result) {
}
