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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.json.CborFactoryLoader;
import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.ArraySchema;
import org.eclipse.ditto.wot.model.DataSchemaType;
import org.eclipse.ditto.wot.model.ObjectSchema;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.WotInternalErrorException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonNodePath;
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


    private static final String PROPERTIES = "properties";

    private final CborFactory cborFactory;
    private final ObjectMapper jacksonCborMapper;
    private final SchemaValidatorsConfig schemaValidatorsConfig;
    @Nullable
    private final Cache<JsonSchemaCacheKey, JsonSchema> jsonSchemaCache;

    JsonSchemaTools(@Nullable final Cache<JsonSchemaCacheKey, JsonSchema> jsonSchemaCache) {
        final var cborFactoryLoader = CborFactoryLoader.getInstance();
        cborFactory = cborFactoryLoader.getCborFactoryOrThrow();
        jacksonCborMapper = new CBORMapper();
        schemaValidatorsConfig = SchemaValidatorsConfig.builder()
                .pathType(PathType.JSON_POINTER)
                .build();
        this.jsonSchemaCache = jsonSchemaCache;
    }

    JsonSchema extractFromSingleDataSchema(final SingleDataSchema dataSchema,
            final boolean validateRequiredObjectFields,
            final DittoHeaders dittoHeaders
    ) {
        final JsonNode jsonNode;
        try {
            final JsonObject dataSchemaJson;
            if (!validateRequiredObjectFields) {
                dataSchemaJson = adjustDataSchemaRemovingRequiredObjectFields(dataSchema.toJson());
            } else {
                dataSchemaJson = dataSchema.toJson();
            }
            final byte[] bytes = cborFactory.toByteArray(dataSchemaJson);
            jsonNode = jacksonCborMapper.reader().readTree(bytes);
        } catch (final JsonParseException e) {
            throw DittoRuntimeException.asDittoRuntimeException(e, t -> WotInternalErrorException.newBuilder()
                            .message("Error during parsing input JSON")
                            .cause(t)
                            .dittoHeaders(dittoHeaders)
                            .build())
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

    private static JsonObject adjustDataSchemaRemovingRequiredObjectFields(final JsonObject dataSchemaJson) {
        final Optional<String> type = dataSchemaJson.getValue(SingleDataSchema.DataSchemaJsonFields.TYPE);
        if (type.filter(DataSchemaType.OBJECT.getName()::equals).isPresent()) {
            final Optional<JsonObject> adjustedProperties = dataSchemaJson.getValue(ObjectSchema.JsonFields.PROPERTIES)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(obj -> obj.stream()
                            .map(field -> JsonField.newInstance(field.getKey(),
                                            adjustDataSchemaRemovingRequiredObjectFields(field.getValue().asObject()))
                                    // recurse!
                            ).collect(JsonCollectors.fieldsToObject())
                    );

            JsonObject dataSchemaJsonAdjusted = dataSchemaJson;
            if (adjustedProperties.isPresent()) {
                dataSchemaJsonAdjusted = adjustedProperties.map(
                                adjProps -> dataSchemaJson.set(ObjectSchema.JsonFields.PROPERTIES, adjProps)
                        )
                        .orElse(dataSchemaJson);
            }
            return dataSchemaJsonAdjusted.remove("required");
        } else if (type.filter(DataSchemaType.ARRAY.getName()::equals).isPresent()) {
            final Optional<JsonObject> adjustedItems = dataSchemaJson.getValue(ArraySchema.JsonFields.ITEMS)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(JsonSchemaTools::adjustDataSchemaRemovingRequiredObjectFields); // recurse!

            JsonObject dataSchemaJsonAdjusted = dataSchemaJson;
            if (adjustedItems.isPresent()) {
                dataSchemaJsonAdjusted = adjustedItems.map(
                                adjProps -> dataSchemaJson.set(ArraySchema.JsonFields.ITEMS, adjProps)
                        )
                        .orElse(dataSchemaJson);
            }
            return dataSchemaJsonAdjusted;
        }
        return dataSchemaJson;
    }

    OutputUnit validateDittoJsonBasedOnDataSchema(final SingleDataSchema dataSchema,
            final JsonPointer pointerPath,
            final boolean validateRequiredObjectFields,
            @Nullable final JsonValue jsonValue,
            final DittoHeaders dittoHeaders
    ) {
        final JsonSchemaCacheKey schemaCacheKey = new JsonSchemaCacheKey(dataSchema, validateRequiredObjectFields);
        final JsonSchema jsonSchema = Optional.ofNullable(jsonSchemaCache)
                .flatMap(c -> c.getBlocking(schemaCacheKey))
                .orElseGet(() -> {
                    final JsonSchema extractedSchema =
                            extractFromSingleDataSchema(dataSchema, validateRequiredObjectFields, dittoHeaders);
                    if (jsonSchemaCache != null) {
                        jsonSchemaCache.put(schemaCacheKey, extractedSchema);
                    }
                    return extractedSchema;
                });

        JsonPointer relativePropertyPath = JsonPointer.empty();
        JsonSchema effectiveSchema = jsonSchema;
        JsonValue valueToValidate = jsonValue;
        if (pointerPath.getLevelCount() > 1) {
            final JsonPointer subPointer = pointerPath.getSubPointer(1).orElseThrow();
            relativePropertyPath = subPointer;
            JsonNodePath jsonNodePath = new JsonNodePath(PathType.JSON_POINTER);
            for (int i = 0; i < subPointer.getLevelCount(); i++) {
                // Descend into schema only if it is of type "object" and has the requested property.
                // This is in line with Ditto's JSON pointer usage, which does not support direct array element access.
                // Keys like "0", "1", etc. are treated as object keys if the schema says it's an object.
                final String jsonKey = subPointer.get(i).orElseThrow().toString();
                final JsonNode currentSchemaNode = effectiveSchema.getSchemaNode();
                final boolean isObjectSchema = Optional.ofNullable(currentSchemaNode.get("type"))
                        .map(JsonNode::asText)
                        .filter("object"::equals)
                        .isPresent();

                if (isObjectSchema &&
                        currentSchemaNode.has(PROPERTIES) &&
                        currentSchemaNode.get(PROPERTIES).has(jsonKey)) {
                    jsonNodePath = jsonNodePath.append(PROPERTIES).append(jsonKey);
                    effectiveSchema = effectiveSchema.getSubSchema(jsonNodePath);
                    relativePropertyPath = relativePropertyPath.getSubPointer(1).orElseThrow();
                    valueToValidate = Optional.ofNullable(valueToValidate)
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .flatMap(obj -> obj.getValue(jsonKey))
                            .orElse(valueToValidate);
                }
            }
        }
        return validateDittoJson(effectiveSchema, relativePropertyPath, valueToValidate, dittoHeaders);
    }

    OutputUnit validateDittoJson(final JsonSchema jsonSchema,
            final JsonPointer relativePropertyPath,
            @Nullable final JsonValue jsonValue,
            final DittoHeaders dittoHeaders
    ) {
        if (jsonValue == null) {
            throw WotThingModelPayloadValidationException.newBuilder("No provided JSON value to validate was present")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        final JsonNode jsonNode;
        try {
            final byte[] bytes = cborFactory.toByteArray(jsonValue);
            jsonNode = jacksonCborMapper.reader().readTree(bytes);
        } catch (final JsonParseException e) {
            throw DittoRuntimeException.asDittoRuntimeException(e, t -> WotInternalErrorException.newBuilder()
                            .message("Error during parsing input JSON")
                            .cause(t)
                            .dittoHeaders(dittoHeaders)
                            .build())
                    .setDittoHeaders(dittoHeaders);
        } catch (final IOException e) {
            throw WotInternalErrorException.newBuilder()
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        final OutputUnit validate = jsonSchema.validate(jsonNode, OutputFormat.LIST);
        if (!validate.isValid() && !validate.getDetails().isEmpty()) {
            final List<OutputUnit> validationDetails = new ArrayList<>(validate.getDetails());
            validate.getDetails().forEach(detail -> {
                if (!relativePropertyPath.isEmpty()) {
                    validationDetails.remove(detail);
                    if (detail.getInstanceLocation().startsWith(relativePropertyPath.toString()) ||
                        detail.getEvaluationPath().startsWith(
                                StreamSupport.stream(relativePropertyPath.spliterator(), false)
                                        .collect(Collectors.joining("/properties/", "/properties/", ""))
                        )
                    ) {
                        final String adjustedInstanceLocation =
                                detail.getInstanceLocation().replace(relativePropertyPath.toString(), "");
                        detail.setInstanceLocation(adjustedInstanceLocation);
                        validationDetails.add(detail);
                    }
                }
            });
            validate.setDetails(validationDetails);
        }
        return validate;
    }
}
