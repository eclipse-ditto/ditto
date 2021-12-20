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
package org.eclipse.ditto.protocol.adapter;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * A function to extract information from the topic path and enrich the ditto headers with them.
 * <p>
 * A set of available extractors, e.g. for live-channel and entityId, which can be used for
 * {@link #injectHeaders(DittoHeaders, TopicPath, Extractor[])} is provided by inner class {@link Extractor}.
 *
 * @since 2.3.0
 */
public final class HeadersFromTopicPath {

    private HeadersFromTopicPath() {
    }

    /**
     * Injects new headers
     *
     * @param dittoHeaders the headers where the additional headers shall be injected into.
     * @param topicPath where the headers will be extracted from.
     * @param topicPathExtractors the extractor functions. Use {@link Extractor}
     * @return new enriched ditto headers.
     */
    public static DittoHeaders injectHeaders(final DittoHeaders dittoHeaders,
            final TopicPath topicPath,
            final Extractor... topicPathExtractors) {

        final Map<String, String> headersFromTopicPath = Arrays.stream(topicPathExtractors)
                .map(fn -> fn.apply(topicPath))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return dittoHeaders
                .toBuilder()
                .putHeaders(headersFromTopicPath)
                .build();
    }

    /**
     * only a type declaration and a set of implemented extractor functions, which can be used for
     * {@link #injectHeaders(DittoHeaders, TopicPath, Extractor[])}
     */
    public interface Extractor extends Function<TopicPath, Optional<Map.Entry<String, String>>> {

        /**
         * Extracts the channel, if it is 'live'
         *
         * @param topicPath the topic path to extract information from.
         * @return header KV containing the extra information from topic path.
         */
        static Optional<Map.Entry<String, String>> liveChannelExtractor(final TopicPath topicPath) {
            if (topicPath.isChannel(TopicPath.Channel.LIVE)) {
                final String key = DittoHeaderDefinition.CHANNEL.getKey();
                final String value = TopicPath.Channel.LIVE.getName();

                return Optional.of(new SimpleImmutableEntry<>(key, value));
            } else {
                return Optional.empty();
            }
        }

        /**
         * Extracts the entityId from the Topic-Path if none of the pieces is a placeholder ('_').
         *
         * @param topicPath the topic path to extract information from.
         * @return header KV containing the extra information from topic path.
         */
        static Optional<Map.Entry<String, String>> entityIdExtractor(final TopicPath topicPath) {
            return getEntityId(topicPath)
                    .map(entityId -> new SimpleImmutableEntry<>(DittoHeaderDefinition.ENTITY_ID.getKey(), entityId));
        }

    }

    private static Optional<String> getEntityId(final TopicPath topicPath) {
        final TopicPath.Group group = topicPath.getGroup();
        final String namespace = topicPath.getNamespace();
        final String entityName = topicPath.getEntityName();
        if (!TopicPath.ID_PLACEHOLDER.equals(namespace) && !TopicPath.ID_PLACEHOLDER.equals(entityName)) {
            return Optional.of(String.join(":", group.getEntityType(), namespace, entityName));
        } else {
            return Optional.empty();
        }
    }

}
