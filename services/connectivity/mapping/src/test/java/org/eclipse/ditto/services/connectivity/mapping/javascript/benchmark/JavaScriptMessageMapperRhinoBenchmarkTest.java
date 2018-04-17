/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.mapping.javascript.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.junit.Test;

/**
 * Simple Test executing the benchmark scenarios for
 * {@link org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperRhino}.
 */
public class JavaScriptMessageMapperRhinoBenchmarkTest {

    private static final String MAPPING_INCOMING_NAMESPACE = "org.eclipse.ditto";
    private static final String MAPPING_INCOMING_ID = "jmh-test";

    @Test
    public void simpleMapTextPayload() {
        final Adaptable adaptable = runScenario(new SimpleMapTextPayloadToDitto());
        System.out.println(adaptable);

        assertDefaults(adaptable);
        assertThat(adaptable.getPayload().getValue()).contains(
                JsonValue.of(SimpleMapTextPayloadToDitto.MAPPING_STRING));
    }

    @Test
    public void test1DecodeBinaryPayloadToDitto() {
        final Adaptable adaptable = runScenario(new Test1DecodeBinaryPayloadToDitto());
        System.out.println(adaptable);

        assertDefaults(adaptable);
        assertThat(adaptable.getPayload().getValue()).contains(JsonFactory.readFrom("{\"a\":11,\"b\":8,\"c\":99}"));
    }

    @Test
    public void test2ParseJsonPayloadToDitto() {
        final Adaptable adaptable = runScenario(new Test2ParseJsonPayloadToDitto());
        System.out.println(adaptable);

        assertDefaults(adaptable);
        assertThat(adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(o -> o.getValue("attributes/manufacturer"))
                .orElse(null)
        ).contains(JsonValue.of("myManufacturer"));
    }

    @Test
    public void test3FormatJsonPayloadToDitto() {
        final Adaptable adaptable = runScenario(new Test3FormatJsonPayloadToDitto());
        System.out.println(adaptable);

        assertDefaults(adaptable);
    }

    @Test
    public void test4ConstructJsonPayloadToDitto() {
        final Adaptable adaptable = runScenario(new Test4ConstructJsonPayloadToDitto());
        System.out.println(adaptable);

        assertDefaults(adaptable);
        assertThat(adaptable.getPayload().getValue().map(JsonValue::asObject).map(o -> o.getValue("cc")).orElse(null))
                .contains(JsonValue.of("xxx/456/yyy"));
    }

    @Test
    public void test5DecodeBinaryToDitto() {
        final Adaptable adaptable = runScenario(new Test5DecodeBinaryToDitto());
        System.out.println(adaptable);

        assertDefaults(adaptable);
        assertThat(adaptable.getPayload().getValue()).contains(JsonFactory.readFrom("{\"temperature\":{\"properties\":{\"value\":25.43}},\"pressure\":{\"properties\":{\"value\":1015}},\"humidity\":{\"properties\":{\"value\":42}}}"));
    }

    private Adaptable runScenario(final MapToDittoProtocolScenario scenario) {
        final MessageMapper messageMapper = scenario.getMessageMapper();
        final ExternalMessage externalMessage = scenario.getExternalMessage();
        return messageMapper.map(externalMessage).get();
    }

    private static void assertDefaults(final Adaptable adaptable) {
        assertThat(adaptable.getTopicPath().getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(adaptable.getTopicPath().getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFY);
        assertThat(adaptable.getTopicPath().getNamespace()).isEqualTo(MAPPING_INCOMING_NAMESPACE);
        assertThat(adaptable.getTopicPath().getId()).isEqualTo(MAPPING_INCOMING_ID);
    }
}
