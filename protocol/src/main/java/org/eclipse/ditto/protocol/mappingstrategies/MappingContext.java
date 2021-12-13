/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.MessagePath;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Context for mapping an {@link Adaptable} to a {@code Signal}.
 *
 * @since 2.3.0
 */
final class MappingContext {

    private static final JsonKey ATTRIBUTE_PATH_PREFIX = JsonKey.of("attributes");
    private static final JsonKey FEATURE_PATH_PREFIX = JsonKey.of("features");
    private static final int FEATURE_PROPERTY_PATH_LEVEL = 3;
    private static final JsonKey POLICY_ENTRIES_PATH_PREFIX = JsonKey.of("entries");
    private static final int RESOURCES_PATH_LEVEL = 2;
    private static final JsonKey RESOURCES_LEVEL_KEY = JsonKey.of("resources");
    private static final int SUBJECTS_PATH_LEVEL = 2;
    private static final JsonKey SUBJECTS_LEVEL_KEY = JsonKey.of("subjects");
    private static final int SUBJECT_PATH_LEVEL = SUBJECTS_PATH_LEVEL + 1;
    private static final int RESOURCE_PATH_LEVEL = 3;

    private final Adaptable adaptable;

    private MappingContext(final Adaptable adaptable) {
        this.adaptable = adaptable;
    }

    /**
     * Returns an instance of {@code MappingContext}.
     *
     * @param adaptable the adaptable that is the base of the returned {@code MappingContext}.
     * @return the instance.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     */
    public static MappingContext of(final Adaptable adaptable) {
        return new MappingContext(ConditionChecker.checkNotNull(adaptable, "adaptable"));
    }

    Adaptable getAdaptable() {
        return adaptable;
    }

    DittoHeaders getDittoHeaders() {
        return adaptable.getDittoHeaders();
    }

    HttpStatus getHttpStatusOrThrow() {
        final Payload payload = adaptable.getPayload();
        return payload.getHttpStatus()
                .orElseThrow(() -> new IllegalAdaptableException("Payload does not contain a HTTP status.",
                        "Please ensure that the payload of the Adaptable contains an expected HTTP status.",
                        adaptable.getDittoHeaders()));
    }

