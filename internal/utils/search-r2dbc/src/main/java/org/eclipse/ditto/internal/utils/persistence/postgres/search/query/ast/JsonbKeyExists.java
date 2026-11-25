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
 * The jsonb {@code ?} single-key existence test {@code <jsonb> ? <textKey>} — whether the left jsonb value contains the
 * key/element given by the right {@code text} bind. Safe with r2dbc {@code $n} placeholders (no JDBC {@code ?} clash).
 */
final class JsonbKeyExists implements SqlExpression {

    private final SqlExpression jsonb;
    private final SqlExpression key;

    JsonbKeyExists(final SqlExpression jsonb, final SqlExpression key) {
        this.jsonb = jsonb;
        this.key = key;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(jsonb).sql(" ? ").render(key);
    }

}
