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

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategies;

/**
 * Abstract implementation of {@link Adapter} to provide common functionality.
 */
public abstract class AbstractAdapter<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        implements Adapter<T> {

    /**
     * Fixed criterion part of the adaptable type used in response signals.
     */
    protected static final String RESPONSES_CRITERION = "responses";

    private final MappingStrategies<T> mappingStrategies;
    private final HeaderTranslator headerTranslator;
    protected final PayloadPathMatcher payloadPathMatcher;

    protected AbstractAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator, final PayloadPathMatcher payloadPathMatcher) {
        this.mappingStrategies = requireNonNull(mappingStrategies);
        this.headerTranslator = requireNonNull(headerTranslator);
        this.payloadPathMatcher = requireNonNull(payloadPathMatcher);
    }

    /**
     * Reads Ditto headers from an Adaptable. CAUTION: Headers are taken as-is!.
     *
     * @param adaptable the protocol message.
     * @return the headers of the message.
     */
    protected static DittoHeaders dittoHeadersFrom(final Adaptable adaptable) {
        return adaptable.getDittoHeaders();
    }

    protected static TopicPath.Action getAction(final TopicPath topicPath) {
        return topicPath.getAction()
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
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
        final DittoHeaders filteredHeaders = addTopicPathInfo(
                DittoHeaders.of(headerTranslator.fromExternalHeaders(externalHeaders)),
                externalAdaptable.getTopicPath());

        final JsonifiableMapper<T> jsonifiableMapper = mappingStrategies.find(type);
        if (null == jsonifiableMapper) {
            throw UnknownTopicPathException.fromTopicAndPath(externalAdaptable.getTopicPath(),
                    externalAdaptable.getPayload().getPath(), filteredHeaders);
        }

        final Adaptable adaptable = externalAdaptable.setDittoHeaders(filteredHeaders);
        return DittoJsonException.wrapJsonRuntimeException(() -> jsonifiableMapper.map(adaptable));
    }

    /**
     * Determine the type from {@link Adaptable} (default implementation, subclasses may overwrite this method).
     *
     * @param adaptable the processed adaptable
     * @return the type of the adaptable
     */
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(payloadPathMatcher.match(path));
        return topicPath.getGroup() + "." + getTypeCriterionAsString(topicPath) + ":" + commandName;
    }

    /**
     * Extracts the criterion from the given topic path. By default the criterion is directly read from topic path, but
     * subclasses may overwrite this method (e.g. responses have a fixed criterion).
     *
     * @param topicPath the topic path of the adaptable
     * @return the criterion used in the type as a string
     */
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return topicPath.getCriterion().getName();
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
        final DittoHeadersBuilder<?, ?> headersBuilder = DittoHeaders.newBuilder();
        if (topicPath.getNamespace() != null && topicPath.getId() != null) {
            // add entity ID for known topic-paths for error reporting.
            headersBuilder.putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(),
                    (topicPath.getNamespace() + ":" + topicPath.getId()));
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
        final Adaptable adaptable = mapSignalToAdaptable(signal, channel);
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(adaptable.getDittoHeaders());

        return adaptable.setDittoHeaders(DittoHeaders.of(externalHeaders));
    }

    /**
     * Subclasses must implement the method to map from the given {@link org.eclipse.ditto.signals.base.Signal} to an
     * {@link Adaptable}.
     *
     * @param signal the signal to map.
     * @param channel the channel to which the signal belongs.
     * @return the mapped {@link Adaptable}
     */
    protected abstract Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel);

    /**
     * @return the {@link HeaderTranslator} used for the mapping
     */
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