    ThingId getThingId() {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getEntityName());
    }

    Optional<Thing> getThing() {
        final Optional<Thing> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(ThingsModelFactory.newThing(jsonValue.asObject()));
            } else {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Payload value is not a Thing as JSON object but <{0}>.", jsonValue),
                        "Please ensure that the payload value is a valid Thing JSON object representation.",
                        adaptable.getDittoHeaders()
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private Optional<JsonValue> getPayloadValue() {
        final Payload payload = adaptable.getPayload();
        return payload.getValue();
    }

    Thing getThingOrThrow() {
        return getThing().orElseThrow(() ->
                new IllegalAdaptableException(
                        MessageFormat.format("Payload does not contain a {0} as JSON object" +
                                        " because it has no value at all.",
                                Thing.class.getSimpleName()),
                        adaptable.getDittoHeaders()
                ));
    }

    JsonPointer getAttributePointerOrThrow() {
        final MessagePath messagePath = getMessagePath();
        if (!messagePath.getRoot().filter(ATTRIBUTE_PATH_PREFIX::equals).isPresent()) {
            throw newMessagePathInvalidPrefixException(ATTRIBUTE_PATH_PREFIX.asPointer());
        }
        return messagePath.nextLevel();
    }

    private MessagePath getMessagePath() {
        final Payload payload = adaptable.getPayload();
        return payload.getPath();
    }

    private IllegalAdaptableException newMessagePathInvalidPrefixException(final CharSequence expectedPrefix) {
        return new IllegalAdaptableException(
                MessageFormat.format("Message path of payload does not start with <{0}>.", expectedPrefix),
                MessageFormat.format("Please ensure that the message path of the Adaptable starts with <{0}>.",
                        expectedPrefix),
                adaptable.getDittoHeaders()
        );
    }

    Optional<JsonValue> getAttributeValue() {
        return getPayloadValue();
    }

    JsonValue getAttributeValueOrThrow() {
        return getAttributeValue()
                .orElseThrow(() -> new IllegalAdaptableException("Payload does not contain an attribute value.",
                        "Please ensure that the payload of the Adaptable contains an attribute value.",
                        adaptable.getDittoHeaders()));
    }

    Optional<Attributes> getAttributes() {
        final Optional<Attributes> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(ThingsModelFactory.newAttributes(jsonValue.asObject()));
            } else {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Payload value is not an {0} as JSON object but <{1}>.",
                                Attributes.class.getSimpleName(),
                                jsonValue),
                        adaptable.getDittoHeaders()
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Optional<Features> getFeatures() {
        final Optional<Features> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(ThingsModelFactory.newFeatures(jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(Features.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private IllegalAdaptableException newPayloadValueNotJsonObjectException(final Class<?> targetType,
            final JsonValue jsonValue) {

        return new IllegalAdaptableException(
                MessageFormat.format("Payload value is not a {0} as JSON object but <{1}>.",
                        targetType.getSimpleName(),
                        jsonValue),
                MessageFormat.format("Please ensure that the payload value is a JSON object representation of <{0}>.",
                        targetType),
                adaptable.getDittoHeaders()
        );
    }

    String getFeatureIdOrThrow() {
        final MessagePath messagePath = getMessagePath();
        if (!messagePath.getRoot().filter(FEATURE_PATH_PREFIX::equals).isPresent()) {
            throw newMessagePathInvalidPrefixException(FEATURE_PATH_PREFIX.asPointer());
        } else {
            return messagePath.get(1)
                    .map(JsonKey::toString)
                    .orElseThrow(() -> new IllegalAdaptableException(
                                    "Message path of payload does not contain a feature ID.",
                                    MessageFormat.format("Please ensure that the message path of the payload consists" +
                                                    " of two segments, starting with {0}/ and ending with" +
                                                    " the feature ID.",
                                            FEATURE_PATH_PREFIX),
                                    adaptable.getDittoHeaders()
                            )
                    );
        }
    }

    Optional<Feature> getFeature() {
        final Optional<Feature> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(ThingsModelFactory.newFeatureBuilder(jsonValue.asObject())
                        .useId(getFeatureIdOrThrow())
                        .build());
            } else {
                throw newPayloadValueNotJsonObjectException(Feature.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Optional<ThingDefinition> getThingDefinition() {
        final Optional<ThingDefinition> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isString()) {
                result = Optional.of(ThingsModelFactory.newDefinition(jsonValue.asString()));
            } else {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Payload value is not a {0} as JSON string but <{1}>.",
                                ThingDefinition.class.getSimpleName(),
                                jsonValue),
                        adaptable.getDittoHeaders()
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Optional<FeatureDefinition> getFeatureDefinition() {
        final Optional<FeatureDefinition> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isArray()) {
                result = Optional.of(ThingsModelFactory.newFeatureDefinition(jsonValue.asArray()));
            } else {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Payload value is not a {0} as JSON array but <{1}>.",
                                FeatureDefinition.class.getSimpleName(),
                                jsonValue),
                        adaptable.getDittoHeaders()
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Optional<FeatureProperties> getFeatureProperties() {
        final Optional<FeatureProperties> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(ThingsModelFactory.newFeatureProperties(jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(FeatureProperties.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    JsonPointer getFeaturePropertyPointerOrThrow() {
        return getFeaturePropertyPointerOrThrow(JsonKey.of("properties"));
    }

    @SuppressWarnings("java:S3655")
    private JsonPointer getFeaturePropertyPointerOrThrow(final JsonKey levelTwoKey) {
        final String schema = "\"features/${FEATURE_ID}/properties/${PROPERTY_SUB_PATH_OR_PROPERTY_NAME}\"";
        final Map<Integer, JsonKey> expectedPathSegments = new HashMap<>();
        expectedPathSegments.put(0, FEATURE_PATH_PREFIX);
        expectedPathSegments.put(FEATURE_PROPERTY_PATH_LEVEL - 1, levelTwoKey);
        validateMessagePathSegments(expectedPathSegments, schema);

        final MessagePath messagePath = getMessagePath();
        return messagePath.getSubPointer(FEATURE_PROPERTY_PATH_LEVEL)
                .orElseThrow(() -> new IllegalAdaptableException(
                        MessageFormat.format("Message path of payload does not contain a sub-pointer" +
                                " at level <{0,number}>.", FEATURE_PROPERTY_PATH_LEVEL),
                        MessageFormat.format("Please ensure that the message path complies to schema {0}.", schema),
                        adaptable.getDittoHeaders()));
    }

    private void validateMessagePathSegments(final Map<Integer, JsonKey> expectedSegments, final String schema) {
        final MessagePath messagePath = getMessagePath();
        final List<JsonKey> messagePathAsList =
                StreamSupport.stream(messagePath.spliterator(), false).collect(Collectors.toList());

        expectedSegments.forEach((level, expectedJsonKey) -> {
            @Nullable final JsonKey actualJsonKey = messagePathAsList.get(level);
            if (!Objects.equals(actualJsonKey, expectedJsonKey)) {
                final String message;
                if (0 == level) {
                    message = MessageFormat.format("Message path of payload does not start with <{0}>.",
                            expectedJsonKey.asPointer());
                } else {
                    message = MessageFormat.format(
                            "Message path of payload at level <{1, number}> is not <{0}> but <{2}>.",
                            expectedJsonKey,
                            level,
                            actualJsonKey
                    );
                }
                throw new IllegalAdaptableException(message,
                        MessageFormat.format("Please ensure that the message path complies to schema {0}.", schema),
                        adaptable.getDittoHeaders());
            }
        });
    }

    JsonPointer getFeatureDesiredPropertyPointerOrThrow() {
        return getFeaturePropertyPointerOrThrow(JsonKey.of("desiredProperties"));
    }

    Optional<JsonValue> getFeaturePropertyValue() {
        return getPayloadValue();
    }

    Optional<PolicyId> getPolicyId() {
        final Optional<PolicyId> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isString()) {
                result = Optional.of(PolicyId.of(jsonValue.asString()));
            } else {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Payload value is not a {0} as JSON string but <{1}>.",
                                PolicyId.class.getSimpleName(),
                                jsonValue),
                        adaptable.getDittoHeaders()
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    PolicyId getPolicyIdFromTopicPath() {
        final TopicPath topicPath = adaptable.getTopicPath();
        final String namespace = topicPath.getNamespace();
        final String entityName = topicPath.getEntityName();
        return PolicyId.of(namespace, entityName);
    }

    Optional<Policy> getPolicy() {
        final Optional<Policy> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(PoliciesModelFactory.newPolicy(jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(Policy.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Optional<PolicyEntry> getPolicyEntry() {
        final Optional<PolicyEntry> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(PoliciesModelFactory.newPolicyEntry(getLabelOrThrow(), jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(PolicyEntry.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Label getLabelOrThrow() {
        final MessagePath messagePath = getMessagePath();
        final JsonPointer labelSubPath = messagePath.getRoot()
                .filter(POLICY_ENTRIES_PATH_PREFIX::equals)
                .map(root -> messagePath.nextLevel())
                .orElseThrow(() -> newMessagePathInvalidPrefixException(POLICY_ENTRIES_PATH_PREFIX.asPointer()));

        return labelSubPath.getRoot()
                .map(PoliciesModelFactory::newLabel)
                .orElseThrow(() -> new IllegalAdaptableException("Path does not contain a policy label.",
                        "Please ensure that the path of the Adaptable contains a policy label.",
                        adaptable.getDittoHeaders()));
    }

    ResourceKey getResourceKeyOrThrow() {

        // expected: entries/<entry>/resources/<type:/path1/path2>
        final String schema = "\"entries/${POLICY_LABEL}/resources/${RESOURCE_KEY}\"";
        final Map<Integer, JsonKey> expectedSegments = new HashMap<>();
        expectedSegments.put(0, POLICY_ENTRIES_PATH_PREFIX);
        expectedSegments.put(RESOURCES_PATH_LEVEL, RESOURCES_LEVEL_KEY);
        validateMessagePathSegments(expectedSegments, schema);

        final MessagePath messagePath = getMessagePath();
        return messagePath.getSubPointer(RESOURCE_PATH_LEVEL)
                .map(PoliciesModelFactory::newResourceKey)
                .orElseThrow(() -> new IllegalAdaptableException(
                        MessageFormat.format("Message messagePath of payload does have resource key " +
                                "at level <{0,number}>.", RESOURCE_PATH_LEVEL),
                        "Please ensure that the message path complies to schema " + schema + ".",
                        adaptable.getDittoHeaders()
                ));
    }

    Optional<Resource> getResource() {
        return getPayloadValue().map(jsonValue -> Resource.newInstance(getResourceKeyOrThrow(), jsonValue));
    }

    SubjectId getSubjectIdOrThrow() {

        // expected: entries/<entry>/subjects/<issuer:subject>
        final String schema = "\"entries/${POLICY_LABEL}/subjects/${SUBJECT_ID}\"";
        final Map<Integer, JsonKey> expectedPathSegments = new HashMap<>();
        expectedPathSegments.put(0, JsonKey.of(POLICY_ENTRIES_PATH_PREFIX));
        expectedPathSegments.put(SUBJECTS_PATH_LEVEL, SUBJECTS_LEVEL_KEY);
        validateMessagePathSegments(expectedPathSegments, schema);

        final MessagePath messagePath = getMessagePath();
        return messagePath.get(SUBJECT_PATH_LEVEL)
                .map(PoliciesModelFactory::newSubjectId)
                .orElseThrow(() -> new IllegalAdaptableException(
                        MessageFormat.format("Message path of payload does not contain a subject ID" +
                                " at level <{0,number}>.", SUBJECT_PATH_LEVEL),
                        "Please ensure that message path complies to schema " + schema + ".",
                        adaptable.getDittoHeaders()
                ));
    }

    Optional<Subject> getSubject() {
        final Optional<Subject> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(PoliciesModelFactory.newSubject(getSubjectIdOrThrow(), jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(Subject.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MappingContext that = (MappingContext) o;
        return Objects.equals(adaptable, that.adaptable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adaptable);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "adaptable=" + adaptable +
                "]";
    }

}
