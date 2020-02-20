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
package org.eclipse.ditto.protocoladapter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 * Abstract implementation of {@link Adapter} to provide common functionality.
 */
abstract class AbstractAdapter<T extends Jsonifiable> implements Adapter<T> {

    private static final int ATTRIBUTE_PATH_LEVEL = 1;
    private static final int FEATURE_PROPERTY_PATH_LEVEL = 3;

    private final Map<String, JsonifiableMapper<T>> mappingStrategies;
    private final HeaderTranslator headerTranslator;

    protected AbstractAdapter(final Map<String, JsonifiableMapper<T>> mappingStrategies, final HeaderTranslator headerTranslator) {
        this.mappingStrategies = checkNotNull(mappingStrategies, "mappingStrategies");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
    }

    protected static boolean isCreatedOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus()
                .map(HttpStatusCode.CREATED::equals)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AuthorizationSubject getAuthorizationSubject(final Adaptable adaptable) {
        return AuthorizationSubject.newInstance(getLeafValueOrThrow(adaptable.getPayload().getPath()));
    }

    protected static ThingId getThingId(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getId());
    }

    protected static Thing getThingOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newThing)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AccessControlList getAclOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(AccessControlListModelFactory::newAcl)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AclEntry getAclEntryOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(permissions -> AccessControlListModelFactory
                        .newAclEntry(getLeafValueOrThrow(adaptable.getPayload().getPath()), permissions))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Attributes getAttributesOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newAttributes)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonPointer getAttributePointerOrThrow(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.getSubPointer(ATTRIBUTE_PATH_LEVEL)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    protected static JsonValue getAttributeValueOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static ThingDefinition getThingDefinitionOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asString)
                .map(ThingsModelFactory::newDefinition)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static String getFeatureIdOrThrow(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.get(1).orElseThrow(() -> UnknownPathException.newBuilder(path).build()).toString();
    }

    protected static String getFeatureIdForMessageOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        final MessagePath messagePath = payload.getPath();
        return messagePath.getFeatureId()
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Features getFeaturesOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatures)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Feature getFeatureOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(jsonObject -> ThingsModelFactory.newFeatureBuilder(jsonObject)
                        .useId(getFeatureIdOrThrow(adaptable))
                        .build())
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static FeatureDefinition getFeatureDefinitionOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asArray)
                .map(ThingsModelFactory::newFeatureDefinition)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static FeatureProperties getFeaturePropertiesOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatureProperties)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonPointer getFeaturePropertyPointerOrThrow(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.getSubPointer(FEATURE_PROPERTY_PATH_LEVEL)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    protected static JsonValue getFeaturePropertyValueOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static PolicyId getPolicyIdOrThrow(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asString)
                .map(PolicyId::of)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static HttpStatusCode getStatusCodeOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getStatus()
                .orElseThrow(() -> new JsonMissingFieldException(Payload.JsonFields.STATUS));
    }

    @Nullable
    protected static String getNamespaceOrNull(final Adaptable adaptable) {
        final String namespace = adaptable.getTopicPath().getNamespace();
        return "_".equals(namespace) ? null : namespace;
    }

    private static String getLeafValueOrThrow(final JsonPointer path) {
        return path.getLeaf().orElseThrow(() -> UnknownPathException.newBuilder(path).build()).toString();
    }

    protected static CommandsTopicPathBuilder fromTopicPathBuilderWithChannel(final TopicPathBuilder topicPathBuilder,
            final TopicPath.Channel channel) {

        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            commandsTopicPathBuilder = topicPathBuilder.twin().commands();
        } else if (channel == TopicPath.Channel.LIVE) {
            commandsTopicPathBuilder = topicPathBuilder.live().commands();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }
        return commandsTopicPathBuilder;
    }

    protected static TopicPath.Action getActionOrThrow(final TopicPath topicPath) {
        return topicPath.getAction()
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
    }

    /**
     * Returns the given String {@code s} with an upper case first letter.
     *
     * @param s the String.
     * @return the upper case String.
     */
    protected static String upperCaseFirst(final String s) {
        if (s.isEmpty()) {
            return s;
        }

        final char[] chars = s.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    /*
     * injects header reading phase to parsing of protocol messages.
     */
    @Override
    public final T fromAdaptable(final Adaptable externalAdaptable) {
        checkNotNull(externalAdaptable, "Adaptable");
        // get type from external adaptable before header filtering in case some headers exist for external messages
        // but not internally in Ditto.
        final String type = getType(externalAdaptable);

        // filter headers by header translator, then inject any missing information from topic path
        final DittoHeaders externalHeaders = externalAdaptable.getDittoHeaders();
        final DittoHeaders filteredHeaders = addTopicPathInfo(headerTranslator.fromExternalHeaders(externalHeaders),
                externalAdaptable.getTopicPath());

        final JsonifiableMapper<T> jsonifiableMapper = mappingStrategies.get(type);
        if (null == jsonifiableMapper) {
            throw UnknownTopicPathException.fromTopicAndPath(externalAdaptable.getTopicPath(),
                    externalAdaptable.getPayload().getPath(), filteredHeaders);
        }

        final Adaptable adaptable = externalAdaptable.setDittoHeaders(filteredHeaders);
        return DittoJsonException.wrapJsonRuntimeException(() -> jsonifiableMapper.map(adaptable));
    }

    protected abstract String getType(Adaptable adaptable);

    /**
     * Add to headers any information that will be missing from topic path.
     *
     * @param filteredHeaders headers read from external headers.
     * @param topicPath topic path of an adaptable.
     * @return filteredHeaders with extra information from topicPath.
     */
    private static DittoHeaders addTopicPathInfo(final DittoHeaders filteredHeaders, final TopicPath topicPath) {
        final Map<String, String> extraInfo = mapTopicPathToHeaders(topicPath);
        return extraInfo.isEmpty() ? filteredHeaders : filteredHeaders.toBuilder().putHeaders(extraInfo).build();
    }

    /**
     * Add any extra information in topic path as Ditto headers. Currently "channel" is the only relevant header.
     *
     * @param topicPath the topic path to extract information from.
     * @return headers containing extra information from topic path.
     */
    private static Map<String, String> mapTopicPathToHeaders(final TopicPath topicPath) {
        final Map<String, String> extraHeaders = new HashMap<>();
        if (topicPath.getNamespace() != null && topicPath.getId() != null) {
            // add thing ID for known topic-paths for error reporting.
            extraHeaders.put(MessageHeaderDefinition.THING_ID.getKey(),
                    topicPath.getNamespace() + ":" + topicPath.getId());
        }
        if (topicPath.getChannel() == TopicPath.Channel.LIVE) {
            extraHeaders.put(DittoHeaderDefinition.CHANNEL.getKey(), TopicPath.Channel.LIVE.getName());
        }
        return extraHeaders;
    }

    /*
     * inject header publishing phase to creation of protocol messages.
     */
    @Override
    public final Adaptable toAdaptable(final T signal, final TopicPath.Channel channel) {
        final Adaptable adaptable = constructAdaptable(signal, channel);
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(adaptable.getDittoHeaders());
        return adaptable.setDittoHeaders(DittoHeaders.of(externalHeaders));
    }

    protected abstract Adaptable constructAdaptable(T signal, TopicPath.Channel channel);

    protected final HeaderTranslator headerTranslator() {
        return headerTranslator;
    }

    /**
     * Utility class for matching {@link Payload} path.
     */
    static final class PathMatcher {

        static final Map<String, Pattern> PATTERNS = new HashMap<>();

        static {
            PATTERNS.put("thing", Pattern.compile("^/$"));
            PATTERNS.put("acl", Pattern.compile("^/acl$"));
            PATTERNS.put("aclEntry", Pattern.compile("^/acl/[^/]*$"));
            PATTERNS.put("policyId", Pattern.compile("^/policyId$"));
            PATTERNS.put("policy", Pattern.compile("^/_policy"));
            PATTERNS.put("policyEntries", Pattern.compile("^/_policy/entries$"));
            PATTERNS.put("policyEntry", Pattern.compile("^/_policy/entries/.*$"));
            PATTERNS.put("policyEntrySubjects", Pattern.compile("^/_policy/entries/[^/]*/subjects$"));
            PATTERNS.put("policyEntrySubject", Pattern.compile("^/_policy/entries/[^/]*/subjects/.*$"));
            PATTERNS.put("policyEntryResources", Pattern.compile("^/_policy/entries/[^/]*/resources$"));
            PATTERNS.put("policyEntryResource", Pattern.compile("^/_policy/entries/[^/]*/resources/.*$"));
            PATTERNS.put("attributes", Pattern.compile("^/attributes$"));
            PATTERNS.put("attribute", Pattern.compile("^/attributes/.*$"));
            PATTERNS.put("definition", Pattern.compile("^/definition$"));
            PATTERNS.put("features", Pattern.compile("^/features$"));
            PATTERNS.put("feature", Pattern.compile("^/features/[^/]*$"));
            PATTERNS.put("featureDefinition", Pattern.compile("^/features/[^/]*/definition$"));
            PATTERNS.put("featureProperties", Pattern.compile("^/features/[^/]*/properties$"));
            PATTERNS.put("featureProperty", Pattern.compile("^/features/[^/]*/properties/.*$"));
        }

        private PathMatcher() {
            throw new AssertionError();
        }

        /**
         * Matches a given {@code path} against known schemes and returns the corresponding entity name.
         *
         * @param path the path to match.
         * @return the entity name which matched.
         * @throws UnknownPathException if {@code path} matched no known scheme.
         */
        static String match(final JsonPointer path) {
            final Predicate<Map.Entry<String, Pattern>> pathMatchesPattern = entry -> {
                final Pattern pattern = entry.getValue();
                final Matcher matcher = pattern.matcher(path);
                return matcher.matches();
            };

            return PATTERNS.entrySet()
                    .stream()
                    .filter(pathMatchesPattern)
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
        }

    }

}
