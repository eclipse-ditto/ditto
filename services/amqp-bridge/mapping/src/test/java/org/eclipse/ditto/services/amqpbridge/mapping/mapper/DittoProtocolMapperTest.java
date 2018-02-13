package org.eclipse.ditto.services.amqpbridge.mapping.mapper;


import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;

public class DittoProtocolMapperTest extends AbstractPayloadMapperTest {

    @Override
    protected PayloadMapper createMapper() {
        return new DittoProtocolMapper();
    }

    @Override
    protected List<String> createSupportedContentTypes() {
        return Arrays.asList(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
    }

    @Override
    protected List<PayloadMapperOptions> createValidOptions() {
        List<PayloadMapperOptions> options = new LinkedList<>();
        options.add(new ImmutablePayloadMapperOptions.Builder(Collections.emptyMap()).build());

        Arrays.asList("true", "false", null, "asdfjökla", "").forEach(s -> {
                    Map<String, String> map = Collections.singletonMap(DittoProtocolMapper
                            .OPTION_DISABLE_CONTENT_TYPE_CHECK, s);
                    options.add(new ImmutablePayloadMapperOptions.Builder(map).build());
                }
        );

        return options;
    }

    @Override
    protected Map<PayloadMapperOptions, Throwable> createInvalidOptions() {
        // there are none
        return Collections.emptyMap();
    }

    @Override
    protected PayloadMapperOptions createIncomingOptions() {
        return new ImmutablePayloadMapperOptions.Builder(Collections.emptyMap()).build();
    }

    @Override
    protected Map<PayloadMapperMessage, Adaptable> createValidIncomingMappings() {
        Map<PayloadMapperMessage, Adaptable> mappings = new HashMap<>();

        Map<String, String> headers = Collections.singletonMap("header-key", "header-value");

        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder("asd" +
                        ":jkl").things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());
        PayloadMapperMessage message = PayloadMappers.createPayloadMapperMessage(
            DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, adaptable.toJsonString(), headers);
        mappings.put(message, adaptable);

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();
        message = PayloadMappers.createPayloadMapperMessage(
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, json.toString(), headers);
        mappings.put(message, ProtocolFactory.jsonifiableAdaptableFromJson(json));

        return mappings;
    }

    @Override
    protected Map<PayloadMapperMessage, Throwable> createInvalidIncomingMappings() {
        Map<PayloadMapperMessage, Throwable> mappings = new HashMap<>();

        Map<String, String> headers = Collections.singletonMap("header-key", "header-value");

        PayloadMapperMessage message;
        message = PayloadMappers.createPayloadMapperMessage(
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, "", headers);
        mappings.put(message, new PayloadMappingException("Mapping failed",
                new IllegalArgumentException("The JSON string to create a JSON object from must not be empty!")));

        // --

        message = PayloadMappers.createPayloadMapperMessage(
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, "{}", headers);
        mappings.put(message, new PayloadMappingException("Mapping failed",
                new JsonMissingFieldException("/path")));

        // --

        message = PayloadMappers.createPayloadMapperMessage(
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, "no json", headers);
        mappings.put(message, new PayloadMappingException("Mapping failed",
                new JsonParseException("Failed to create JSON object from string!")));

        return mappings;
    }

    @Override
    protected PayloadMapperOptions createOutgoingOptions() {
        return new ImmutablePayloadMapperOptions.Builder(Collections.emptyMap()).build();
    }

    @Override
    protected Map<Adaptable, PayloadMapperMessage> createValidOutgoingMappings() {
        Map<Adaptable, PayloadMapperMessage> mappings = new HashMap<>();

        Map<String, String> headers = Collections.singletonMap("header-key", "header-value");

        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder("asd" +
                        ":jkl").things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());
        PayloadMapperMessage message = PayloadMappers.createPayloadMapperMessage(
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, adaptable.toJsonString(), headers);
        mappings.put(adaptable, message);

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();
        adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder(adaptable)
                .withHeaders(DittoHeaders.of(headers)).build());

        message = PayloadMappers.createPayloadMapperMessage(
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE, null, adaptable.toJsonString(), headers);
        mappings.put(adaptable, message);

        return mappings;
    }

    @Override
    protected Map<Adaptable, Throwable> createInvalidOutgoingMappings() {
        // adaptible is strongly typed and can always be jsonified, no invalid test needed.
        return Collections.emptyMap();
    }
}