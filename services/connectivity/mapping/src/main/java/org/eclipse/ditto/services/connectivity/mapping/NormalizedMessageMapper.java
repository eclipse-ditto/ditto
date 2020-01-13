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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;

/**
 * A message mapper implementation for normalized changes.
 * Create- and modify-events are mapped to nested sparse JSON.
 * All other signals and incoming messages are dropped.
 */
@PayloadMapper(alias = "Normalized")
public final class NormalizedMessageMapper extends AbstractMessageMapper {

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

    @Override
    public String getId() {
        return "normalized";
    }

    @Override
    public void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
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
    public List<ExternalMessage> map(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return isCreatedOrModifiedThingEvent(topicPath)
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
        builder.set(THING_ID, ThingId.of(topicPath.getNamespace(), topicPath.getId()).toString());

        // enrich with data selected by "extraFields", do this first - the actual changed data applied on top of that:
        extraData.ifPresent(builder::setAll);

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

        final JsonObject result = jsonFieldSelector == null
                ? builder.build()
                : builder.build().get(jsonFieldSelector);

        final Map<String, String> headers = getHeaders(adaptable);

        return ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withTopicPath(adaptable.getTopicPath())
                .withText(result.toString())
                .build();
    }

    private Map<String, String> getHeaders(final Adaptable adaptable) {
        return new LinkedHashMap<>(adaptable.getHeaders().orElse(DittoHeaders.empty()));
    }

    private static JsonObject abridgeMessage(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        // add fields of an event protocol message excluding "value" and "status"
        builder.set(JsonifiableAdaptable.JsonFields.TOPIC, adaptable.getTopicPath().getPath());
        builder.set(Payload.JsonFields.PATH, payload.getPath().toString());
        payload.getFields().ifPresent(fields -> builder.set(Payload.JsonFields.FIELDS, fields.toString()));
        builder.set(JsonifiableAdaptable.JsonFields.HEADERS,
                dittoHeadersToJson(adaptable.getHeaders().orElse(DittoHeaders.empty())));
        return builder.build();
    }

    private static JsonObject dittoHeadersToJson(final DittoHeaders dittoHeaders) {
        return dittoHeaders.entrySet()
                .stream()
                .map(entry -> JsonFactory.newField(JsonKey.of(entry.getKey()), JsonFactory.newValue(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static boolean isCreatedOrModifiedThingEvent(final TopicPath topicPath) {
        final Optional<TopicPath.Action> action = topicPath.getAction();
        if (topicPath.getCriterion() == TopicPath.Criterion.EVENTS &&
                topicPath.getGroup() == TopicPath.Group.THINGS &&
                action.isPresent()) {
            final TopicPath.Action act = action.get();
            return act == TopicPath.Action.CREATED || act == TopicPath.Action.MODIFIED;
        }
        return false;
    }

}
