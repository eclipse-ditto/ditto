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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Supplier;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;

import org.apache.pekko.serialization.Serialization;

/**
 * Turns a Pekko persistence payload — a journal event or a snapshot — into the JSON text a JSONB column stores, and
 * back. Every journal and snapshot-store plugin block carries one codec, selected by its {@value #CONFIG_KEY} key:
 * <ul>
 *     <li>{@value #JSON_TEXT} (the default): the payload already is JSON — a Ditto {@code JsonValue} or its text,
 *     produced by the per-entity event adapter or the snapshot codec — and is stored as it is; on read the text is
 *     handed back for the adapter to parse. The journal and snapshot store of every entity use it.</li>
 *     <li>{@value #PEKKO_SERIALIZATION}: the payload is a Pekko-serialized message that the JSONB journal cannot
 *     interpret — cluster sharding's remember-entities events and state — and is wrapped in a JSON envelope
 *     ({@code serializerId}, {@code manifest}, {@code bytes}) on write and deserialized again on read. Only the
 *     {@code connection_remember} plugin blocks use it.</li>
 * </ul>
 */
public interface PostgresPayloadCodec {

    /** The plugin-block key that selects the codec; absent means {@value #JSON_TEXT}. */
    String CONFIG_KEY = "payload-codec";

    /** Config value: the payload is JSON text (or a Ditto {@code JsonValue}) and is stored unchanged. */
    String JSON_TEXT = "json-text";

    /** Config value: the payload is Pekko-serialized and stored inside the JSON envelope. */
    String PEKKO_SERIALIZATION = "pekko-serialization";

    /**
     * Encodes a payload into the JSON text to bind to the JSONB column.
     *
     * @param payload the journal event (already unwrapped from {@code Tagged}) or the snapshot.
     * @return the JSON text.
     * @throws IllegalArgumentException if this codec cannot store the payload; Pekko treats that as a rejected write.
     */
    String encode(Object payload);

    /**
     * Decodes the stored JSON text into the payload handed to Pekko: the replayed event or the offered snapshot.
     *
     * @param storedJson the JSONB column rendered as text.
     * @return the payload.
     * @throws IllegalArgumentException if the stored text is not what this codec writes.
     */
    Object decode(String storedJson);

    /**
     * Selects the codec a plugin block configures.
     *
     * @param pluginConfig the journal or snapshot-store plugin's own config block.
     * @param serialization the actor system's Pekko serialization, resolved only for {@value #PEKKO_SERIALIZATION}.
     * @return the codec.
     * @throws DittoConfigError if {@value #CONFIG_KEY} names an unknown codec.
     */
    static PostgresPayloadCodec fromConfig(final Config pluginConfig, final Supplier<Serialization> serialization) {
        checkNotNull(pluginConfig, "pluginConfig");
        checkNotNull(serialization, "serialization");
        final String name = pluginConfig.hasPath(CONFIG_KEY) ? pluginConfig.getString(CONFIG_KEY) : JSON_TEXT;
        return switch (name) {
            case JSON_TEXT -> JsonTextPayloadCodec.INSTANCE;
            case PEKKO_SERIALIZATION -> PekkoSerializationPayloadCodec.of(serialization.get());
            default -> throw new DittoConfigError(MessageFormat.format(
                    "Unknown {0} <{1}> in a PostgreSQL persistence plugin block; expected <{2}> (the default) or "
                            + "<{3}>.", CONFIG_KEY, name, JSON_TEXT, PEKKO_SERIALIZATION));
        };
    }

}
