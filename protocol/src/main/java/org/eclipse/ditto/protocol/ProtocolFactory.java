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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;

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
     * Parses the string argument as a {@link TopicPath}.
     *
     * @param topicPathString a String containing the {@code TopicPath} representation to be parsed.
     * @return the {@code TopicPath} represented by the argument.
     * @throws NullPointerException if {@code path} is {@code null}.
     * @throws org.eclipse.ditto.protocol.adapter.UnknownTopicPathException if the string does not contain a parsable
     * {@code TopicPath}.
     */
    public static TopicPath newTopicPath(final String topicPathString) {
        return ImmutableTopicPath.parseTopicPath(topicPathString);
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@link ThingId}.
     * The namespace and name part of the {@code TopicPath} will pe parsed from the {@code ThingId} and set in the
     * builder.
     *
     * @param thingId the ID.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if {@code thingId} is not in the expected format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final ThingId thingId) {
        checkNotNull(thingId, "thingId");
        final TopicPathBuilder result = ImmutableTopicPath.newBuilder(thingId.getNamespace(), thingId.getName());
        return result.things();
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@link NamespacedEntityId}.
     * The namespace and name part of the {@code TopicPath} will pe parsed from the entity ID and set in the builder.
     *
     * @param entityId the ID.
     * @return the builder.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if {@code entityId} is not in the expected
     * format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final NamespacedEntityId entityId) {
        checkNotNull(entityId, "entityId");
        final TopicPathBuilder result = ImmutableTopicPath.newBuilder(entityId.getNamespace(), entityId.getName());
        if (entityId.getEntityType().equals(ThingConstants.ENTITY_TYPE)) {
            return result.things();
        } else if (entityId.getEntityType().equals(PolicyConstants.ENTITY_TYPE)) {
            return result.policies();
        }
        return result;
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@link EntityId}.
     * The namespace and name part of the {@code TopicPath} will pe parsed from the entity ID and set in the builder.
     *
     * @param entityId the ID.
     * @return the builder.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if {@code entityId} is not in the expected
     * format.
     * @since 3.2.0
     */
    public static TopicPathBuilder newTopicPathBuilder(final EntityId entityId) {
        checkNotNull(entityId, "entityId");
        final TopicPathBuilder result;
        if (entityId instanceof NamespacedEntityId) {
            final String namespace = ((NamespacedEntityId) entityId).getNamespace();
            final String name = ((NamespacedEntityId) entityId).getName();
            result = ImmutableTopicPath.newBuilder(namespace, name);
        } else {
            result = ProtocolFactory.newTopicPathBuilderFromName(entityId.toString());
        }

        if (entityId.getEntityType().equals(ThingConstants.ENTITY_TYPE)) {
            return result.things();
        } else if (entityId.getEntityType().equals(PolicyConstants.ENTITY_TYPE)) {
            return result.policies();
        } else if (entityId.getEntityType().equals(ConnectivityConstants.ENTITY_TYPE)) {
            return result.connections();
        }
        return result;
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@link PolicyId}.
     * The namespace and name part of the {@code TopicPath} will pe parsed from the {@code PolicyId} and set in the
     * builder.
     *
     * @param policyId the id.
     * @return the builder.
     * @throws NullPointerException if {@code policyId} is {@code null}.
     * @throws org.eclipse.ditto.policies.model.PolicyIdInvalidException if {@code policyId} is not in the expected
     * format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final PolicyId policyId) {
        checkNotNull(policyId, "policyId");
        final TopicPathBuilder result = ImmutableTopicPath.newBuilder(policyId.getNamespace(), policyId.getName());
        return result.policies();
    }

    /**
     * Returns a new {@code TopicPathBuilder}.
     * The name part of the {@code TopicPath} is set to {@link TopicPath#ID_PLACEHOLDER}.
     *
     * @param namespace the namespace to be set to the builder.
     * @return the builder.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     */
    public static TopicPathBuilder newTopicPathBuilderFromNamespace(final String namespace) {
        final TopicPathBuilder result = ImmutableTopicPath.newBuilder(namespace, TopicPath.ID_PLACEHOLDER);
        return result.things();
    }

    /**
     * Returns a new {@code TopicPathBuilder}.
     * The namespace part of the {@code TopicPath} is set to {@link TopicPath#ID_PLACEHOLDER}.
     *
     * @param name the name to be set to the builder.
     * @return the builder.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static TopicPathBuilder newTopicPathBuilderFromName(final String name) {
        return ImmutableTopicPath.newBuilder(TopicPath.ID_PLACEHOLDER, name);
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
        return newPayloadBuilder(JsonPointer.empty());
    }

    /**
     * Returns a new {@code PayloadBuilder} for the specified {@code path}.
     *
     * @param path the path.
     * @return the builder.
     */
    public static PayloadBuilder newPayloadBuilder(final JsonPointer path) {
        return ImmutablePayload.getBuilder(path);
    }

    /**
     * Copy the payload's content into a new {@code PayloadBuilder}.
     *
     * @param payload the payload.
     * @return the builder.
     * @since 2.1.0
     */
    public static PayloadBuilder toPayloadBuilder(final Payload payload) {
        return new ImmutablePayload.ImmutablePayloadBuilder(payload);
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
