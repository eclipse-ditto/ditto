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

import javax.annotation.Nullable;

/**
 * A {@code FROM}/join table (or CTE) reference with an optional alias, e.g. {@code search_things st}, {@code search_flat
 * sf}, or a bare CTE name {@code sel}. Both name and alias are validated identifiers ({@link Identifiers}).
 */
final class TableRef implements SqlNode {

    private final String name;
    @Nullable
    private final String alias;

    TableRef(final String name, @Nullable final String alias) {
        this.name = Identifiers.require(name, "table name");
        this.alias = alias == null ? null : Identifiers.require(alias, "table alias");
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql(name);
        if (alias != null) {
            ctx.sql(" ").sql(alias);
        }
    }

}
