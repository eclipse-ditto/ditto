/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.cluster;

import static java.util.Objects.requireNonNull;

import java.io.NotSerializableException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ExtendedActorSystem;
import akka.serialization.SerializerWithStringManifest;

/**
 * Abstract {@link SerializerWithStringManifest} which handles serializing and deserializing {@link Jsonifiable}s
 * {@link WithDittoHeaders}.
 */
public abstract class AbstractJsonifiableWithDittoHeadersSerializer extends SerializerWithStringManifest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJsonifiableWithDittoHeadersSerializer.class);

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final JsonFieldDefinition<JsonObject> JSON_DITTO_HEADERS =
            JsonFactory.newJsonObjectFieldDefinition("dittoHeaders");

    private static final JsonFieldDefinition<JsonValue> JSON_PAYLOAD =
            JsonFactory.newJsonValueFieldDefinition("payload");

    private final int identifier;
    private final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> mappingStrategies;
    private final Function<Object, String> manifestProvider;

    /**
     * Constructs a new {@code AbstractJsonifiableWithDittoHeadersSerializer} object.
     */
    protected AbstractJsonifiableWithDittoHeadersSerializer(final int identifier, final ExtendedActorSystem actorSystem,
            final Function<Object, String> manifestProvider) {

        this.identifier = identifier;

        final MappingStrategy mappingStrategy = MappingStrategy.loadMappingStrategy(actorSystem);

        mappingStrategies = new HashMap<>();
        mappingStrategies.putAll(requireNonNull(mappingStrategy.determineStrategy(), "mapping strategy"));
        this.manifestProvider = requireNonNull(manifestProvider, "manifest provider");
    }

    @Override
    public int identifier() {
        return identifier;
    }

    @Override
    public String manifest(final Object o) {
        return manifestProvider.apply(o);
    }

    @Override
    public byte[] toBinary(final Object object) {
        if (object instanceof Jsonifiable) {
            final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
            final DittoHeaders dittoHeaders = getDittoHeadersOrEmpty(object);
            jsonObjectBuilder.set(JSON_DITTO_HEADERS, dittoHeaders.toJson());

            final JsonValue jsonValue;

            if (object instanceof Jsonifiable.WithPredicate) {
                final JsonSchemaVersion schemaVersion =
                        dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);

                jsonValue = ((Jsonifiable.WithPredicate) object).toJson(schemaVersion, FieldType.regularOrSpecial());
            } else {
                jsonValue = ((Jsonifiable) object).toJson();
            }

            jsonObjectBuilder.set(JSON_PAYLOAD, jsonValue);

            return jsonObjectBuilder.build()
                    .toString()
                    .getBytes(UTF8_CHARSET);
        } else {
            LOG.error("Could not serialize class <{}> as it does not implement <{}>!", object.getClass(),
                    Jsonifiable.WithPredicate.class);
            final String error = new NotSerializableException(object.getClass().getName()).getMessage();
            return error.getBytes(UTF8_CHARSET);
        }
    }

    private static DittoHeaders getDittoHeadersOrEmpty(final Object object) {
        if (object instanceof WithDittoHeaders) {
            @Nullable final DittoHeaders dittoHeaders = ((WithDittoHeaders) object).getDittoHeaders();
            if (null != dittoHeaders) {
                return dittoHeaders;
            }
            LOG.warn("Object <{}> did not contain DittoHeaders although it should! Using empty DittoHeaders instead.");
        }
        return DittoHeaders.empty();
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        final String json = new String(bytes, UTF8_CHARSET);
        try {
            return tryToCreateKnownJsonifiableFrom(manifest, json);
        } catch (final NotSerializableException e) {
            return e;
        }
    }

    private Jsonifiable tryToCreateKnownJsonifiableFrom(final String manifest, final String json)
            throws NotSerializableException {
        try {
            return createJsonifiableFrom(manifest, json);
        } catch (final DittoRuntimeException | JsonRuntimeException e) {
            LOG.error("Got <{}> during fromBinary(byte[],String) deserialization for manifest <{}> and JSON: '{}'",
                    e.getClass().getSimpleName(), manifest, json, e);
            throw new NotSerializableException(manifest);
        }
    }

    private Jsonifiable createJsonifiableFrom(final String manifest, final String json)
            throws NotSerializableException {

        final BiFunction<JsonObject, DittoHeaders, Jsonifiable> mappingFunction = mappingStrategies.get(manifest);
        if (null == mappingFunction) {
            LOG.warn("No strategy found to map manifest <{}> to a Jsonifiable.WithPredicate!", manifest);
            throw new NotSerializableException(manifest);
        }

        final JsonObject jsonObject = JsonFactory.newObject(json);

        final JsonObject payload = getPayload(jsonObject);

        final DittoHeadersBuilder dittoHeadersBuilder = jsonObject.getValue(JSON_DITTO_HEADERS)
                .map(DittoHeaders::newBuilder)
                .orElseGet(DittoHeaders::newBuilder);

        return mappingFunction.apply(payload, dittoHeadersBuilder.build());
    }

    private static JsonObject getPayload(final JsonObject sourceJsonObject) {
        final JsonObject result;

        final Optional<JsonValue> payloadJsonOptional = sourceJsonObject.getValue(JSON_PAYLOAD);
        if (payloadJsonOptional.isPresent()) {
            final JsonValue payloadJson = payloadJsonOptional.get();
            if (!payloadJson.isObject()) {
                final String msgPattern = "Value <{0}> for <{1}> was not of type <{2}>!";
                final String simpleName = JSON_PAYLOAD.getValueType().getSimpleName();
                final String msg = MessageFormat.format(msgPattern, payloadJson, JSON_PAYLOAD.getPointer(), simpleName);
                throw new DittoJsonException(new IllegalArgumentException(msg));
            } else {
                result = payloadJson.asObject();
            }
        } else {
            result = JsonFactory.newObject();
        }

        return result;
    }

}
