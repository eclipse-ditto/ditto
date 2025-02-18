/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.protocol.TopicPath.Channel.LIVE;

import java.time.Instant;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.EventsTopicPathBuilder;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownPathException;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingDefinitionMigratedEventAdapter}.
 */
public final class ThingDefinitionMigratedEventAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingDefinitionMigratedEventAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingDefinitionMigratedEventAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownPathException.class)
    public void unknownCommandFromAdaptable() {
        final Instant now = Instant.now();
        final Adaptable adaptable = Adaptable.newBuilder(topicPathMigrated())
                .withPayload(Payload.newBuilder(JsonPointer.of("/_unknown"))
                        .withValue(TestConstants.THING.toJson())
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test
    public void thingMigratedFromAdaptable() {
        final JsonPointer path = TestConstants.THING_POINTER;
        final JsonObject value = TestConstants.THING.toJson(FieldType.all());
        final long revision = TestConstants.REVISION;

        final Instant now = Instant.now();
        final ThingDefinitionMigrated expected =
                ThingDefinitionMigrated.of(TestConstants.THING,
                        revision, now, setChannelHeader(TestConstants.DITTO_HEADERS_V_2), null);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMigrated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(value)
                        .withRevision(revision)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingDefinitionMigrated actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingMigratedToAdaptable() {
        final JsonPointer path = TestConstants.THING_POINTER;
        final JsonObject value = TestConstants.THING.toJson();
        final long revision = TestConstants.REVISION;

        final Instant now = Instant.now();
        final Adaptable expected = Adaptable.newBuilder(topicPathMigrated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(value)
                        .withRevision(revision)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingDefinitionMigrated thingDefinitionMigrated =
                ThingDefinitionMigrated.of(TestConstants.THING,
                        revision, now, setChannelHeader(TestConstants.DITTO_HEADERS_V_2), null);
        final Adaptable actual = underTest.toAdaptable(thingDefinitionMigrated, channel);

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

    private TopicPath topicPathMigrated() {
        return topicPathBuilder().migrated().build();
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
