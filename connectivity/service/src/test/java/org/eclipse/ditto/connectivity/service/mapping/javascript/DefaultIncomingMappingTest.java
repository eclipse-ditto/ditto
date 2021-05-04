/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.MessagePath;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.junit.Test;

public class DefaultIncomingMappingTest {

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final String THING_NAME = "example";
    private static final String RETRIEVE_THINGS_TOPIC_PATH =
            NAMESPACE + "/" + THING_NAME + "/things/twin/commands/retrieve";

    private static final String CONTENT_TYPE = "application/vnd.eclipse.ditto+json";

    private static final String RETRIEVE_THINGS_COMMAND = "" +
            "{\n" +
            "    \"topic\": \"" + RETRIEVE_THINGS_TOPIC_PATH + "\",\n" +
            "    \"headers\": {\n" +
            "        \"content-type\": \"" + CONTENT_TYPE + "\"\n" +
            "    },\n" +
            "    \"path\": \"/\"\n" +
            "}";

    private static final DefaultIncomingMapping UNDER_TEST = DefaultIncomingMapping.get();

    @Test
    public void applyWithTextPayload() {
        final ExternalMessageBuilder externalMessageBuilder =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap());

        final ExternalMessage externalMessage = externalMessageBuilder.withText(RETRIEVE_THINGS_COMMAND).build();
        final MessagePath expectedPath = Payload.newBuilder(JsonPointer.of("/")).build().getPath();
        final TopicPath expectedTopicPath =
                TopicPath.newBuilder(ThingId.of(NAMESPACE, THING_NAME)).things().twin().commands().retrieve().build();
        final List<Adaptable> adaptableList = UNDER_TEST.apply(externalMessage);

        assertThat(adaptableList).isNotEmpty();
        final Adaptable adaptable = adaptableList.get(0);
        assertThat(adaptable.getDittoHeaders()).isEqualTo(DittoHeaders.newBuilder().contentType(CONTENT_TYPE).build());
        assertThat(adaptable.getTopicPath()).isEqualTo(expectedTopicPath);
        assertThat((CharSequence) adaptable.getPayload().getPath()).isEqualTo(expectedPath);
    }

    @Test
    public void applyWithBytePayload() {
        final ExternalMessageBuilder externalMessageBuilder =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap());

        final ExternalMessage externalMessage = externalMessageBuilder
                .withBytes(RETRIEVE_THINGS_COMMAND.getBytes())
                .build();

        final MessagePath expectedPath = Payload.newBuilder(JsonPointer.of("/")).build().getPath();
        final TopicPath expectedTopicPath =
                TopicPath.newBuilder(ThingId.of(NAMESPACE, THING_NAME)).things().twin().commands().retrieve().build();
        final List<Adaptable> adaptableList = UNDER_TEST.apply(externalMessage);

        assertThat(adaptableList).isNotEmpty();
        final Adaptable adaptable = adaptableList.get(0);
        assertThat(adaptable.getDittoHeaders()).isEqualTo(DittoHeaders.newBuilder().contentType(CONTENT_TYPE).build());
        assertThat(adaptable.getTopicPath()).isEqualTo(expectedTopicPath);
        assertThat((CharSequence) adaptable.getPayload().getPath()).isEqualTo(expectedPath);
    }

}
