/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation;

import java.io.IOException;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.json.CborFactoryLoader;
import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.WotInternalErrorException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.OutputFormat;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaId;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.output.OutputUnit;

/**
 * Contains tools around the used JsonSchema library and validating Ditto JSON, including mapping to Jackson.
 */
final class JsonSchemaTools {

    private final CborFactory cborFactory;
    private final ObjectMapper jacksonCborMapper;
    private final SchemaValidatorsConfig schemaValidatorsConfig;

    JsonSchemaTools() {
        final var cborFactoryLoader = CborFactoryLoader.getInstance();
        cborFactory = cborFactoryLoader.getCborFactoryOrThrow();
        jacksonCborMapper = new CBORMapper();
        schemaValidatorsConfig = new SchemaValidatorsConfig();
        schemaValidatorsConfig.setPathType(PathType.JSON_POINTER);
    }

    JsonSchema extractFromSingleDataSchema(final SingleDataSchema dataSchema, final DittoHeaders dittoHeaders) {
        final JsonNode jsonNode;
        try {
            final byte[] bytes = cborFactory.toByteArray(dataSchema.toJson());
            jsonNode = jacksonCborMapper.reader().readTree(bytes);
        } catch (final JsonParseException e) {
            throw DittoRuntimeException.asDittoRuntimeException(e, t -> WotInternalErrorException.newBuilder()
                            .message("Error during parsing input JSON")
                            .cause(t)
                            .dittoHeaders(dittoHeaders)
                            .build() )
                    .setDittoHeaders(dittoHeaders);
        } catch (final IOException e) {
            throw WotInternalErrorException.newBuilder()
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        final JsonMetaSchema.Builder metaSchemaBuilder = JsonMetaSchema.builder(SchemaId.V7, JsonMetaSchema.getV7());
        metaSchemaBuilder.keyword(new NonValidationKeyword("@type"));
        metaSchemaBuilder.keyword(new NonValidationKeyword("unit"));
        metaSchemaBuilder.keyword(new NonValidationKeyword("ditto:category"));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7, builder ->
                        builder.metaSchema(metaSchemaBuilder.build())
                )
                .getSchema(jsonNode, schemaValidatorsConfig);
    }

    OutputUnit validateDittoJsonBasedOnDataSchema(final SingleDataSchema dataSchema,
            final JsonValue jsonValue,
            final DittoHeaders dittoHeaders) {
        final JsonSchema jsonSchema = extractFromSingleDataSchema(dataSchema, dittoHeaders);
        return validateDittoJson(jsonSchema, jsonValue, dittoHeaders);
    }

    OutputUnit validateDittoJson(final JsonSchema jsonSchema,
            final JsonValue jsonValue,
            final DittoHeaders dittoHeaders) {
        final JsonNode jsonNode;
        try {
            final byte[] bytes = cborFactory.toByteArray(jsonValue);
            jsonNode = jacksonCborMapper.reader().readTree(bytes);
        } catch (final JsonParseException e) {
            throw DittoRuntimeException.asDittoRuntimeException(e, t -> WotInternalErrorException.newBuilder()
                            .message("Error during parsing input JSON")
                            .cause(t)
                            .dittoHeaders(dittoHeaders)
                            .build() )
                    .setDittoHeaders(dittoHeaders);
        } catch (final IOException e) {
            throw WotInternalErrorException.newBuilder()
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return jsonSchema.validate(jsonNode, OutputFormat.LIST);
    }
}
