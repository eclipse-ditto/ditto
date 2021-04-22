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
package org.eclipse.ditto.protocol.adapter.things;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownPathException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


/**
 * Unit test for {@link org.eclipse.ditto.protocol.adapter.things.ThingModifyCommandResponseAdapter}.
 */
@RunWith(Parameterized.class)
public final class ThingMergeCommandResponseAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingMergeCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingMergeCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownPathException.class)
    public void unknownCommandFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.of("/_policy"))
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test
    public void mergeThingResponseFromAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.THING_POINTER;

        final MergeThingResponse mergeThingResponse = MergeThingResponse.of(TestConstants.THING_ID,
                path, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptableCreated = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingModifyCommandResponse<?> actualCreated = underTest.fromAdaptable(adaptableCreated);

        assertWithExternalHeadersThat(actualCreated).isEqualTo(mergeThingResponse);
    }

    @Test
    public void mergeThingResponseToAdaptable() {
        final TopicPath topicPath = topicPath(TopicPath.Action.MERGE);
        final JsonPointer path = TestConstants.THING_POINTER;

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(HttpStatus.NO_CONTENT)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThingResponse mergeThingResponse = MergeThingResponse.of(TestConstants.THING_ID,
                path, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actualMerged = underTest.toAdaptable(mergeThingResponse, channel);

        assertWithExternalHeadersThat(actualMerged).isEqualTo(expected);
        assertThat(actualMerged.getDittoHeaders()).containsEntry(DittoHeaderDefinition.CONTENT_TYPE.getKey(),
                ContentType.APPLICATION_MERGE_PATCH_JSON.getValue());
    }
}
