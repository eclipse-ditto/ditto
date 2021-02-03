/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.LIVE;

import java.time.Instant;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.contenttype.ContentType;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.EventsTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.LiveTwinTest;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.protocoladapter.UnknownPathException;
import org.eclipse.ditto.signals.events.things.ThingMerged;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocoladapter.things.ThingEventAdapter}.
 */
public final class ThingMergedEventAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingMergedEventAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingMergedEventAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownPathException.class)
    public void unknownCommandFromAdaptable() {
        final Instant now = Instant.now();
        final Adaptable adaptable = Adaptable.newBuilder(topicPathMerged())
                .withPayload(Payload.newBuilder(JsonPointer.of("/_policy"))
                        .withValue(TestConstants.THING.toJson())
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test
    public void thingMergedFromAdaptable() {
        final JsonPointer path = TestConstants.THING_POINTER;
        final JsonValue value = TestConstants.THING.toJson();
        final long revision = TestConstants.REVISION;

        final Instant now = Instant.now();
        final ThingMerged expected =
                ThingMerged.of(TestConstants.THING_ID, path, value,
                        revision, now, setChannelHeader(TestConstants.DITTO_HEADERS_V_2), null);


        final Adaptable adaptable = Adaptable.newBuilder(topicPathMerged())
                .withPayload(Payload.newBuilder(path)
                        .withValue(value)
                        .withRevision(revision)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingMerged actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingMergedToAdaptable() {
        final JsonPointer path = TestConstants.THING_POINTER;
        final JsonValue value = TestConstants.THING.toJson();
        final long revision = TestConstants.REVISION;

        final Instant now = Instant.now();
        final Adaptable expected = Adaptable.newBuilder(topicPathMerged())
                .withPayload(Payload.newBuilder(path)
                        .withValue(value)
                        .withRevision(revision)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingMerged thingMerged =
                ThingMerged.of(TestConstants.THING_ID, path, value,
                        revision, now, setChannelHeader(TestConstants.DITTO_HEADERS_V_2), null);
        final Adaptable actual = underTest.toAdaptable(thingMerged, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
        assertThat(actual.getDittoHeaders()).containsEntry(DittoHeaderDefinition.CONTENT_TYPE.getKey(),
                ContentType.APPLICATION_MERGE_PATCH_JSON.getValue());
    }


    private DittoHeaders setChannelHeader(final DittoHeaders dittoHeaders) {
        if (channel == LIVE) {
            return dittoHeaders.toBuilder().channel(LIVE.getName()).build();
        } else {
            return dittoHeaders;
        }
    }

    private TopicPath topicPathMerged() {
        return topicPathBuilder().merged().build();
    }

    private EventsTopicPathBuilder topicPathBuilder() {
        final TopicPathBuilder topicPathBuilder = TopicPath.newBuilder(TestConstants.THING_ID)
                .things();

        if (channel == LIVE) {
            topicPathBuilder.live();
        } else {
            topicPathBuilder.twin();
        }

        return topicPathBuilder.events();
    }

}
