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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Test for {@link HeadersFromTopicPath}
 */
public final class HeadersFromTopicPathTest {

    @Test
    public void shallExtractLiveChannel() {
        // Arrange
        final TopicPath topicPath = TopicPath.newBuilder(ThingId.of("org.eclipse.ditto:fancy-thing"))
                .live()
                .commands()
                .modify()
                .build();

        // Act
        final DittoHeaders dittoHeaders = HeadersFromTopicPath.injectHeaders(DittoHeaders.empty(),
                topicPath,
                HeadersFromTopicPath.Extractor::liveChannelExtractor);

        // Assert
        assertThat(dittoHeaders.getChannel()).hasValue("live");
    }

    @Test
    public void shallNotExtractOtherChannels() {
        // Arrange
        final TopicPath topicPath = TopicPath.newBuilder(ThingId.of("org.eclipse.ditto:fancy-thing"))
                .twin()
                .commands()
                .modify()
                .build();

        // Act
        final DittoHeaders dittoHeaders = HeadersFromTopicPath.injectHeaders(DittoHeaders.empty(),
                topicPath,
                HeadersFromTopicPath.Extractor::liveChannelExtractor);

        // Assert
        assertThat(dittoHeaders).isEmpty();
    }

    @Test
    public void shallNotFailWhenNoneChannel() {
        // Arrange
        final TopicPath topicPath = TopicPath.newBuilder(ThingId.of("org.eclipse.ditto:fancy-thing"))
                .none()
                .commands()
                .modify()
                .build();

        // Act
        final DittoHeaders dittoHeaders = HeadersFromTopicPath.injectHeaders(DittoHeaders.empty(),
                topicPath,
                HeadersFromTopicPath.Extractor::liveChannelExtractor);

        // Assert
        assertThat(dittoHeaders).isEmpty();
    }

    @Test
    public void shallExtractEntityId() {
        // Arrange
        final ThingId thingId = ThingId.of("org.eclipse.ditto:fancy-thing");
        final String expectedDittoEntityId = ThingConstants.ENTITY_TYPE + ":" + thingId;

        final TopicPath topicPath = TopicPath.newBuilder(thingId)
                .live()
                .commands()
                .modify()
                .build();

        // Act
        final DittoHeaders dittoHeaders = HeadersFromTopicPath.injectHeaders(DittoHeaders.empty(),
                topicPath,
                HeadersFromTopicPath.Extractor::entityIdExtractor);

        // Assert
        assertThat(dittoHeaders).containsKey(DittoHeaderDefinition.ENTITY_ID.getKey());
    }

    @Test
    public void shallNotFailWithPlaceholder() {
        // Arrange
        final TopicPath topicPath = ProtocolFactory.newTopicPath("_/_/things/twin/commands/modify");

        // Act
        final DittoHeaders dittoHeaders = HeadersFromTopicPath.injectHeaders(DittoHeaders.empty(),
                topicPath,
                HeadersFromTopicPath.Extractor::entityIdExtractor);

        // Assert
        assertThat(dittoHeaders).isEmpty();
    }
}
