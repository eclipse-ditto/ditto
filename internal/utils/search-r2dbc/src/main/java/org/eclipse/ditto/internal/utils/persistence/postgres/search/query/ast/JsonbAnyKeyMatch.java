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
 * The jsonb {@code ?|} "any key matches" test {@code <jsonb> ?| <textArray>} (§3.3): checks whether the left jsonb value
 * (an array of subject strings) contains ANY element of the right {@code text[]} bind (the authorization subjects). The
 * {@code ?|} operator is safe with r2dbc {@code $n} placeholders — no JDBC-style {@code ?} clash, no escaping. The
 * subject array is a {@code $n::text[]} bind.
 */
final class JsonbAnyKeyMatch implements SqlExpression {

    private final SqlExpression jsonb;
    private final SqlExpression keys;

    JsonbAnyKeyMatch(final SqlExpression jsonb, final SqlExpression keys) {
        this.jsonb = jsonb;
        this.keys = keys;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(jsonb).sql(" ?| ").render(keys);
    }

}
