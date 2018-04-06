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
package org.eclipse.ditto.protocoladapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Factory for the Protocol Adapter library. Provides many static helper methods.
 */
public final class ProtocolFactory {


    private ProtocolFactory() {
        throw new AssertionError();
    }


    /**
     * Returns a new {@code AdaptableBuilder} for the specified {@code topicPath}.
     *
     * @param topicPath the topic path.
     * @return the builder.
     */
    public static AdaptableBuilder newAdaptableBuilder(final TopicPath topicPath) {
        return ImmutableAdaptableBuilder.of(topicPath);
    }

    /**
     * Returns a new {@code AdaptableBuilder} for the existing {@code existingAdaptable}.
     *
     * @param existingAdaptable the existingAdaptable to initialize the AdaptableBuilder with.
     * @return the builder.
     */
    public static AdaptableBuilder newAdaptableBuilder(final Adaptable existingAdaptable) {
        return newAdaptableBuilder(existingAdaptable, existingAdaptable.getTopicPath());
    }

    /**
     * Returns a new {@code AdaptableBuilder} for the existing {@code existingAdaptable} and a specific
     * {@code overwriteTopicPath} to overwrite the one in {@code existingAdaptable}.
     *
     * @param existingAdaptable the existingAdaptable to initialize the AdaptableBuilder with.
     * @param overwriteTopicPath the specific {@code TopicPath} to set as overwrite.
     * @return the builder.
     */
    public static AdaptableBuilder newAdaptableBuilder(final Adaptable existingAdaptable,
            final TopicPath overwriteTopicPath) {
        return ImmutableAdaptableBuilder.of(overwriteTopicPath).withPayload(existingAdaptable.getPayload())
                .withHeaders(existingAdaptable.getHeaders().orElse(null));
    }

    /**
     * Returns an empty {@code TopicPath}.
     *
     * @return the topic path.
     */
    public static TopicPath emptyTopicPath() {
        return ImmutableTopicPathBuilder.empty();
    }

    /**
     * Returns a new {@code TopicPath} for the specified {@code path}.
     *
     * @param path the path.
     * @return the builder.
     * @throws NullPointerException if {@code path} is {@code null}.
     * @throws UnknownTopicPathException if {@code path} is no valid {@code TopicPath}.
     */
    @SuppressWarnings({"squid:S1166"})
    public static TopicPath newTopicPath(final String path) {
        final String[] parts = path.split("/");

        try {
            final String namespace = parts[0];
            final String id = parts[1];
            final TopicPath.Group group =
                    TopicPath.Group.forName(parts[2])
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
            final TopicPath.Channel channel =
                    TopicPath.Channel.forName(parts[3])
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
            final TopicPath.Criterion criterion =
                    TopicPath.Criterion.forName(parts[4])
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());

            switch (criterion) {
                case COMMANDS:
                case EVENTS:
                    // commands and events Path always contain an ID:
                    final TopicPath.Action action =
                            TopicPath.Action.forName(parts[5])
                                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion, action);
                case ERRORS:
                    // errors Path does neither contain an "action":
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion);
                case MESSAGES:
                    // messages Path always contain a subject:
                    final String[] subjectParts = Arrays.copyOfRange(parts, 5, parts.length);
                    final String subject = String.join("/", (CharSequence[]) subjectParts);
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion, subject);
                default:
                    throw UnknownTopicPathException.newBuilder(path).build();
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw UnknownTopicPathException.newBuilder(path).build();
        }
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@code thingId}. The {@code namespace} and {@code id}
     * part of the {@code TopicPath} will pe parsed from the {@code thingId} and set in the builder.
     *
     * @param thingId the id.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} is not in the expected format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final String thingId) {
        return ImmutableTopicPathBuilder.of(thingId).things();
    }

    /**
     * Returns a new {@code TopicPathBuilder}. The {@code id} part of the {@code TopicPath} is set to
     * {@link TopicPath#ID_PLACEHOLDER}.
     *
     * @param namespace the namespace.
     * @return the builder.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     */
    public static TopicPathBuilder newTopicPathBuilderFromNamespace(final String namespace) {
        return ImmutableTopicPathBuilder.of(namespace, TopicPath.ID_PLACEHOLDER).things();
    }


    /**
     * Returns a new {@code Payload} from the specified {@code jsonString}.
     *
     * @param jsonString the JSON string.
     * @return the payload.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a JSON object.
     */
    public static Payload newPayload(final String jsonString) {
        return newPayload(JsonFactory.newObject(jsonString));
    }

    /**
     * Returns a new {@code Payload} from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the payload.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} does not have the expected format.
     */
    public static Payload newPayload(final JsonObject jsonObject) {
        return ImmutablePayload.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code PayloadBuilder} without a path.
     *
     * @return the builder.
     */
    public static PayloadBuilder newPayloadBuilder() {
        return ImmutablePayloadBuilder.of();
    }

    /**
     * Returns a new {@code PayloadBuilder} for the specified {@code path}.
     *
     * @param path the path.
     * @return the builder.
     */
    public static PayloadBuilder newPayloadBuilder(final JsonPointer path) {
        return ImmutablePayloadBuilder.of(path);
    }


    /**
     * Returns new empty {@code Headers}.
     *
     * @return the headers.
     */
    public static DittoHeaders emptyHeaders() {
        return DittoHeaders.empty();
    }

    /**
     * Returns new {@code Headers} for the specified {@code headers} map.
     *
     * @param headers the headers map.
     * @return the headers.
     */
    public static DittoHeaders newHeadersWithDittoContentType(final Map<String, String> headers) {
        return DittoHeaders.of(headers).toBuilder().contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE).build();
    }

    /**
     * Returns new {@code Headers} for the specified {@code headers} map.
     *
     * @param headers the headers map.
     * @return the headers.
     */
    public static DittoHeaders newHeaders(final Collection<Map.Entry<String, String>> headers) {
        return DittoHeaders.of(headers.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Returns new {@code Headers} for the specified {@code headersAsJson}.
     *
     * @param headersAsJson the headers as JSON.
     * @return the headers.
     */
    public static DittoHeaders newHeaders(final JsonObject headersAsJson) {
        return DittoHeaders.newBuilder(headersAsJson).build();
    }

    /**
     * Wraps the passed in {@code adaptable} to a {@link JsonifiableAdaptable} which has a JSON representation.
     *
     * @param adaptable the already created {@code Adaptable} to wrap.
     * @return the JsonifiableAdaptable.
     */
    public static JsonifiableAdaptable wrapAsJsonifiableAdaptable(final Adaptable adaptable) {
        return ImmutableJsonifiableAdaptable.of(adaptable);
    }

    /**
     * Converts the passed in {@code adaptableAsJson} to a {@link JsonifiableAdaptable}.
     *
     * @param adaptableAsJson the adaptable as JsonObject.
     * @return the JsonifiableAdaptable.
     */

    public static JsonifiableAdaptable jsonifiableAdaptableFromJson(final JsonObject adaptableAsJson) {
        return ImmutableJsonifiableAdaptable.fromJson(adaptableAsJson);
    }

}
