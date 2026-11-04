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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import java.util.Objects;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * The single source of truth for turning JSON object keys / pointers into the {@code search_flat} {@code path}/
 * {@code wpath} TEXT columns' segment encoding (RFC 6901), shared by BOTH the write side ({@code ThingFlattener},
 * which escapes each raw JSON key as it walks the tree) and the read side (the Phase-D RQL→SQL translator, which
 * escapes each queried path segment before comparing against those columns).
 * <p>
 * Keeping this in one place is a correctness obligation, not a convenience: if the two sides ever diverged by one
 * character, a query would silently probe a wpath the writer never emitted and return no rows. Task D2 relocated the
 * escaping here from {@code ThingFlattener} (which now delegates) so the translator and the flattener are literally
 * the same code.
 * </p>
 * <p>
 * <b>Escaping vs. auth paths:</b> this encoding applies ONLY to the flat-table {@code path}/{@code wpath} columns,
 * which are single TEXT values with {@code '/'} as the sole segment separator. The JSONB auth trees
 * ({@code policy_auth}/{@code features_auth}) are addressed with {@code #>}/{@code jsonb_extract_path} using
 * <em>raw</em> (un-escaped) key segments bound as {@code text[]}/{@code text} — so callers building auth paths must
 * NOT use this class (see {@code AuthFilterSqlBuilder}). Ditto's own {@link JsonPointer} API side-steps the slash
 * ambiguity via the {@link JsonKey} type and therefore only documents tilde escaping; the flat columns cannot, so
 * both {@code '~'} and {@code '/'} are escaped here.
 * </p>
 *
 * @since 4.0.0
 */
public final class JsonPointerSegments {

    private JsonPointerSegments() {
        throw new AssertionError("no instances");
    }

    /**
     * Escapes a single (unescaped) JSON object key into an RFC-6901 path segment: {@code '~'} to {@code "~0"}
     * (checked first) and {@code '/'} to {@code "~1"} (checked second), in one left-to-right pass so a slash's
     * escape sequence is never subsequently re-escaped.
     *
     * @param rawKey the raw (unescaped) JSON object key.
     * @return the RFC-6901-escaped path segment.
     */
    public static String escape(final String rawKey) {
        Objects.requireNonNull(rawKey, "rawKey");
        StringBuilder escaped = null;
        for (int i = 0; i < rawKey.length(); i++) {
            final char c = rawKey.charAt(i);
            if (c == '~' || c == '/') {
                if (escaped == null) {
                    escaped = new StringBuilder(rawKey.length() + 4).append(rawKey, 0, i);
                }
                escaped.append(c == '~' ? "~0" : "~1");
            } else if (escaped != null) {
                escaped.append(c);
            }
        }
        return escaped == null ? rawKey : escaped.toString();
    }

    /**
     * Renders a {@link JsonPointer} into the flat-table {@code wpath}/{@code path} form: a leading {@code '/'} then
     * every segment {@link #escape(String) escaped} and joined by {@code '/'}. The pointer's {@link JsonKey}s are
     * already RFC-6901-<em>decoded</em> (that is how {@link JsonPointer#of(CharSequence)} parses {@code ~0}/{@code ~1}),
     * so re-escaping each key here reproduces byte-for-byte the string {@code ThingFlattener} emits for the same raw
     * JSON key — the escaping-parity contract.
     *
     * @param pointer the (non-empty) query path pointer.
     * @return the {@code wpath}/{@code path} string, e.g. {@code "/attributes/temp"}.
     */
    public static String toWpath(final JsonPointer pointer) {
        Objects.requireNonNull(pointer, "pointer");
        final StringBuilder sb = new StringBuilder();
        for (final JsonKey key : pointer) {
            sb.append('/').append(escape(key.toString()));
        }
        return sb.toString();
    }
}
