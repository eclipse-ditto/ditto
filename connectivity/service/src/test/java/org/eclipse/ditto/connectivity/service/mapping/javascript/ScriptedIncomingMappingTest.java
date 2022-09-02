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

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.junit.Test;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

public class ScriptedIncomingMappingTest {

    private static final String PAYLOAD = "payload";
    private static final byte[] BYTES = PAYLOAD.getBytes();

    @Test
    public void mapExternalMessage() {
        mapExternalMessage(ByteBuffer.wrap(BYTES));
    }

    @Test
    public void mapExternalMessageFromReadOnlyBuffer() {
        mapExternalMessage(ByteBuffer.wrap(BYTES).asReadOnlyBuffer());
    }

    @Test
    public void mapExternalMessageTextAndBytes() {
        final ExternalMessage externalMessage = ExternalMessageFactory
                .newExternalMessageBuilder(new HashMap<>())
                .withTextAndBytes(PAYLOAD, ByteBuffer.wrap(BYTES))
                .build();

        final NativeObject nativeObject = ScriptedIncomingMapping.mapExternalMessageToNativeObject(externalMessage);

        final String textPayload = (String) nativeObject.get("textPayload");
        final NativeArrayBuffer bytePayload = (NativeArrayBuffer) nativeObject.get("bytePayload");
        assertThat(textPayload).isEqualTo(PAYLOAD);
        assertThat(bytePayload.getBuffer()).isEqualTo(BYTES);
    }

    private void mapExternalMessage(final ByteBuffer source) {
        final ExternalMessage externalMessage = ExternalMessageFactory
                .newExternalMessageBuilder(new HashMap<>())
                .withBytes(source)
                .build();

        final NativeObject nativeObject = ScriptedIncomingMapping.mapExternalMessageToNativeObject(externalMessage);

        final NativeArrayBuffer bytePayload = (NativeArrayBuffer) nativeObject.get("bytePayload");
        assertThat(bytePayload.getBuffer()).isEqualTo(BYTES);
    }

    @Test
    public void mapExternalMessageProducingArrayOfDittoProtocolMessages() {

        testJavascript("function createDittoCommand(namespace, name, headers, value) {\n" +
                        "    var path = \"/attributes/test\";\n" +
                        "    return Ditto.buildDittoProtocolMsg(\n" +
                        "        namespace,\n" +
                        "        name,\n" +
                        "        \"things\",\n" +
                        "        \"twin\",\n" +
                        "        \"commands\",\n" +
                        "        \"modify\",\n" +
                        "        path,\n" +
                        "        headers,\n" +
                        "        value\n" +
                        "    );\n" +
                        "}\n" +
                        "\n" +
                        "function mapToDittoProtocolMsg(\n" +
                        "  headers,\n" +
                        "  textPayload,\n" +
                        "  bytePayload,\n" +
                        "  contentType\n" +
                        ") {\n" +
                        "  var namespace = 'org.eclipse.ditto';\n" +
                        "  var name = 'thing-1';\n" +
                        "\n" +
                        "  var dittoCommands = [createDittoCommand(namespace, name, headers, textPayload)];\n" +
                        "  dittoCommands = dittoCommands.concat(createDittoCommand(namespace, name, headers, textPayload + \"1\"));\n" +
                        "  return dittoCommands;\n" +
                        "}\n",
                adaptables -> {
                    assertThat(adaptables).isNotEmpty();
                    assertThat(adaptables).hasSize(2);
                    final Adaptable adaptable0 = adaptables.get(0);
                    assertThat(adaptable0.getPayload().getValue()).contains(JsonValue.of(PAYLOAD));
                    final Adaptable adaptable1 = adaptables.get(1);
                    assertThat(adaptable1.getPayload().getValue()).contains(JsonValue.of(PAYLOAD + "1"));
                });
    }

    private void testJavascript(final String scriptToTest, final Consumer<List<Adaptable>> mappedAdaptables) {
        final SandboxingContextFactory contextFactory = new SandboxingContextFactory(Duration.ofMillis(500), 10);
        contextFactory.call(cx -> {
            final Scriptable scope = cx.initSafeStandardObjects(); // that one disables "print, exit, quit", etc.
            JavaScriptMessageMapperRhino.loadJavascriptLibrary(cx, scope, new InputStreamReader(
                            getClass().getResourceAsStream(JavaScriptMessageMapperRhino.DITTO_SCOPE_SCRIPT)),
                    JavaScriptMessageMapperRhino.DITTO_SCOPE_SCRIPT);
            JavaScriptMessageMapperRhino.loadJavascriptLibrary(cx, scope,
                    new InputStreamReader(getClass().getResourceAsStream(JavaScriptMessageMapperRhino.INCOMING_SCRIPT)),
                    JavaScriptMessageMapperRhino.INCOMING_SCRIPT);
            JavaScriptMessageMapperRhino.loadJavascriptLibrary(cx, scope,
                    new InputStreamReader(getClass().getResourceAsStream(JavaScriptMessageMapperRhino.OUTGOING_SCRIPT)),
                    JavaScriptMessageMapperRhino.OUTGOING_SCRIPT);

            final ScriptedIncomingMapping incomingMapping = new ScriptedIncomingMapping(contextFactory, scope);
            cx.evaluateString(scope, scriptToTest,
                    JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT, 1, null);

            final ExternalMessage externalMessage = ExternalMessageFactory
                    .newExternalMessageBuilder(new HashMap<>())
                    .withText(PAYLOAD)
                    .build();
            final List<Adaptable> adaptables = incomingMapping.apply(externalMessage);
            mappedAdaptables.accept(adaptables);

            return scope;
        });
    }
}
