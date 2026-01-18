/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.mapping;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;

import com.typesafe.config.Config;

/**
 * A message mapper implementation for normalized changes.
 * Create-, modify-, merged- and deleted-events are mapped to nested sparse JSON.
 * For complete thing deletions (ThingDeleted), a special `_deleted` field is included with the deletion timestamp.
 * Partial deletions (AttributeDeleted, FeatureDeleted, etc.) can be mapped to `_deletedFields` when enabled.
 * All other signals and incoming messages are dropped.
 */
public final class NormalizedMessageMapper extends AbstractMessageMapper {

    private static final String PAYLOAD_MAPPER_ALIAS = "Normalized";

    /**
     * Config property to project parts from the mapping result.
     */
    public static final String FIELDS = "fields";
    static final String INCLUDE_DELETED_FIELDS = "includeDeletedFields";

    private static final JsonFieldDefinition<String> THING_ID = Thing.JsonFields.ID;
    private static final JsonFieldDefinition<String> MODIFIED = Thing.JsonFields.MODIFIED;
    private static final JsonFieldDefinition<Long> REVISION = Thing.JsonFields.REVISION;
    private static final JsonFieldDefinition<JsonObject> ABRIDGED_ORIGINAL_MESSAGE =
            JsonFactory.newJsonObjectFieldDefinition("_context");
    /**
     * JSON field containing the Thing's deleted timestamp in ISO-8601 format.
     */
    private static final JsonFieldDefinition<String> DELETED = JsonFactory.newStringFieldDefinition("_deleted",
            FieldType.SPECIAL,
            FieldType.HIDDEN,
            JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> DELETED_FIELDS =
            JsonFactory.newJsonObjectFieldDefinition("_deletedFields",
                    FieldType.SPECIAL,
                    FieldType.HIDDEN,
                    JsonSchemaVersion.V_2);

    @Nullable
    private JsonFieldSelector jsonFieldSelector;
    private boolean includeDeletedFields;

    /**
     * Constructs a new instance of NormalizedMessageMapper extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    NormalizedMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
    }

    private NormalizedMessageMapper(final NormalizedMessageMapper copyFromMapper) {
        super(copyFromMapper);
        this.jsonFieldSelector = copyFromMapper.jsonFieldSelector;
        this.includeDeletedFields = copyFromMapper.includeDeletedFields;
    }

    @Override
    public String getAlias() {
        return PAYLOAD_MAPPER_ALIAS;
    }

    @Override
    public boolean isConfigurationMandatory() {
        return false;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return new NormalizedMessageMapper(this);
    }

    @Override
    public void doConfigure(final Connection connection, final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        final Optional<String> fields = configuration.findProperty(FIELDS);
        fields.ifPresent(s ->
                jsonFieldSelector =
                        JsonFactory.newFieldSelector(s, JsonParseOptions.newBuilder().withoutUrlDecoding().build()));
        includeDeletedFields = configuration.findProperty(INCLUDE_DELETED_FIELDS)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        // All incoming messages are dropped.
        return Collections.emptyList();
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }
    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return isThingChangeEvent(topicPath, adaptable.getPayload(), includeDeletedFields)
                ? Collections.singletonList(flattenAsThingChange(adaptable))
                : Collections.emptyList();
    }

    private ExternalMessage flattenAsThingChange(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final Payload payload = adaptable.getPayload();
        final JsonPointer path = JsonPointer.of(payload.getPath());
        final Optional<JsonValue> payloadValue = payload.getValue();
        final Optional<JsonObject> extraData = payload.getExtra();
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        builder.set(THING_ID, ThingId.of(topicPath.getNamespace(), topicPath.getEntityName()).toString());

        if (path.isEmpty() && payloadValue.isPresent()) {
            final JsonValue value = payloadValue.get();
            if (value.isObject()) {
                value.asObject().forEach(builder::set);
            } else {
                // this is impossible; the adaptable should be the protocol message of an event.
                throw new IllegalStateException("Got adaptable with empty path and non-object value: " + adaptable);
            }
        } else {
            payloadValue.ifPresent(jsonValue -> builder.set(path, jsonValue));
        }

        payload.getTimestamp().ifPresent(timestamp -> builder.set(MODIFIED, timestamp.toString()));
        payload.getRevision().ifPresent(revision -> builder.set(REVISION, revision));

        // Add _deleted field only for complete thing deletions (ThingDeleted events)
        if (isThingDeleted(topicPath, payload)) {
            payload.getTimestamp().ifPresent(timestamp ->
                    builder.set(DELETED, timestamp.toString())
            );
        }

        if (includeDeletedFields) {
            final JsonObject deletedFields = extractDeletedFields(topicPath, payload, path, payloadValue);
            if (!deletedFields.isEmpty()) {
                builder.set(DELETED_FIELDS, deletedFields);
            }
        }

        builder.set(ABRIDGED_ORIGINAL_MESSAGE, abridgeMessage(adaptable));

        final var json = builder.build();
        final var jsonWithExtra = extraData.map(extra -> JsonFactory.mergeJsonValues(json, extra))
                .orElse(json)
                .asObject();

        final JsonObject jsonFiltered = jsonFieldSelector == null
                ? jsonWithExtra
                : jsonWithExtra.get(jsonFieldSelector);

        final JsonObject result;
        if (topicPath.isAction(TopicPath.Action.MERGED)) {
            result = filterNullValuesAndEmptyObjects(jsonFiltered);
        } else {
            result = jsonFiltered;
        }

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .contentType(ContentType.APPLICATION_JSON)
                .build();
        return ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withTopicPath(adaptable.getTopicPath())
                .withText(result.toString())
                .build();
    }

    private static JsonObject abridgeMessage(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder(adaptable.getDittoHeaders()).build();
        builder.set(JsonifiableAdaptable.JsonFields.TOPIC, adaptable.getTopicPath().getPath());
        builder.set(Payload.JsonFields.PATH, payload.getPath().toString());
        builder.set(Payload.JsonFields.VALUE, adaptable.getPayload().getValue().orElse(JsonValue.nullLiteral()));
        payload.getFields().ifPresent(fields -> builder.set(Payload.JsonFields.FIELDS, fields.toString()));
        builder.set(JsonifiableAdaptable.JsonFields.HEADERS, dittoHeadersToJson(dittoHeaders));
        return builder.build();
    }

    private static JsonObject dittoHeadersToJson(final DittoHeaders dittoHeaders) {
        return dittoHeaders.entrySet()
                .stream()
                .map(entry -> JsonFactory.newField(JsonKey.of(entry.getKey()), JsonFactory.newValue(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static boolean isThingChangeEvent(final TopicPath topicPath,
            final Payload payload,
            final boolean includeDeletedFields) {
        final var isThingEvent =
                topicPath.isGroup(TopicPath.Group.THINGS) && topicPath.isCriterion(TopicPath.Criterion.EVENTS);

        final var isChange = topicPath.isAction(TopicPath.Action.CREATED)
                || topicPath.isAction(TopicPath.Action.MODIFIED)
                || topicPath.isAction(TopicPath.Action.MERGED)
                || (includeDeletedFields && topicPath.isAction(TopicPath.Action.DELETED))
                || isThingDeleted(topicPath, payload);

        return isThingEvent && isChange;
    }

    private static boolean isThingDeleted(final TopicPath topicPath, final Payload payload) {
        return topicPath.isAction(TopicPath.Action.DELETED) &&
                JsonPointer.of(payload.getPath()).isEmpty();
    }

    private static JsonObject extractDeletedFields(final TopicPath topicPath,
            final Payload payload,
            final JsonPointer path,
            final Optional<JsonValue> payloadValue) {
        if (!payload.getTimestamp().isPresent()) {
            return JsonObject.empty();
        }
        final JsonObjectBuilder deletedFieldsBuilder = JsonFactory.newObjectBuilder();
        final String timestamp = payload.getTimestamp().orElseThrow().toString();

        if (topicPath.isAction(TopicPath.Action.DELETED) && !path.isEmpty()) {
            deletedFieldsBuilder.set(path.toString(), timestamp);
            return deletedFieldsBuilder.build();
        }

        if (topicPath.isAction(TopicPath.Action.MERGED) && payloadValue.isPresent()) {
            final JsonValue value = payloadValue.get();
            if (value.isNull() && !path.isEmpty()) {
                deletedFieldsBuilder.set(path.toString(), timestamp);
            } else if (value.isObject()) {
                extractNullsFromMergePatch(value.asObject(), path, deletedFieldsBuilder, timestamp);
            }
        }

        return deletedFieldsBuilder.build();
    }

    private static void extractNullsFromMergePatch(final JsonObject mergeObject,
            final JsonPointer basePath,
            final JsonObjectBuilder deletedFieldsBuilder,
            final String timestamp) {
        mergeObject.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            if (isRegexDeletionKey(key)) {
                return;
            }
            final JsonPointer currentPath = basePath.isEmpty()
                    ? JsonPointer.empty().addLeaf(key)
                    : basePath.addLeaf(key);
            final JsonValue value = jsonField.getValue();
            if (value.isNull()) {
                deletedFieldsBuilder.set(currentPath.toString(), timestamp);
            } else if (value.isObject()) {
                extractNullsFromMergePatch(value.asObject(), currentPath, deletedFieldsBuilder, timestamp);
            }
        });
    }

    private static boolean isRegexDeletionKey(final JsonKey key) {
        final String keyString = key.toString();
        return keyString.startsWith("{{") && keyString.endsWith("}}") &&
                (keyString.contains("~") || keyString.contains("/"));
    }

    private static JsonObject filterNullValuesAndEmptyObjects(final JsonObject jsonObject) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        jsonObject.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value = jsonField.getValue();
            final JsonValue result;

            if (value.isNull()) {
                return;
            } else if (value.isObject()) {
                result = filterNullValuesAndEmptyObjects(value.asObject());
                if (result.asObject().isEmpty()) {
                    return;
                }
            } else {
                result = value;
            }
            builder.set(key, result);
        });

        return builder.build();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", jsonFieldSelector=" + jsonFieldSelector +
                "]";
    }
}
