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
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;

import java.nio.charset.StandardCharsets;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;

/**
 * The default {@link PostgresPayloadCodec}: the payload already is JSON. A Ditto {@link JsonValue} is rendered, JSON
 * text ({@link CharSequence} or UTF-8 {@code byte[]}) is taken as it is; anything else is rejected, because the
 * per-entity event adapter or snapshot codec must produce a JSONB-bindable payload. On read the stored text is handed
 * back unchanged for that adapter to parse. (Before this codec existed the journal accepted {@code JsonValue} and
 * {@code CharSequence} and the snapshot store additionally {@code byte[]}; the union is deliberate — no Ditto adapter
 * produces {@code byte[]}, and a non-JSON one is still rejected, by PostgreSQL as a class-22 data error.)
 * <p>
 * A raw U+0000 in the text is rejected up front: PostgreSQL {@code jsonb} cannot store it, so the insert could never
 * succeed. Ditto's JSON escaper turns a NUL in a string value into the six-character escape before it reaches here, so
 * ordinary payloads never trip this guard; that escaped form PostgreSQL itself rejects at insert time (SQLSTATE class
 * 22), which the journal maps to a per-write rejection.
 * </p>
 */
@Immutable
public final class JsonTextPayloadCodec implements PostgresPayloadCodec {

    public static final JsonTextPayloadCodec INSTANCE = new JsonTextPayloadCodec();

    private JsonTextPayloadCodec() {
        // singleton
    }

    @Override
    public String encode(final Object payload) {
        final String jsonText;
        if (payload instanceof JsonValue jsonValue) {
            jsonText = jsonValue.toString();
        } else if (payload instanceof CharSequence charSequence) {
            jsonText = charSequence.toString();
        } else if (payload instanceof byte[] bytes) {
            jsonText = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unable to serialize payload of type <"
                    + (payload == null ? "null" : payload.getClass()) + ">; expected a Ditto JsonValue or its JSON "
                    + "text (JsonValue/String/byte[]) — the event adapter or snapshot codec must produce a "
                    + "JSONB-bindable payload.");
        }
        if (jsonText.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Payload for the PostgreSQL backend contains U+0000 (NUL), which "
                    + "PostgreSQL jsonb cannot store. Rejecting the write.");
        }
        return jsonText;
    }

    @Override
    public Object decode(final String storedJson) {
        return storedJson;
    }

}
