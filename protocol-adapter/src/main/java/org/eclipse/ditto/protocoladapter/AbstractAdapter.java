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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Map;

import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.things.ThingId;

/**
 * Abstract implementation of {@link Adapter} to provide common functionality.
 */
abstract class AbstractAdapter<T extends Jsonifiable> implements Adapter<T> {

    private final Map<String, JsonifiableMapper<T>> mappingStrategies;
    private final HeaderTranslator headerTranslator;
    protected final PathMatcher pathMatcher;


    protected AbstractAdapter(final Map<String, JsonifiableMapper<T>> mappingStrategies,
            final HeaderTranslator headerTranslator, final PathMatcher pathMatcher) {
        this.mappingStrategies = requireNonNull(mappingStrategies);
        this.headerTranslator = requireNonNull(headerTranslator);
        this.pathMatcher = requireNonNull(pathMatcher);
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

    protected static ThingId thingIdFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getId());
    }

    protected static String featureIdForMessageFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getPath()
                .getFeatureId()
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    //    public static void main(String[] args) {
//        final ModifyPolicyEntries modifyPolicyEntries = ModifyPolicyEntries.of(PolicyId.of("ns", "policy"),
//                Arrays.asList(PoliciesModelFactory.newPolicyEntry("lbl",
//                        Arrays.asList(
//                                PoliciesModelFactory.newSubject(SubjectId.newInstance(SubjectIssuer.GOOGLE, "123"),
//                                        SubjectType.GENERATED)),
//                        Arrays.asList(PoliciesModelFactory.newResource(ResourceKey.newInstance("type", "path"),
//                                EffectedPermissions.newInstance(Arrays.asList("READ"), Arrays.asList("WRITE")))))),
//                DittoHeaders.newBuilder().correlationId("cid").build());
//
//        System.out.println(modifyPolicyEntries.toJsonString());
//    }


//    private static Iterable<Resource> resourcesFrom(final Adaptable adaptable) {
//        return adaptable.getPayload().getValue()
//            .map(JsonValue::asObject)
//            .map(jo -> jo.get(PolicyEntry.JsonFields.RESOURCES))
//            .map(jsonObject -> jsonObject.stream()
//                    .map(f -> PoliciesModelFactory.newResource(ResourceKey.newInstance(f.getKeyName()), f.getValue()))
//                    .collect(Collectors.toList()))
//            .orElseThrow(() -> JsonParseException.newBuilder().build());
//    }
//
//    private static Iterable<Subject> subjectsFrom(final Adaptable adaptable) {
//        return adaptable.getPayload().getValue()
//                .map(JsonValue::asObject)
//                .map(jo -> jo.get(PolicyEntry.JsonFields.SUBJECTS))
//                .map(jsonObject -> jsonObject.stream()
//                        .map(f -> PoliciesModelFactory.newSubject(f.getKeyName(), f.getValue().asObject()))
//                        .collect(Collectors.toList()))
//                .orElseThrow(() -> JsonParseException.newBuilder().build());
//    }

    protected static HttpStatusCode statusCodeFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus().orElse(null);
    }

    protected static String namespaceFrom(final Adaptable adaptable) {
        final String namespace = adaptable.getTopicPath().getNamespace();
        return "_".equals(namespace) ? null : namespace;
    }

    protected static String leafValue(final JsonPointer path) {
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

        final JsonifiableMapper<T> jsonifiableMapper = mappingStrategies.get(type);
        if (null == jsonifiableMapper) {
            throw UnknownTopicPathException.fromTopicAndPath(externalAdaptable.getTopicPath(),
                    externalAdaptable.getPayload().getPath(), filteredHeaders);
        }

        final Adaptable adaptable = externalAdaptable.setDittoHeaders(filteredHeaders);
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
        final DittoHeadersBuilder headersBuilder = DittoHeaders.newBuilder();
        if (topicPath.getNamespace() != null && topicPath.getId() != null) {
            // add thing ID for known topic-paths for error reporting.
            headersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(),
                    topicPath.getNamespace() + ":" + topicPath.getId());
        }
        if (topicPath.getChannel() == TopicPath.Channel.LIVE) {
            headersBuilder.channel(TopicPath.Channel.LIVE.getName());
        }
        return headersBuilder.build();
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

}
