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
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
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
                .orElseThrow(() -> IllegalAdaptableException.newInstance("Payload does not contain a HTTP status.",
                        "Please ensure that the payload of the Adaptable contains an expected HTTP status.",
                        adaptable));
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
                throw IllegalAdaptableException.newInstance(
                        MessageFormat.format("Payload value is not a Thing as JSON object but <{0}>.", jsonValue),
                        "Please ensure that the payload value is a valid Thing JSON object representation.",
                        adaptable
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
        return getThing().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a Thing as JSON object because it has no value at all.",
                "Please ensure that the payload contains a valid Thing JSON object as value.",
                adaptable
        ));
    }

    JsonObject getPayloadValueAsJsonObjectOrThrow() {
        final Optional<JsonValue> payloadValue = getPayloadValue();
        if (payloadValue.isPresent()) {
            final JsonValue jsonValue = payloadValue.get();
            if (jsonValue.isObject()) {
                return jsonValue.asObject();
            } else {
                throw IllegalAdaptableException.newInstance(
                        MessageFormat.format("Payload value is not a JSON object but <{0}>.", jsonValue),
                        "Please ensure that the payload value is a valid JSON object.",
                        adaptable
                );
            }
        } else {
            throw IllegalAdaptableException.newInstance(
                    "Payload does not contain a JSON object value because it has no value at all.",
                    "Please ensure that the payload value contains a valid JSON object as value.",
                    adaptable
            );
        }
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
        return IllegalAdaptableException.newInstance(
                MessageFormat.format("Message path of payload does not start with <{0}>.", expectedPrefix),
                MessageFormat.format("Please ensure that the message path of the Adaptable starts with <{0}>.",
                        expectedPrefix),
                adaptable
        );
    }

    Optional<JsonValue> getAttributeValue() {
        return getPayloadValue();
    }

    JsonValue getAttributeValueOrThrow() {
        return getAttributeValue()
                .orElseThrow(() -> IllegalAdaptableException.newInstance("Payload does not contain an attribute value.",
                        "Please ensure that the payload of the Adaptable contains an attribute value.",
                        adaptable));
    }

    Optional<Attributes> getAttributes() {
        final Optional<Attributes> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(ThingsModelFactory.newAttributes(jsonValue.asObject()));
            } else {
                throw IllegalAdaptableException.newInstance(
                        MessageFormat.format("Payload value is not an {0} as JSON object but <{1}>.",
                                Attributes.class.getSimpleName(),
                                jsonValue),
                        adaptable
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Attributes getAttributesOrThrow() {
        return getAttributes().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain an Attributes as JSON object because it has no value at all.",
                "Please ensure that the payload contains a valid Attributes JSON object as value.",
                adaptable
        ));
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

        return IllegalAdaptableException.newInstance(
                MessageFormat.format("Payload value is not a {0} as JSON object but <{1}>.",
                        targetType.getSimpleName(),
                        jsonValue),
                MessageFormat.format("Please ensure that the payload value is a JSON object representation of <{0}>.",
                        targetType),
                adaptable
        );
    }

    Features getFeaturesOrThrow() {
        return getFeatures().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a Features as JSON string object it has no value at all.",
                "Please ensure that the payload contains a valid Features JSON object as value.",
                adaptable
        ));
    }

    String getFeatureIdOrThrow() {
        final MessagePath messagePath = getMessagePath();
        if (!messagePath.getRoot().filter(FEATURE_PATH_PREFIX::equals).isPresent()) {
            throw newMessagePathInvalidPrefixException(FEATURE_PATH_PREFIX.asPointer());
        } else {
            return messagePath.get(1)
                    .map(JsonKey::toString)
                    .orElseThrow(() -> IllegalAdaptableException.newInstance(
                                    "Message path of payload does not contain a feature ID.",
                                    MessageFormat.format("Please ensure that the message path of the payload consists" +
                                                    " of two segments, starting with {0}/ and ending with" +
                                                    " the feature ID.",
                                            FEATURE_PATH_PREFIX),
                                    adaptable
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
                final JsonObject jsonObject = jsonValue.asObject();
                if (jsonObject.isNull()) {
                    result = Optional.empty();
                } else {
                    result = Optional.of(ThingsModelFactory.newFeatureBuilder(jsonObject)
                            .useId(getFeatureIdOrThrow())
                            .build());
                }
            } else {
                throw newPayloadValueNotJsonObjectException(Feature.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    Feature getFeatureOrThrow() {
        return getFeature().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a Feature as JSON object it has no value at all.",
                "Please ensure that the payload contains a valid Feature JSON object as value.",
                adaptable
        ));
    }

    Optional<ThingDefinition> getThingDefinition() {
        final Optional<ThingDefinition> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isString()) {
                result = Optional.of(ThingsModelFactory.newDefinition(jsonValue.asString()));
            } else {
                throw IllegalAdaptableException.newInstance(
                        MessageFormat.format("Payload value is not a {0} as JSON string but <{1}>.",
                                ThingDefinition.class.getSimpleName(),
                                jsonValue),
                        adaptable
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    ThingDefinition getThingDefinitionOrThrow() {
        return getThingDefinition().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a ThingDefinition as JSON string because it has no value at all.",
                "Please ensure that the payload contains a valid ThingDefinition JSON string as value.",
                adaptable
        ));
    }

    Optional<FeatureDefinition> getFeatureDefinition() {
        final Optional<FeatureDefinition> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isArray()) {
                result = Optional.of(ThingsModelFactory.newFeatureDefinition(jsonValue.asArray()));
            } else {
                throw IllegalAdaptableException.newInstance(
                        MessageFormat.format("Payload value is not a {0} as JSON array but <{1}>.",
                                FeatureDefinition.class.getSimpleName(),
                                jsonValue),
                        adaptable
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    FeatureDefinition getFeatureDefinitionOrThrow() {
        return getFeatureDefinition().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a FeatureDefinition as JSON array because it has no value at all.",
                "Please ensure that the payload contains a valid FeatureDefinition JSON array as value.",
                adaptable
        ));
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

    FeatureProperties getFeaturePropertiesOrThrow() {
        return getFeatureProperties().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a FeatureProperties as JSON object it has no value at all.",
                "Please ensure that the payload contains a valid FeatureProperties JSON object as value.",
                adaptable
        ));
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
                .orElseThrow(() -> IllegalAdaptableException.newInstance(
                        MessageFormat.format("Message path of payload does not contain a sub-pointer" +
                                " at level <{0,number}>.", FEATURE_PROPERTY_PATH_LEVEL),
                        MessageFormat.format("Please ensure that the message path complies to schema {0}.", schema),
                        adaptable));
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
                throw IllegalAdaptableException.newInstance(message,
                        MessageFormat.format("Please ensure that the message path complies to schema {0}.", schema),
                        adaptable);
            }
        });
    }

    JsonPointer getFeatureDesiredPropertyPointerOrThrow() {
        return getFeaturePropertyPointerOrThrow(JsonKey.of("desiredProperties"));
    }

    Optional<JsonValue> getFeaturePropertyValue() {
        return getPayloadValue();
    }

    JsonValue getFeaturePropertyValueOrThrow() {
        return getFeaturePropertyValue().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a feature property value because it has no value at all.",
                "Please ensure that the payload contains a JSON value as value.",
                adaptable
        ));
    }

    Optional<String> getNamespace() {
        final Optional<String> result;
        final TopicPath topicPath = adaptable.getTopicPath();
        final String namespace = topicPath.getNamespace();
        if (TopicPath.ID_PLACEHOLDER.equals(namespace)) {
            result = Optional.empty();
        } else {
            result = Optional.of(namespace);
        }
        return result;
    }

    Optional<PolicyId> getPolicyId() {
        final Optional<PolicyId> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isString()) {
                result = Optional.of(PolicyId.of(jsonValue.asString()));
            } else {
                throw IllegalAdaptableException.newInstance(
                        MessageFormat.format("Payload value is not a {0} as JSON string but <{1}>.",
                                PolicyId.class.getSimpleName(),
                                jsonValue),
                        adaptable
                );
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    PolicyId getPolicyIdOrThrow() {
        return getPolicyId().orElseThrow(() -> IllegalAdaptableException.newInstance(
                "Payload does not contain a PolicyId as JSON string because it has no value at all.",
                "Please ensure that the payload contains a valid PolicyId JSON string value as value.",
                adaptable
        ));
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

    Optional<PolicyImports> getPolicyImports() {
        final Optional<PolicyImports> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(PoliciesModelFactory.newPolicyImports(jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(PolicyImports.class, jsonValue);
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    PolicyId getImportedPolicyId() {
        final MessagePath path = adaptable.getPayload().getPath();
        return path.getRoot()
                .filter(entries -> Policy.JsonFields.IMPORTS.getPointer().equals(entries.asPointer()))
                .map(entries -> path.nextLevel())
                .flatMap(JsonPointer::getRoot)
                .map(JsonKey::toString)
                .map(PolicyId::of)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    Optional<PolicyImport> getPolicyImport() {
        final Optional<PolicyImport> result;
        final Optional<JsonValue> payloadValueOptional = getPayloadValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isObject()) {
                result = Optional.of(PoliciesModelFactory.newPolicyImport(getPolicyIdOrThrow(), jsonValue.asObject()));
            } else {
                throw newPayloadValueNotJsonObjectException(PolicyImports.class, jsonValue);
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
                .orElseThrow(() -> IllegalAdaptableException.newInstance("Path does not contain a policy label.",
                        "Please ensure that the path of the Adaptable contains a policy label.",
                        adaptable));
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
                .orElseThrow(() -> IllegalAdaptableException.newInstance(
                        MessageFormat.format("Message messagePath of payload does have resource key " +
                                "at level <{0,number}>.", RESOURCE_PATH_LEVEL),
                        "Please ensure that the message path complies to schema " + schema + ".",
                        adaptable
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
                .orElseThrow(() -> IllegalAdaptableException.newInstance(
                        MessageFormat.format("Message path of payload does not contain a subject ID" +
                                " at level <{0,number}>.", SUBJECT_PATH_LEVEL),
                        "Please ensure that message path complies to schema " + schema + ".",
                        adaptable
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
