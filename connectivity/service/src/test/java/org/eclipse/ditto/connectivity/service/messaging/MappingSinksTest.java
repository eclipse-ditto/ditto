/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.MappedInboundExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.config.mapping.DefaultMappingConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.protocol.DittoProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.MessageDispatcher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link InboundMappingSink} and its outbound equivalent.
 */
public final class MappingSinksTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final String NOOP_OUTBOUND_SCRIPT = "function mapFromDittoProtocolMsg(){return null;}";
    private static final String NOOP_INBOUND_SCRIPT = "function mapToDittoProtocolMsg(){return null;}";

    @Rule
    public final ActorSystemResource resource = ActorSystemResource.newInstance(ConfigFactory.load("test"));

    @Test
    public void inboundConcurrentJsMapping() {
        new TestKit(resource.getActorSystem()) {{
            // GIVEN:
            // Incoming script sleeps for the seconds specified in the text payload,
            // then reads and sets the thing name as the value of the global variable.
            final int processorPoolSize = 5;
            final var connection = getConnection(getRacyInboundScript(), NOOP_OUTBOUND_SCRIPT, processorPoolSize);
            final var processors = IntStream.range(0, processorPoolSize)
                    .mapToObj(i -> getInboundMappingProcessor(connection))
                    .toList();
            final var sink = Sink.foreach(o -> testActor().tell(o, ActorRef.noSender()));
            final var underTest = InboundMappingSink.createSink(processors, connection.getId(),
                    processorPoolSize, sink, getMappingConfig(),
                    ThrottlingConfig.of(ConfigFactory.empty()),
                    (MessageDispatcher) resource.getActorSystem().getDispatcher());

            // WHEN:
            // Mapper is asked to map the text messages ["5", "4", "3", "2", "1"], which make the mapper sleep for 5 to 1s.
            final var messages = IntStream.range(-processorPoolSize, 0)
                    .map(i -> -i)
                    .<Object>mapToObj(i -> {
                        final var string = String.valueOf(i);
                        final var headers = Map.of("i", string, "content-type", "text/plain");
                        final var message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                                .withText(string)
                                .withPayloadMapping(ConnectivityModelFactory.newPayloadMapping("javascript"))
                                .build();
                        return new ExternalMessageWithSender(message, testActor());
                    })
                    .toList();

            Source.from(messages).to(underTest).run(resource.getActorSystem());

            // THEN:
            // The results of the mapper should preserve the incoming message order "5", "4", "3", "2", "1".
            // The results should not be "4", "3", "2", "1", "1" due to the global variable persisting between messages.
            for (int i = processorPoolSize; i > 0; --i) {
                final var outcomes = expectMsgClass(FiniteDuration.apply(30, "s"), InboundMappingOutcomes.class);
                assertThat(outcomes.getOutcomes()).hasSize(1);
                final int j = i;
                outcomes.getOutcomes().get(0).accept(new MappingOutcome.Visitor<MappedInboundExternalMessage, Void>() {

                    @Override
                    public Void onMapped(final String mapperId, final MappedInboundExternalMessage mapped) {
                        final var signal = mapped.getSignal();
                        assertThat(signal).isInstanceOf(ModifyAttribute.class);
                        final var modifyAttribute = (ModifyAttribute) signal;
                        assertThat(modifyAttribute.getAttributeValue()).isEqualTo(JsonValue.of(String.valueOf(j)));
                        assertThat(modifyAttribute.getEntityId().toString()).hasToString("ns:" + j);
                        return null;
                    }

                    @Override
                    public Void onDropped(final String mapperId, @Nullable final ExternalMessage droppedMessage) {
                        throw new AssertionError("Not expecting dropped: " + droppedMessage);
                    }

                    @Override
                    public Void onError(final String mapperId, final Exception error,
                            @Nullable final TopicPath topicPath,
                            @Nullable final ExternalMessage externalMessage) {
                        throw new AssertionError("Not expecting error: " + externalMessage, error);
                    }
                });
            }
        }};
    }

    @Test
    public void outboundConcurrentJsMapping() {
        new TestKit(resource.getActorSystem()) {{
            // GIVEN:
            // Outgoing script sleeps for the seconds specified in the signal entity name,
            // then reads and sets the text payload as the value of the global variable.
            final int processorPoolSize = 5;
            final var connection = getConnection(NOOP_INBOUND_SCRIPT, getRacyOutboundScript(), processorPoolSize);
            final var processors = IntStream.range(0, processorPoolSize)
                    .mapToObj(i -> getOutboundMappingProcessor(connection))
                    .toList();
            final Props props = OutboundMappingProcessorActor.props(testActor(), processors, connection,
                    TestConstants.CONNECTIVITY_CONFIG, 3);
            final ActorRef underTest = childActorOf(props);

            // WHEN:
            // Mapper is asked to map the events with thing names ["5", "4", "3", "2", "1"],
            // which make the mapper sleep for 5 to 1s.
            for (int i = processorPoolSize; i > 0; --i) {
                final var string = String.valueOf(i);
                final var headers = DittoHeaders.of(Map.of("i", string));
                final var signal = AttributeModified.of(ThingId.of("ns:" + i),
                        JsonPointer.of("/attributes/i"), JsonValue.of(i), i, null, headers, null);
                final var message = OutboundSignalFactory.newOutboundSignal(signal, connection.getTargets());
                underTest.tell(message, testActor());
            }

            // THEN:
            // The results of the mapper should preserve the incoming message order with text payload
            // "5", "4", "3", "2", "1". The results should not have text payload "4", "3", "2", "1", "1"
            // due to the global variable persisting between messages.
            for (int i = processorPoolSize; i > 0; --i) {
                final var publish =
                        expectMsgClass(FiniteDuration.apply(30, "s"), BaseClientActor.PublishMappedMessage.class);
                assertThat(publish.getOutboundSignal().getMappedOutboundSignals()).hasSize(1);
                final var outboundSignal = publish.getOutboundSignal().getMappedOutboundSignals().get(0);
                assertThat(outboundSignal.getExternalMessage().getHeaders()).containsEntry("i", String.valueOf(i));
                assertThat(outboundSignal.getExternalMessage().getTextPayload()).contains(String.valueOf(i));
            }
        }};
    }

    private static Map<String, MappingContext> getJavascriptMappings(final String inboundScript,
            final String outboundScript) {
        return Map.of("javascript", ConnectivityModelFactory.newMappingContext("JavaScript",
                JsonObject.newBuilder()
                        .set("incomingScript", inboundScript)
                        .set("outgoingScript", outboundScript)
                        .build(),
                Map.of(), Collections.emptyMap()));
    }

    private static Connection getConnection(final String inboundScript, final String outboundScript, final int poolSize) {
        final var mappings = getJavascriptMappings(inboundScript, outboundScript);
        final var definition = ConnectivityModelFactory.newPayloadMappingDefinition(mappings);
        final var payloadMapping = ConnectivityModelFactory.newPayloadMapping("javascript");
        final var source = ConnectivityModelFactory.newSourceBuilder(
                        TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT.get(0))
                .payloadMapping(payloadMapping)
                .build();
        final var target = ConnectivityModelFactory.newTargetBuilder(TestConstants.Targets.TWIN_TARGET)
                .payloadMapping(payloadMapping)
                .build();
        return TestConstants.createConnection()
                .toBuilder()
                .processorPoolSize(poolSize)
                .payloadMappingDefinition(definition)
                .setSources(List.of(source))
                .setTargets(List.of(target))
                .build();
    }

    private OutboundMappingProcessor getOutboundMappingProcessor(final Connection connection) {
        final var provider = new DittoProtocolAdapterProvider(DefaultProtocolConfig.of(ConfigFactory.empty()));
        final var adapter = provider.getProtocolAdapter(null);
        final var logger = TestConstants.mockThreadSafeDittoLoggingAdapter();
        return OutboundMappingProcessor.of(connection, TestConstants.CONNECTIVITY_CONFIG, resource.getActorSystem(),
                adapter, logger);
    }

    private InboundMappingProcessor getInboundMappingProcessor(final Connection connection) {
        final var provider = new DittoProtocolAdapterProvider(DefaultProtocolConfig.of(ConfigFactory.empty()));
        final var adapter = provider.getProtocolAdapter(null);
        final var logger = TestConstants.mockThreadSafeDittoLoggingAdapter();
        return InboundMappingProcessor.of(connection, TestConstants.CONNECTIVITY_CONFIG, resource.getActorSystem(),
                adapter, logger);
    }

    private static MappingConfig getMappingConfig() {
        final var config = ConfigFactory.parseString(
                "mapping {\n" +
                        "  javascript {\n" +
                        "    maxScriptSizeBytes = 50000 # 50kB\n" +
                        "    maxScriptExecutionTime = 60s\n" +
                        "    maxScriptStackDepth = 25\n" +
                        "    commonJsModulePath = \"./target/test-classes/unpacked-test-webjars\"\n" +
                        "  }\n" +
                        "}");
        return DefaultMappingConfig.of(config);
    }

    private static String getRacyInboundScript() {
        return "var $global = $global;\n" +
                "function sleep(sec) {\n" +
                "    var start = new Date();\n" +
                "    var now = new Date();\n" +
                "    while(now - start < sec*1000) now = new Date();\n" +
                "}\n" +
                "function mapToDittoProtocolMsg(headers,textPayload,bytePayload,contentType) {\n" +
                "    $global = textPayload;\n" +
                "    sleep(parseInt(textPayload, 10));\n" +
                "    let namespace = \"ns\";\n" +
                "    let group = \"things\";\n" +
                "    let channel = \"twin\";\n" +
                "    let criterion = \"commands\";\n" +
                "    let action = \"modify\";\n" +
                "    let path = \"/attributes/i\";\n" +
                "    let dittoHeaders = {};\n" +
                "    dittoHeaders[\"correlation-id\"] = textPayload;\n" +
                "    let value = textPayload;\n" +
                "    let name = $global;\n" +
                "    $global = textPayload;\n" +
                "    return Ditto.buildDittoProtocolMsg(namespace,name,group,channel,criterion,action,path," +
                "        dittoHeaders,value);\n" +
                "}";
    }

    private static String getRacyOutboundScript() {
        return "var $global = $global;\n" +
                "function sleep(sec) {\n" +
                "    var start = new Date();\n" +
                "    var now = new Date();\n" +
                "    while(now - start < sec*1000) now = new Date();\n" +
                "}\n" +
                "function mapFromDittoProtocolMsg(ns,i,g,ch,cr,a,p,h,value,s,e) {\n" +
                "    $global = i;\n" +
                "    sleep(parseInt(i, 10));\n" +
                "    let payload = $global;\n" +
                "    $global = i;\n" +
                "    return Ditto.buildExternalMsg(h, payload, null, 'text/plain');\n" +
                "}";
    }
}
