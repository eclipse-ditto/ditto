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
package org.eclipse.ditto.protocol.adapter;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Map;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.MessagePath;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.HeadersFromTopicPath.Extractor;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;

/**
 * Abstract implementation of {@link Adapter} to provide common functionality.
 */
public abstract class AbstractAdapter<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        implements Adapter<T> {

    /**
     * Fixed criterion part of the adaptable type used in response signals.
     */
    protected static final String RESPONSES_CRITERION = "responses";

    protected final PayloadPathMatcher payloadPathMatcher;

    private final MappingStrategies<T> mappingStrategies;
    private final HeaderTranslator headerTranslator;

    protected AbstractAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator,
            final PayloadPathMatcher payloadPathMatcher) {

        this.mappingStrategies = checkNotNull(mappingStrategies, "mappingStrategies");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.payloadPathMatcher = checkNotNull(payloadPathMatcher, "payloadPathMatcher");
    }

    /*
     * injects header reading phase to parsing of protocol messages.
     */
    @Override
    public final T fromAdaptable(final Adaptable externalAdaptable) {
        checkNotNull(externalAdaptable, "externalAdaptable");

        // Get type from external adaptable before header filtering in case some headers exist for external messages
        // but not internally in Ditto.
        final String type = getType(externalAdaptable);
        final DittoHeaders filteredHeaders = filterHeadersAndAddExtraHeadersFromTopicPath(externalAdaptable);
        final JsonifiableMapper<T> mapper = getJsonifiableMapperOrThrow(type, externalAdaptable, filteredHeaders);
        final Adaptable adaptable = externalAdaptable.setDittoHeaders(filteredHeaders);
        return DittoJsonException.wrapJsonRuntimeException(() -> mapper.map(adaptable));
    }

    /**
     * Determine the type from {@link Adaptable} (default implementation, subclasses may overwrite this method).
     *
     * @param adaptable the processed adaptable
     * @return the type of the adaptable
     */
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final Payload adaptablePayload = adaptable.getPayload();
        final JsonPointer path = adaptablePayload.getPath();
        final String commandName = getActionOrThrow(topicPath) + upperCaseFirst(payloadPathMatcher.match(path));
        return topicPath.getGroup() + "." + getTypeCriterionAsString(topicPath) + ":" + commandName;
    }

    private static TopicPath.Action getActionOrThrow(final TopicPath topicPath) {
        return topicPath.getAction()
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
    }

    private JsonifiableMapper<T> getJsonifiableMapperOrThrow(final String type,
            final Adaptable externalAdaptable,
            final DittoHeaders filteredHeaders) {

        final JsonifiableMapper<T> result = mappingStrategies.find(type);
        if (null == result) {
            final Payload adaptablePayload = externalAdaptable.getPayload();
            throw UnknownTopicPathException.fromTopicAndPath(externalAdaptable.getTopicPath(),
                    adaptablePayload.getPath(),
                    filteredHeaders);
        }
        return result;
    }

    /**
     * Returns the given String {@code s} with an upper case first letter.
     *
     * @param s the String.
     * @return the upper case String.
     */
    protected static String upperCaseFirst(final String s) {
        final String result;
        if (s.isEmpty()) {
            result = s;
        } else {
            final char[] chars = s.toCharArray();
            chars[0] = Character.toUpperCase(chars[0]);
            result = new String(chars);
        }
        return result;
    }

    /**
     * Extracts the criterion from the given topic path. By default the criterion is directly read from topic path, but
     * subclasses may overwrite this method (e.g. responses have a fixed criterion).
     *
     * @param topicPath the topic path of the adaptable
     * @return the criterion used in the type as a string
     */
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        final TopicPath.Criterion criterion = topicPath.getCriterion();
        return criterion.getName();
    }

    // filter headers by header translator, then inject any missing information from topic path
    private DittoHeaders filterHeadersAndAddExtraHeadersFromTopicPath(final Adaptable externalAdaptable) {
        final DittoHeaders dittoHeadersFromExternal =
                DittoHeaders.of(headerTranslator.fromExternalHeaders(externalAdaptable.getDittoHeaders()));

        return HeadersFromTopicPath.injectHeaders(dittoHeadersFromExternal,
                externalAdaptable.getTopicPath(),
                Extractor::liveChannelExtractor,
                Extractor::entityIdExtractor);
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
     * Subclasses must implement the method to map from the given {@link org.eclipse.ditto.base.model.signals.Signal}
     * to an {@link Adaptable}.
     *
     * @param signal the signal to map.
     * @param channel the channel to which the signal belongs.
     * @return the mapped {@link Adaptable}
     */
    protected abstract Adaptable mapSignalToAdaptable(T signal, TopicPath.Channel channel);

    /**
     * Validate a prefix of the message path of the adaptable and unescape it.
     * Useful to skip validations for a suffix of the message path, say policy subject ID or thing message subject.
     *
     * @param adaptable the adaptable.
     * @param prefixLevel how many JSON pointer segments to validate.
     * @return adaptable with parsed and unescaped message path prefix.
     */
    protected Adaptable validateAndPreprocessMessagePathPrefix(final Adaptable adaptable, final int prefixLevel) {
        final MessagePath messagePath = adaptable.getPayload().getPath();
        final JsonPointer prefixPointer = messagePath.getPrefixPointer(prefixLevel)
                .map(JsonPointer::toString)
                .map(JsonFactory::newPointer)
                .orElseGet(JsonPointer::empty);
        final JsonPointer parsedMessagePath =
                prefixPointer.append(messagePath.getSubPointer(prefixLevel).orElseGet(JsonPointer::empty));
        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(ProtocolFactory.toPayloadBuilder(adaptable.getPayload())
                        .withPath(parsedMessagePath)
                        .build())
                .build();
    }

}
