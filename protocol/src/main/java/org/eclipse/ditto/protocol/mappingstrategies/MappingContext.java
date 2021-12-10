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
import java.util.Objects;
import java.util.Optional;

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
    private static final int SUBJECT_PATH_LEVEL = 3;
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
        final MessagePath messagePath = getMessagePath();
        if (!messagePath.getRoot().filter(FEATURE_PATH_PREFIX::equals).isPresent()) {
            throw newMessagePathInvalidPrefixException(FEATURE_PATH_PREFIX.asPointer());
        } else {
            if (messagePath.getLevelCount() <= FEATURE_PROPERTY_PATH_LEVEL) {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Message path of payload has <{0,number}> levels which is less than" +
                                        " the required <{1,number}> levels.",
                                messagePath.getLevelCount(),
                                FEATURE_PROPERTY_PATH_LEVEL + 1),
                        "Please ensure that the message path complies to schema" +
                                " \"features/${FEATURE_ID}/properties/${PROPERTY_SUB_PATH_OR_PROPERTY_NAME}\".",
                        adaptable.getDittoHeaders()
                );
            }
            final boolean hasExpectedPropertiesSegment = messagePath.get(FEATURE_PROPERTY_PATH_LEVEL - 1)
                    .filter(levelTwoKey::equals)
                    .isPresent();
            if (!hasExpectedPropertiesSegment) {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Message path of payload is not <{0}> at level <{1,number}>.",
                                levelTwoKey,
                                FEATURE_PROPERTY_PATH_LEVEL - 1),
                        "Please ensure that the message path complies to schema" +
                                " \"features/${FEATURE_ID}/properties/${PROPERTY_SUB_PATH_OR_PROPERTY_NAME}\".",
                        adaptable.getDittoHeaders()
                );
            }
            return messagePath.getSubPointer(FEATURE_PROPERTY_PATH_LEVEL).get();
        }
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
        return getResourceKey()
                .orElseThrow(() -> new IllegalAdaptableException("Path does not contain policy resource key.",
                        "Please ensure that the path of the Adaptable contains the policy resource key.",
                        adaptable.getDittoHeaders()));
    }


    private Optional<ResourceKey> getResourceKey() {
        // expected: entries/<entry>/resources/<type:/path1/path2>
        final MessagePath path = getMessagePath();
        return path.getRoot()
                .filter(entries -> {
                    if (Policy.JsonFields.ENTRIES.getPointer().equals(entries.asPointer())) {
                        return true;
                    } else {
                        throw new IllegalAdaptableException(
                                MessageFormat.format("Path does not include <{0}> but <{1}>",
                                        Policy.JsonFields.ENTRIES.getPointer(),
                                        entries),
                                adaptable.getDittoHeaders());
                    }
                })
                .flatMap(entries -> path.get(RESOURCES_PATH_LEVEL))
                .filter(resources -> {
                    if (PolicyEntry.JsonFields.RESOURCES.getPointer().equals(resources.asPointer())) {
                        return true;
                    } else {
                        throw new IllegalAdaptableException(
                                MessageFormat.format("Path does not include <{0}> but <{1}>",
                                        PolicyEntry.JsonFields.RESOURCES.getPointer(),
                                        resources),
                                adaptable.getDittoHeaders());
                    }
                })
                .flatMap(resources -> path.getSubPointer(RESOURCE_PATH_LEVEL))
                .map(PoliciesModelFactory::newResourceKey);
    }

    Optional<Resource> getResource() {
        return getPayloadValue().map(jsonValue -> Resource.newInstance(getResourceKeyOrThrow(), jsonValue));
    }

    SubjectId getSubjectIdOrThrow() {
        return getSubjectId()
                .orElseThrow(() -> new IllegalAdaptableException("Path does not contain policy resource key.",
                        "Please ensure that the path of the Adaptable contains the policy resource key.",
                        adaptable.getDittoHeaders()));
    }

    private Optional<SubjectId> getSubjectId() {
        // expected: entries/<entry>/resources/<issuer:subject>
        final MessagePath path = getMessagePath();
        return path.getRoot()
                .filter(entries -> {
                    if (Policy.JsonFields.ENTRIES.getPointer().equals(entries.asPointer())) {
                        return true;
                    } else {
                        throw new IllegalAdaptableException(
                                MessageFormat.format("Path does not include <{0}> but <{1}>.",
                                        Policy.JsonFields.ENTRIES.getPointer(),
                                        entries),
                                adaptable.getDittoHeaders());
                    }
                })
                .flatMap(entries -> path.get(RESOURCES_PATH_LEVEL))
                .filter(resources -> {
                    if (PolicyEntry.JsonFields.SUBJECTS.getPointer().equals(resources.asPointer())) {
                        return true;
                    } else {
                        throw new IllegalAdaptableException(
                                MessageFormat.format("Path does not include <{0}> but <{1}>.",
                                        PolicyEntry.JsonFields.SUBJECTS.getPointer(),
                                        resources),
                                adaptable.getDittoHeaders());
                    }
                })
                .flatMap(resources -> path.getSubPointer(SUBJECT_PATH_LEVEL))
                .map(MappingContext::stripLeadingSlash)
                .map(PoliciesModelFactory::newSubjectId);
    }

    private static CharSequence stripLeadingSlash(final CharSequence charSequence) {
        final CharSequence result;
        if (0 < charSequence.length() && '/' == charSequence.charAt(0)) {
            result = charSequence.subSequence(1, charSequence.length());
        } else {
            result = charSequence;
        }
        return result;
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
