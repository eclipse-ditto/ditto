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

import java.util.Objects;

/**
 * A subquery in {@code FROM}: {@code (<select>) <alias>}.
 */
final class DerivedTable implements SqlNode {

    private final Select subquery;
    private final String alias;

    DerivedTable(final Select subquery, final String alias) {
        this.subquery = Objects.requireNonNull(subquery, "subquery");
        this.alias = Identifiers.require(alias, "derived table alias");
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql("(").render(subquery).sql(") ").sql(alias);
    }

}
