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
package org.eclipse.ditto.connectivity.service.mapping.javascript.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.junit.Test;

/**
 * Simple Test executing the benchmark scenarios for {@code JavaScriptMessageMapperRhino}.
 */
public class JavaScriptMessageMapperRhinoBenchmarkTest {

    private static final String MAPPING_INCOMING_NAMESPACE = "org.eclipse.ditto";
    private static final String MAPPING_INCOMING_ID = "jmh-test";

    @Test
    public void simpleMapTextPayload() {
        runScenario(new SimpleMapTextPayloadToDitto()).forEach(
                adaptable -> {
                    System.out.println(adaptable);

                    assertDefaults(adaptable);
                    assertThat(adaptable.getPayload().getValue()).contains(
                            JsonValue.of(SimpleMapTextPayloadToDitto.MAPPING_STRING));
                });
    }

    @Test
    public void test1DecodeBinaryPayloadToDitto() {
        runScenario(new Test1DecodeBinaryPayloadToDitto()).forEach(
                adaptable -> {
                    System.out.println(adaptable);

                    assertDefaults(adaptable);
                    assertThat(adaptable.getPayload().getValue()).contains(
                            JsonFactory.readFrom("{\"a\":11,\"b\":8,\"c\":99}"));
                });
    }

    @Test
    public void test2ParseJsonPayloadToDitto() {
        runScenario(new Test2ParseJsonPayloadToDitto()).forEach(
                adaptable -> {
                    System.out.println(adaptable);

                    assertDefaults(adaptable);
                    assertThat(adaptable.getPayload().getValue()
                            .map(JsonValue::asObject)
                            .map(o -> o.getValue("attributes/manufacturer"))
                            .orElse(null)
                    ).contains(JsonValue.of("myManufacturer"));
                });
    }

    @Test
    public void test3FormatJsonPayloadToDitto() {
        runScenario(new Test3FormatJsonPayloadToDitto()).forEach(
                adaptable -> {
                    System.out.println(adaptable);

                    assertDefaults(adaptable);
                });
    }

    @Test
    public void test4ConstructJsonPayloadToDitto() {
        runScenario(new Test4ConstructJsonPayloadToDitto()).forEach(
                adaptable -> {
                    System.out.println(adaptable);

                    assertDefaults(adaptable);
                    assertThat(adaptable.getPayload()
                            .getValue()
                            .map(JsonValue::asObject)
                            .map(o -> o.getValue("cc"))
                            .orElse(null))
                            .contains(JsonValue.of("xxx/456/yyy"));
                });
    }

    @Test
    public void test5DecodeBinaryToDitto() {
        runScenario(new Test5DecodeBinaryToDitto()).forEach(
                adaptable -> {
                    System.out.println(adaptable);
                    assertDefaults(adaptable);
                    assertThat(adaptable.getPayload().getValue()).contains(JsonFactory.readFrom(
                            "{\"temperature\":{\"properties\":{\"value\":25.43}},\"pressure\":{\"properties\":{\"value\":1015}},\"humidity\":{\"properties\":{\"value\":42}}}"));
                }
        );
    }

    private List<Adaptable> runScenario(final MapToDittoProtocolScenario scenario) {
        final MessageMapper messageMapper = scenario.getMessageMapper();
        final ExternalMessage externalMessage = scenario.getExternalMessage();
        return messageMapper.map(externalMessage);
    }

    private static void assertDefaults(final Adaptable adaptable) {
        assertThat(adaptable.getTopicPath().getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(adaptable.getTopicPath().getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFY);
        assertThat(adaptable.getTopicPath().getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getEntityName()).isEqualTo(MAPPING_INCOMING_ID);
    }
}
