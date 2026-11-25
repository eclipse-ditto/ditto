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
 * A (optionally qualified) column reference, e.g. {@code st.thing_id} or {@code val_num}. Both the qualifier (table
 * alias) and the name are validated as bare SQL identifiers — this AST never renders user path text as an identifier
 * (see {@link Identifiers}); a queried path is bound as a {@code $n} value, never as a column name.
 */
final class Column implements SqlExpression {

    @Nullable
    private final String qualifier;
    private final String name;

    Column(@Nullable final String qualifier, final String name) {
        this.qualifier = qualifier == null ? null : Identifiers.require(qualifier, "column qualifier");
        this.name = Identifiers.require(name, "column name");
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        if (qualifier != null) {
            ctx.sql(qualifier).sql(".");
        }
        ctx.sql(name);
    }

}
