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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
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

import akka.actor.ActorSystem;

/**
 * A message mapper implementation for normalized changes.
 * Create-,  modify- and merged-events are mapped to nested sparse JSON.
 * All other signals and incoming messages are dropped.
 */
public final class NormalizedMessageMapper extends AbstractMessageMapper {

    private static final String PAYLOAD_MAPPER_ALIAS = "Normalized";

    /**
     * Config property to project parts from the mapping result.
     */
    public static final String FIELDS = "fields";

    private static final JsonFieldDefinition<String> THING_ID = Thing.JsonFields.ID;
    private static final JsonFieldDefinition<String> MODIFIED = Thing.JsonFields.MODIFIED;
    private static final JsonFieldDefinition<Long> REVISION = Thing.JsonFields.REVISION;
    private static final JsonFieldDefinition<JsonObject> ABRIDGED_ORIGINAL_MESSAGE =
            JsonFactory.newJsonObjectFieldDefinition("_context");

    @Nullable
    private JsonFieldSelector jsonFieldSelector;

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
        return isCreatedModifiedOrMergedThingEvent(topicPath)
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
        // add fields of an event protocol message excluding "value" and "status"
        builder.set(JsonifiableAdaptable.JsonFields.TOPIC, adaptable.getTopicPath().getPath());
        builder.set(Payload.JsonFields.PATH, payload.getPath().toString());
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

    private static boolean isCreatedModifiedOrMergedThingEvent(final TopicPath topicPath) {
        final var isThingEvent =
                topicPath.isGroup(TopicPath.Group.THINGS) && topicPath.isCriterion(TopicPath.Criterion.EVENTS);

        final var isCreatedModifiedOrMerged = topicPath.isAction(TopicPath.Action.CREATED) ||
                topicPath.isAction(TopicPath.Action.MODIFIED) ||
                topicPath.isAction(TopicPath.Action.MERGED);

        return isThingEvent && isCreatedModifiedOrMerged;
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
