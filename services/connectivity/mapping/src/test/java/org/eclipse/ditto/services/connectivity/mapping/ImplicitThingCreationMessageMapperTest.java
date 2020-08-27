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

package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.services.models.things.Permission.READ;
import static org.eclipse.ditto.services.models.things.Permission.WRITE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.mapping.ImplicitThingCreationMessageMapper}.
 */
public class ImplicitThingCreationMessageMapperTest {

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static final String HEADER_HONO_DEVICE_ID = "device_id";
    private static final String HEADER_HONO_GATEWAY_ID = "gateway_id";

    private static final String THING_TEMPLATE = "{" +
            "\"thingId\": \"{{ header:device_id }}\"," +
            "\"attributes\": {" +
            "\"info\": {" +
            "\"gatewayId\": \"{{ header:gateway_id }}\"" +
            "}" +
            "}" +
            "}";

    private static final JsonObject INITIAL_POLICY = Policy.newBuilder()
            .forLabel("DEFAULT")
            .setSubject(SubjectIssuer.INTEGRATION, "solutionId:connectionId")
            .setGrantedPermissions(PoliciesResourceType.policyResource("/"), READ, WRITE)
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ, WRITE)
            .setGrantedPermissions(PoliciesResourceType.messageResource("/"), READ, WRITE)
            .build()
            .toJson();

    private static final String THING_TEMPLATE_WITH_POLICY = JsonObject.newBuilder()
            .set("thingId", "{{ header:device_id }}")
            .set("policyId", "{{ header:device_id }}")
            .set("_policy", INITIAL_POLICY)
            .set("attributes", JsonObject.newBuilder()
                    .set("info", JsonObject.newBuilder()
                            .set("gatewayId", "{{ header:gateway_id }}")
                            .build())
                    .build())
            .build()
            .toString();

    private static final String THING_TEMPLATE_WITHOUT_PLACEHOLDERS = "{" +
            "\"thingId\": \"some:validThingId!\"," +
            "\"policyId\": \"some:validPolicyId!\"," +
            "\"attributes\": {" +
            "\"info\": {" +
            "\"gatewayId\": \"some:validGatewayId!\"" +
            "}" +
            "}" +
            "}";

    private static MappingConfig mappingConfig;
    private MessageMapper underTest;

    @Before
    public void setUp() {
        mappingConfig = DefaultMappingConfig.of(ConfigFactory.empty());
        underTest = new ImplicitThingCreationMessageMapper();
    }

    @Test
    public void doForwardMappingContextWithDeviceIdPlaceholder() {
        final Map<String, String> headers = createValidHeaders();
        underTest.configure(mappingConfig, createMapperConfig(THING_TEMPLATE));

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing =
                createExpectedThing("headerNamespace:headerDeviceId", "headerNamespace:headerDeviceId",
                        "headerNamespace:headerGatewayId");
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyEntityId()).isEmpty();
        assertThat(createThing.getThing().getAttributes()).isEqualTo(expectedThing.getAttributes());
    }

    @Test
    public void doForwardMappingContextWithPolicyPlaceholder() {
        final Map<String, String> headers = createValidHeaders();
        underTest.configure(mappingConfig, createMapperConfig(THING_TEMPLATE_WITH_POLICY));

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing =
                createExpectedThing("headerNamespace:headerDeviceId", "headerNamespace:headerDeviceId",
                        "headerNamespace:headerGatewayId");
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyEntityId()).isEqualTo(expectedThing.getPolicyEntityId());
        assertThat(createThing.getThing().getAttributes()).isEqualTo(expectedThing.getAttributes());
        assertThat(createThing.getInitialPolicy()).contains(INITIAL_POLICY);
    }

    @Test
    public void doForwardWithoutPlaceholders() {
        final Map<String, String> headers = createValidHeaders();
        underTest.configure(mappingConfig, createMapperConfig(THING_TEMPLATE_WITHOUT_PLACEHOLDERS));

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing =
                createExpectedThing("some:validThingId!", "some:validPolicyId!", "some:validGatewayId!");
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyEntityId()).isEqualTo(expectedThing.getPolicyEntityId());
    }

    @Test
    public void throwErrorIfMappingConfigIsMissing() {
        final DefaultMessageMapperConfiguration invalidMapperConfig = createMapperConfig("{}");

        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.configure(mappingConfig, invalidMapperConfig));
    }

    @Test
    public void throwErrorIfThingIdIsMissingInConfig() {
        final String thingMissing = "{" +
                "\"policyId\": \"{{ header:policy_id }}\"" +
                "}";

        final DefaultMessageMapperConfiguration invalidMapperConfig = createMapperConfig(thingMissing);

        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.configure(mappingConfig, invalidMapperConfig));
    }

    @Test
    public void throwErrorIfHeaderForPlaceholderIsMissing() {
        underTest.configure(mappingConfig, createMapperConfig(THING_TEMPLATE));

        final Map<String, String> missingEntityHeader = new HashMap<>();
        missingEntityHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");

        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(missingEntityHeader).build();

        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.map(externalMessage));
    }

    private static Signal<?> getFirstMappedSignal(final List<Adaptable> mappingResult) {
        return mappingResult.stream().findFirst()
                .map(DITTO_PROTOCOL_ADAPTER::fromAdaptable)
                .orElseGet(() -> fail("Mapping Result did not contain a Signal."));
    }

    private Map<String, String> createValidHeaders() {
        final Map<String, String> validHeader = new HashMap<>();
        validHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(HEADER_HONO_GATEWAY_ID, "headerNamespace:headerGatewayId");
        return validHeader;
    }

    private Thing createExpectedThing(final String thingId, final String policyId, final String gatewayId) {
        return ThingsModelFactory.newThing("{" +
                "\"thingId\": \"" + thingId + "\"," +
                "\"policyId\": \"" + policyId + "\"," +
                "\"attributes\": {" +
                "\"info\": {" +
                "\"gatewayId\": \"" + gatewayId + "\"" +
                "}" +
                "}" +
                "}");
    }

    private DefaultMessageMapperConfiguration createMapperConfig(final String thingTemplate) {
        final Map<String, String> options = Collections.singletonMap("thing", thingTemplate);
        final Map<String, String> conditions = Collections.singletonMap("implicitThingCreation",
                "{{ header:hono_registration_status | fn:filter(header:hono_registration_status,'eq','NEW') }}");
        return DefaultMessageMapperConfiguration.of("valid", options, conditions);
    }

}
