/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.contenttype.ContentType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;

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
        return ImmutableAdaptableBuilder.of(existingAdaptable.getTopicPath())
                .withPayload(existingAdaptable.getPayload())
                .withHeaders(existingAdaptable.getDittoHeaders());
    }

    /**
     * Returns a new {@code Adaptable} with the {@code extra} field set in the payload.
     *
     * @param existingAdaptable the existing adaptable.
     * @param extra the extra fields.
     * @return the new adaptable.
     */
    public static Adaptable setExtra(final Adaptable existingAdaptable, final JsonObject extra) {
        return newAdaptableBuilder(existingAdaptable)
                .withPayload(Payload.newBuilder(existingAdaptable.getPayload())
                        .withExtra(extra)
                        .build())
                .build();
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
     * @throws org.eclipse.ditto.protocol.adapter.UnknownTopicPathException if {@code path} is no valid {@code TopicPath}.
     */
    @SuppressWarnings({"squid:S1166"})
    public static TopicPath newTopicPath(final String path) {
        checkNotNull(path, "path");
        final LinkedList<String> parts = new LinkedList<>(Arrays.asList(path.split(TopicPath.PATH_DELIMITER)));

        try {
            final String namespace = parts.pop(); // parts[0]
            final String id = parts.pop(); // parts[1]
            final TopicPath.Group group =
                    TopicPath.Group.forName(parts.pop()) // parts[2]
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());

            final TopicPath.Channel channel;
            switch (group) {
                case POLICIES:
                    channel = TopicPath.Channel.NONE;
                    break;
                case THINGS:
                    channel = TopicPath.Channel.forName(parts.pop()) // parts[3]
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
                    break;
                default:
                    throw UnknownTopicPathException.newBuilder(path).build();
            }

            final TopicPath.Criterion criterion =
                    TopicPath.Criterion.forName(parts.pop())
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());

            switch (criterion) {
                case COMMANDS:
                case EVENTS:
                    // commands and events Path always contain an ID:
                    final TopicPath.Action action =
                            TopicPath.Action.forName(parts.pop())
                                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion, action);
                case SEARCH:
                    final TopicPath.SearchAction searchAction =
                            TopicPath.SearchAction.forName(parts.pop())
                                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion, searchAction);
                case ERRORS:
                    // errors Path does neither contain an "action":
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion);
                case MESSAGES:
                case ACKS:
                case ANNOUNCEMENTS:
                    // messages and announcements should always contain a non-empty subject:
                    // ACK Paths contain a custom acknowledgement label or an empty subject for aggregated ACKs:
                    final String subject = String.join(TopicPath.PATH_DELIMITER, parts);
                    if (subject.isEmpty()) {
                        return ImmutableTopicPath.of(namespace, id, group, channel, criterion);
                    } else {
                        return ImmutableTopicPath.of(namespace, id, group, channel, criterion, subject);
                    }

                default:
                    throw UnknownTopicPathException.newBuilder(path).build();
            }
        } catch (final NoSuchElementException e) {
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
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if {@code thingId} is not in the expected format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final ThingId thingId) {
        return ImmutableTopicPathBuilder.of(thingId).things();
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@code entityId}. The {@code namespace} and {@code id}
     * part of the {@code TopicPath} will pe parsed from the {@code entityId} and set in the builder.
     *
     * @param entityId the id.
     * @return the builder.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if {@code entityIdId} is not in the expected
     * format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final NamespacedEntityId entityId) {
        return ImmutableTopicPathBuilder.of(entityId).things();
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@code policyId}. The {@code namespace} and {@code id}
     * part of the {@code TopicPath} will pe parsed from the {@code policyId} and set in the builder.
     *
     * @param policyId the id.
     * @return the builder.
     * @throws NullPointerException if {@code policyId} is {@code null}.
     * @throws org.eclipse.ditto.policies.model.PolicyIdInvalidException if {@code policyId} is not in the expected
     * format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final PolicyId policyId) {
        return ImmutableTopicPathBuilder.of(policyId).policies();
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
        return newPayloadBuilder(null);
    }

    /**
     * Returns a new {@code PayloadBuilder} for the specified {@code path}.
     *
     * @param path the path.
     * @return the builder.
     */
    public static PayloadBuilder newPayloadBuilder(@Nullable final JsonPointer path) {
        return ImmutablePayload.getBuilder(path);
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
        return DittoHeaders.newBuilder(headers)
                .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                .build();
    }

    /**
     * Returns new {@code Headers} for the specified {@code headers} map with having
     * {@link ContentType#APPLICATION_JSON} set for the content-type header.
     *
     * @param headers the headers map.
     * @return the headers.
     */
    public static DittoHeaders newHeadersWithJsonContentType(final Map<String, String> headers) {
        return DittoHeaders.newBuilder(headers)
                .contentType(ContentType.APPLICATION_JSON)
                .build();
    }

    /**
     * Returns new {@code Headers} for the specified {@code headers} map with Json merge patch content-type.
     *
     * @param headers the headers map.
     * @return the headers.
     */
    public static DittoHeaders newHeadersWithJsonMergePatchContentType(final Map<String, String> headers) {
        return DittoHeaders.newBuilder(headers)
                .contentType(ContentType.APPLICATION_MERGE_PATCH_JSON)
                .build();
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
