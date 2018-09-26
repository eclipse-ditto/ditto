/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.protocoladapter;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 * Abstract implementation of {@link Adapter} to provide common functionality.
 */
abstract class AbstractAdapter<T extends Jsonifiable> implements Adapter<T> {

    private static final int ATTRIBUTE_PATH_LEVEL = 1;
    private static final int FEATURE_PROPERTY_PATH_LEVEL = 3;

    private final Map<String, JsonifiableMapper<T>> mappingStrategies;
    private final HeaderTranslator headerTranslator;

    protected AbstractAdapter(final Map<String, JsonifiableMapper<T>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        this.mappingStrategies = requireNonNull(mappingStrategies);
        this.headerTranslator = requireNonNull(headerTranslator);
    }

    protected static boolean isCreated(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus()
                .map(HttpStatusCode.CREATED::equals)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Reads Ditto headers from an Adaptable. CAUTION: Headers are taken as-is!.
     *
     * @param adaptable the protocol message.
     * @return the headers of the message.
     */
    protected static DittoHeaders dittoHeadersFrom(final Adaptable adaptable) {
        return adaptable.getHeaders().orElseGet(DittoHeaders::empty);
    }

    protected static AuthorizationSubject authorizationSubjectFrom(final Adaptable adaptable) {
        return AuthorizationSubject.newInstance(leafValue(adaptable.getPayload().getPath()));
    }

    protected static JsonFieldSelector selectedFieldsFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getFields().orElse(null);
    }

    protected static String thingIdFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return topicPath.getNamespace() + ":" + topicPath.getId();
    }

    protected static Thing thingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newThing)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonArray thingsArrayFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AccessControlList aclFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(AccessControlListModelFactory::newAcl)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AclEntry aclEntryFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(permissions -> AccessControlListModelFactory
                        .newAclEntry(leafValue(adaptable.getPayload().getPath()), permissions))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Attributes attributesFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newAttributes)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonPointer attributePointerFrom(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.getSubPointer(ATTRIBUTE_PATH_LEVEL)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    protected static JsonValue attributeValueFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static String featureIdFrom(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.get(1).orElseThrow(() -> UnknownPathException.newBuilder(path).build()).toString();
    }

    protected static String featureIdForMessageFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getPath()
                .getFeatureId()
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Features featuresFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatures)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Feature featureFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(jsonObject -> ThingsModelFactory.newFeatureBuilder(jsonObject)
                        .useId(featureIdFrom(adaptable))
                        .build())
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static FeatureDefinition featureDefinitionFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asArray)
                .map(ThingsModelFactory::newFeatureDefinition)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static FeatureProperties featurePropertiesFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatureProperties)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonPointer featurePropertyPointerFrom(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.getSubPointer(FEATURE_PROPERTY_PATH_LEVEL)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    protected static JsonValue featurePropertyValueFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static String policyIdFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asString)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static HttpStatusCode statusCodeFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus().orElse(null);
    }

    protected static String namespaceFrom(final Adaptable adaptable) {
        final String namespace = adaptable.getTopicPath().getNamespace();
        return "_".equals(namespace) ? null : namespace;
    }

    private static String leafValue(final JsonPointer path) {
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

    protected static TopicPath.Action getAction(final TopicPath topicPath) {
        return topicPath.getAction()
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
    }

    protected abstract Adaptable constructAdaptable(final T signal, final TopicPath.Channel channel);

    protected abstract String getType(Adaptable adaptable);

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
        final DittoHeaders externalHeaders = externalAdaptable.getHeaders().orElse(DittoHeaders.empty());
        final DittoHeaders filteredHeaders = addTopicPathInfo(
                headerTranslator.fromExternalHeaders(externalHeaders),
                externalAdaptable.getTopicPath());

        final Adaptable adaptable = externalAdaptable.setDittoHeaders(filteredHeaders);
        final JsonifiableMapper<T> jsonifiableMapper = mappingStrategies.get(type);
        if (null == jsonifiableMapper) {
            throw UnknownTopicPathException.newBuilder(adaptable.getTopicPath()).build();
        }

        return DittoJsonException.wrapJsonRuntimeException(() -> jsonifiableMapper.map(adaptable));
    }

    /**
     * Add to headers any information that will be missing from topic path.
     *
     * @param filteredHeaders headers read from external headers.
     * @param topicPath topic path of an adaptable.
     * @return filteredHeaders with extra information from topicPath.
     */
    private static DittoHeaders addTopicPathInfo(final DittoHeaders filteredHeaders, final TopicPath topicPath) {
        final DittoHeaders extraInfo = mapTopicPathToHeaders(topicPath);
        return extraInfo.isEmpty() ? filteredHeaders : filteredHeaders.toBuilder().putHeaders(extraInfo).build();
    }

    /**
     * Add any extra information in topic path as Ditto headers. Currently "channel" is the only relevant header.
     *
     * @param topicPath the topic path to extract information from.
     * @return headers containing extra information from topic path.
     */
    private static DittoHeaders mapTopicPathToHeaders(final TopicPath topicPath) {
        if (topicPath.getChannel() == TopicPath.Channel.LIVE) {
            return DittoHeaders.newBuilder()
                    .channel(TopicPath.Channel.LIVE.getName())
                    .build();
        } else {
            return DittoHeaders.empty();
        }
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

    protected final HeaderTranslator headerTranslator() {
        return headerTranslator;
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
