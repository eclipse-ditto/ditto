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

package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.things.api.Permission.READ;
import static org.eclipse.ditto.things.api.Permission.WRITE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConflictException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import org.apache.pekko.actor.ActorSystem;

/**
 * Unit test for {@link ImplicitThingCreationMessageMapper}.
 */
public final class ImplicitThingCreationMessageMapperTest {

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static final String HEADER_HONO_DEVICE_ID = "device_id";
    private static final String HEADER_HONO_GATEWAY_ID = "gateway_id";

    private static final JsonObject THING_TEMPLATE = JsonObject.newBuilder()
            .set("thingId", "{{ header:device_id }}")
            .set("_copyPolicyFrom", "{{ header:gateway_id }}")
            .set("attributes", JsonObject.newBuilder()
                    .set("Info", JsonObject.newBuilder()
                            .set("gatewayId", "{{ header:gateway_id }}")
                            .build())
                    .build())
            .build();

    private static final JsonObject COMMAND_HEADERS = JsonObject.newBuilder()
            .set("test-header", "this-is-a-test-header")
            .set("other-test-header", "{{ header:gateway_id }}")
            .set("empty-test-header", "{{ header:gateway_id | fn:filter(header:foobar, 'eq', 'bar') }}")
            .build();
    
    private static final JsonValue ALLOW_POLICY_LOCKOUT = JsonValue.of(false);

    private static final JsonObject INITIAL_POLICY = Policy.newBuilder()
            .forLabel("DEFAULT")
            .setSubject(SubjectIssuer.INTEGRATION, "solutionId:connectionId")
            .setGrantedPermissions(PoliciesResourceType.policyResource("/"), READ, WRITE)
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ, WRITE)
            .setGrantedPermissions(PoliciesResourceType.messageResource("/"), READ, WRITE)
            .build()
            .toJson();

    private static final JsonObject THING_TEMPLATE_WITH_POLICY = JsonObject.newBuilder()
            .set("thingId", "{{ header:device_id }}")
            .set("policyId", "{{ header:device_id }}")
            .set("_policy", INITIAL_POLICY)
            .set("attributes", JsonObject.newBuilder()
                    .set("Info", JsonObject.newBuilder()
                            .set("gatewayId", "{{ header:gateway_id }}")
                            .build())
                    .build())
            .build();

    private static final JsonObject THING_TEMPLATE_WITHOUT_PLACEHOLDERS = JsonObject.newBuilder()
            .set("thingId", "some:validThingId!")
            .set("policyId", "some:validPolicyId!")
            .set("attributes", JsonObject.newBuilder()
                    .set("Info", JsonObject.newBuilder()
                            .set("gatewayId", "some:validGatewayId!")
                            .build())
                    .build())
            .build();

    public static final String GATEWAY_ID = "headerNamespace:headerGatewayId";
    public static final String DEVICE_ID = "headerNamespace:headerDeviceId";

    private Connection connection;
    private ActorSystem actorSystem;
    private MessageMapper underTest;

    @Before
    public void setUp() {
        connection = TestConstants.createConnection();
        actorSystem = ActorSystem.create("Test", TestConstants.CONFIG);
        underTest = new ImplicitThingCreationMessageMapper(actorSystem, Mockito.mock(Config.class));
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
    }

    @Test
    public void doForwardMappingContextWithCommandHeaderPlaceholder() {
        final Map<String, String> headers = createValidHeaders();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE, COMMAND_HEADERS, JsonValue.of(true)), actorSystem);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing = createExpectedThing(DEVICE_ID, DEVICE_ID, GATEWAY_ID);
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyId()).isEmpty();
        assertThat(createThing.getThing().getAttributes()).isEqualTo(expectedThing.getAttributes());
        assertThat(createThing.getDittoHeaders()).containsEntry("other-test-header", GATEWAY_ID);
        assertThat(createThing.getDittoHeaders()).containsEntry("test-header", "this-is-a-test-header");
        assertThat(createThing.getDittoHeaders().isAllowPolicyLockout()).isTrue();
        assertThat(createThing.getDittoHeaders().getIfNoneMatch()).contains(EntityTagMatchers.fromStrings("*"));
        assertThat(createThing.getPolicyIdOrPlaceholder()).contains(GATEWAY_ID);
    }

    @Test
    public void doForwardMappingContextWithDeviceIdPlaceholder() {
        final Map<String, String> headers = createValidHeaders();
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing = createExpectedThing(DEVICE_ID, DEVICE_ID, GATEWAY_ID);
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyId()).isEmpty();
        assertThat(createThing.getThing().getAttributes()).isEqualTo(expectedThing.getAttributes());
        assertThat(createThing.getPolicyIdOrPlaceholder()).contains(GATEWAY_ID);
        assertThat(createThing.getDittoHeaders().isAllowPolicyLockout()).isFalse();
        assertThat(createThing.getDittoHeaders().getIfNoneMatch()).contains(EntityTagMatchers.fromStrings("*"));
    }

    @Test
    public void doForwardMappingContextWithPolicyPlaceholder() {
        final Map<String, String> headers = createValidHeaders();
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE_WITH_POLICY, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing = createExpectedThing(DEVICE_ID, DEVICE_ID, GATEWAY_ID);
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyId()).isEqualTo(expectedThing.getPolicyId());
        assertThat(createThing.getThing().getAttributes()).isEqualTo(expectedThing.getAttributes());
        assertThat(createThing.getInitialPolicy()).contains(INITIAL_POLICY);
        assertThat(createThing.getDittoHeaders().isAllowPolicyLockout()).isFalse();
        assertThat(createThing.getDittoHeaders().getIfNoneMatch()).contains(EntityTagMatchers.fromStrings("*"));
    }

    @Test
    public void doForwardMappingTwice() {
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE_WITH_POLICY, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final Map<String, String> headers1 = new HashMap<>();
        headers1.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId1");
        headers1.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage1 = ExternalMessageFactory.newExternalMessageBuilder(headers1).build();
        final List<Adaptable> mappingResult1 = underTest.map(externalMessage1);

        final Signal<?> firstMappedSignal1 = getFirstMappedSignal(mappingResult1);
        assertThat(firstMappedSignal1).isInstanceOf(CreateThing.class);
        final CreateThing createThing1 = (CreateThing) firstMappedSignal1;

        final Thing expectedThing1 =
                createExpectedThing("headerNamespace:headerDeviceId1", "headerNamespace:headerDeviceId1",
                        GATEWAY_ID);
        assertThat(createThing1.getThing().getEntityId()).isEqualTo(expectedThing1.getEntityId());
        assertThat(createThing1.getThing().getPolicyId()).isEqualTo(expectedThing1.getPolicyId());
        assertThat(createThing1.getThing().getAttributes()).isEqualTo(expectedThing1.getAttributes());
        assertThat(createThing1.getInitialPolicy()).contains(INITIAL_POLICY);


        final Map<String, String> headers2 = new HashMap<>();
        headers2.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId2");
        headers2.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage2 = ExternalMessageFactory.newExternalMessageBuilder(headers2).build();
        final List<Adaptable> mappingResult2 = underTest.map(externalMessage2);

        final Signal<?> firstMappedSignal2 = getFirstMappedSignal(mappingResult2);
        assertThat(firstMappedSignal2).isInstanceOf(CreateThing.class);
        final CreateThing createThing2 = (CreateThing) firstMappedSignal2;

        final Thing expectedThing2 =
                createExpectedThing("headerNamespace:headerDeviceId2", "headerNamespace:headerDeviceId2",
                        GATEWAY_ID);
        assertThat(createThing2.getThing().getEntityId()).isEqualTo(expectedThing2.getEntityId());
        assertThat(createThing2.getThing().getPolicyId()).isEqualTo(expectedThing2.getPolicyId());
        assertThat(createThing2.getThing().getAttributes()).isEqualTo(expectedThing2.getAttributes());
        assertThat(createThing2.getInitialPolicy()).contains(INITIAL_POLICY);
    }

    @Test
    public void doForwardWithoutPlaceholders() {
        final Map<String, String> headers = createValidHeaders();
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE_WITHOUT_PLACEHOLDERS, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        final Thing expectedThing =
                createExpectedThing("some:validThingId!", "some:validPolicyId!", "some:validGatewayId!");
        assertThat(createThing.getThing().getEntityId()).isEqualTo(expectedThing.getEntityId());
        assertThat(createThing.getThing().getPolicyId()).isEqualTo(expectedThing.getPolicyId());
    }

    @Test
    public void maliciousHeaderCannotInjectInlinePolicyIntoResolvedTemplate() {
        // An admin configures a template where an attacker-controllable header is reflected into a (non-id) string
        // value. thingId and policyId are fixed by the admin.
        final JsonObject template = JsonObject.newBuilder()
                .set("thingId", "ns:fixedThing")
                .set("policyId", "ns:adminpolicy")
                .set("attributes", JsonObject.newBuilder()
                        .set("manufacturer", "{{ header:device_id }}")
                        .build())
                .build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(template, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        // Attacker breaks out of the JSON string context to try to inject a top-level inline "_policy" and hijack
        // the twin, overriding the admin-configured "ns:adminpolicy".
        final String maliciousDeviceId =
                "x\"}, \"_policy\": {\"policyId\": \"attacker:policy\"}, \"attributes\": {\"y\": \"z";
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_HONO_DEVICE_ID, maliciousDeviceId);
        headers.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        // no inline policy leaked in - the admin policyId is retained
        assertThat(createThing.getInitialPolicy()).isEmpty();
        assertThat(createThing.getThing().getPolicyId().map(String::valueOf)).contains("ns:adminpolicy");
        assertThat(createThing.getThing().getEntityId().map(String::valueOf)).contains("ns:fixedThing");
        // the malicious payload is confined verbatim to the single string value it was resolved into
        assertThat(createThing.getThing()
                .getAttributes()
                .flatMap(attributes -> attributes.getValue("manufacturer"))
                .map(JsonValue::asString))
                .contains(maliciousDeviceId);
    }

    @Test
    public void maliciousHeaderInThingIdCannotInjectInlinePolicy() {
        // When the injection target is the thingId itself, the broken-out payload stays confined to the thingId
        // string value and cannot introduce a structural top-level inline "_policy".
        final JsonObject template = JsonObject.newBuilder()
                .set("thingId", "{{ header:device_id }}")
                .set("policyId", "ns:adminpolicy")
                .build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(template, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final String maliciousDeviceId = "ns:evil\", \"_policy\": {\"policyId\": \"attacker:policy\"}, \"z\": \"a";
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_HONO_DEVICE_ID, maliciousDeviceId);
        headers.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Signal<?> firstMappedSignal = getFirstMappedSignal(mappingResult);
        assertThat(firstMappedSignal).isInstanceOf(CreateThing.class);
        final CreateThing createThing = (CreateThing) firstMappedSignal;

        // no inline policy leaked in; the whole payload stayed inside the thingId string value
        assertThat(createThing.getInitialPolicy()).isEmpty();
        assertThat(createThing.getThing().getPolicyId().map(String::valueOf)).contains("ns:adminpolicy");
        assertThat(createThing.getThing().getEntityId().map(String::valueOf)).contains(maliciousDeviceId);
    }

    @Test
    public void maliciousHeaderInArrayElementCannotInjectInlinePolicy() {
        // Placeholder reflected into a string element of a JSON array (exercises the array-resolution branch).
        final JsonObject template = JsonObject.newBuilder()
                .set("thingId", "ns:fixedThing")
                .set("policyId", "ns:adminpolicy")
                .set("attributes", JsonObject.newBuilder()
                        .set("tags", JsonArray.of(JsonValue.of("{{ header:device_id }}")))
                        .build())
                .build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(template, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final String maliciousDeviceId =
                "x\"], \"_policy\": {\"policyId\": \"attacker:policy\"}, \"attributes\": {\"tags\": [\"y";
        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_HONO_DEVICE_ID, maliciousDeviceId);
        headers.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final CreateThing createThing = (CreateThing) getFirstMappedSignal(underTest.map(externalMessage));

        assertThat(createThing.getInitialPolicy()).isEmpty();
        assertThat(createThing.getThing().getPolicyId().map(String::valueOf)).contains("ns:adminpolicy");
        final Optional<JsonValue> firstTag = createThing.getThing()
                .getAttributes()
                .flatMap(attributes -> attributes.getValue("tags"))
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .flatMap(array -> array.stream().findFirst());
        assertThat(firstTag.map(JsonValue::asString)).contains(maliciousDeviceId);
    }

    @Test
    public void maliciousHeaderCannotInjectCopyPolicyFrom() {
        final JsonObject template = JsonObject.newBuilder()
                .set("thingId", "ns:fixedThing")
                .set("policyId", "ns:adminpolicy")
                .set("attributes", JsonObject.newBuilder()
                        .set("manufacturer", "{{ header:device_id }}")
                        .build())
                .build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(template, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_HONO_DEVICE_ID,
                "x\"}, \"_copyPolicyFrom\": \"attacker:sourcePolicy\", \"attributes\": {\"y\": \"z");
        headers.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final CreateThing createThing = (CreateThing) getFirstMappedSignal(underTest.map(externalMessage));

        // no _copyPolicyFrom leaked in - the admin policyId is retained
        assertThat(createThing.getPolicyIdOrPlaceholder()).isEmpty();
        assertThat(createThing.getThing().getPolicyId().map(String::valueOf)).contains("ns:adminpolicy");
    }

    @Test
    public void placeholderInInlinePolicySubjectKeyIsResolved() {
        // A legitimate placeholder used as a JSON key (an inline policy subject id) must still resolve - this is a
        // common way to provision a per-device/-gateway policy subject.
        final JsonObject policyWithPlaceholderSubject = JsonObject.newBuilder()
                .set("entries", JsonObject.newBuilder()
                        .set("DEFAULT", JsonObject.newBuilder()
                                .set("subjects", JsonObject.newBuilder()
                                        .set("integration:{{ header:gateway_id }}", JsonObject.newBuilder()
                                                .set("type", "suite-auth")
                                                .build())
                                        .build())
                                .set("resources", JsonObject.newBuilder()
                                        .set("thing:/", JsonObject.newBuilder()
                                                .set("grant", JsonArray.of(JsonValue.of("READ"), JsonValue.of("WRITE")))
                                                .set("revoke", JsonArray.empty())
                                                .build())
                                        .set("policy:/", JsonObject.newBuilder()
                                                .set("grant", JsonArray.of(JsonValue.of("READ"), JsonValue.of("WRITE")))
                                                .set("revoke", JsonArray.empty())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject template = JsonObject.newBuilder()
                .set("thingId", "{{ header:device_id }}")
                .set("policyId", "{{ header:device_id }}")
                .set("_policy", policyWithPlaceholderSubject)
                .build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(template, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(createValidHeaders()).build();
        final CreateThing createThing = (CreateThing) getFirstMappedSignal(underTest.map(externalMessage));

        final JsonObject initialPolicy = createThing.getInitialPolicy().orElseThrow();
        final String subjectKey = initialPolicy.getValue("entries")
                .map(JsonValue::asObject)
                .flatMap(entries -> entries.getValue("DEFAULT"))
                .map(JsonValue::asObject)
                .flatMap(defaultEntry -> defaultEntry.getValue("subjects"))
                .map(JsonValue::asObject)
                .map(subjects -> subjects.stream().findFirst().orElseThrow().getKeyName())
                .orElseThrow();
        assertThat(subjectKey).isEqualTo("integration:" + GATEWAY_ID);
    }

    @Test
    public void commandHeadersDoNotLeakAcrossMessages() {
        // A single mapper instance is pooled and reused for the whole connection lifetime. Resolving a header
        // placeholder must not overwrite the configured template, otherwise a later message would receive an
        // earlier message's resolved value (cross-message metadata contamination).
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final Map<String, String> headers1 = new HashMap<>();
        headers1.put(HEADER_HONO_DEVICE_ID, DEVICE_ID);
        headers1.put(HEADER_HONO_GATEWAY_ID, "headerNamespace:gatewayOne");
        final CreateThing createThing1 = (CreateThing) getFirstMappedSignal(
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers1).build()));

        final Map<String, String> headers2 = new HashMap<>();
        headers2.put(HEADER_HONO_DEVICE_ID, DEVICE_ID);
        headers2.put(HEADER_HONO_GATEWAY_ID, "headerNamespace:gatewayTwo");
        final CreateThing createThing2 = (CreateThing) getFirstMappedSignal(
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers2).build()));

        // "other-test-header" is configured as "{{ header:gateway_id }}"; each message must get its own value
        assertThat(createThing1.getDittoHeaders()).containsEntry("other-test-header", "headerNamespace:gatewayOne");
        assertThat(createThing2.getDittoHeaders()).containsEntry("other-test-header", "headerNamespace:gatewayTwo");
    }

    @Test
    public void resolvedRootKeyCannotIntroduceReservedCopyPolicyFrom() {
        // A placeholder used as a root-level key must NOT be resolved into a reserved structural key such as
        // "_copyPolicyFrom" - root keys are the mapper's structural directives and stay literal.
        final JsonObject template = JsonObject.newBuilder()
                .set("thingId", "ns:fixedThing")
                .set("policyId", "ns:adminpolicy")
                .set("{{ header:device_id }}", "attacker:sourcePolicy")
                .build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(template, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_HONO_DEVICE_ID, "_copyPolicyFrom");
        headers.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();
        final CreateThing createThing = (CreateThing) getFirstMappedSignal(underTest.map(externalMessage));

        // the root key placeholder was not resolved, so no "_copyPolicyFrom" directive was introduced
        assertThat(createThing.getPolicyIdOrPlaceholder()).isEmpty();
        assertThat(createThing.getInitialPolicy()).isEmpty();
        assertThat(createThing.getThing().getPolicyId().map(String::valueOf)).contains("ns:adminpolicy");
    }

    @Test
    public void throwErrorIfMappingConfigIsMissing() {
        final DefaultMessageMapperConfiguration invalidMapperConfig = createMapperConfig(JsonObject.empty(),
                COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT);

        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class)
                .isThrownBy(
                        () -> underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG, invalidMapperConfig,
                                actorSystem));
    }

    @Test
    public void throwErrorIfThingIdIsMissingInConfig() {
        final JsonObject templateMissingThingId = JsonObject.newBuilder()
                .set("policyId", "{{ header:device_id }}")
                .set("_policy", INITIAL_POLICY)
                .set("attributes", JsonObject.newBuilder()
                        .set("Info", JsonObject.newBuilder()
                                .set("gatewayId", "{{ header:gateway_id }}")
                                .build())
                        .build())
                .build();

        final DefaultMessageMapperConfiguration invalidMapperConfig = createMapperConfig(templateMissingThingId,
                COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT);

        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class)
                .isThrownBy(
                        () -> underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG, invalidMapperConfig,
                                actorSystem));
    }

    @Test
    public void throwErrorIfHeaderForPlaceholderIsMissing() {
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);

        final Map<String, String> missingEntityHeader = new HashMap<>();
        missingEntityHeader.put(HEADER_HONO_DEVICE_ID, DEVICE_ID);

        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(missingEntityHeader).build();

        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.map(externalMessage));
    }

    @Test
    public void throwExceptionOnErrorResponse() {
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);
        final ThingConflictException conflictException =
                ThingConflictException.newBuilder(ThingId.generateRandom()).build();
        final Signal<?> thingErrorResponse = ThingErrorResponse.of(conflictException);
        final Adaptable errorAdaptable = DittoProtocolAdapter.newInstance().toAdaptable(thingErrorResponse);
        assertThatExceptionOfType(ThingConflictException.class)
                .isThrownBy(() -> underTest.map(errorAdaptable));
    }

    @Test
    public void throwErrorIfThingIdIsInvalidAfterPlaceholderResolution() {
        final JsonObject templateInvalidThingId = JsonObject.newBuilder()
                .set("thingId", "{{header:id}}")
                .build();

        final DefaultMessageMapperConfiguration mapperConfig =
                createMapperConfig(templateInvalidThingId, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT);

        final var externalMessage = ExternalMessageFactory.newExternalMessageBuilder(Map.of("id", "invalid")).build();

        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG, mapperConfig, actorSystem);

        assertThatExceptionOfType(ThingIdInvalidException.class).isThrownBy(() -> underTest.map(externalMessage));
    }

    @Test
    public void regularCommandResponsesAreNotMapped() {
        underTest.configure(connection, TestConstants.CONNECTIVITY_CONFIG,
                createMapperConfig(THING_TEMPLATE, COMMAND_HEADERS, ALLOW_POLICY_LOCKOUT), actorSystem);
        final Signal<?> thingResponse =
                CreateThingResponse.of(Thing.newBuilder().setId(ThingId.generateRandom()).build(),
                        DittoHeaders.empty());
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(thingResponse);
        assertThat(underTest.map(adaptable)).isEmpty();
    }

    private static Signal<?> getFirstMappedSignal(final List<Adaptable> mappingResult) {
        return mappingResult.stream().findFirst()
                .map(DITTO_PROTOCOL_ADAPTER::fromAdaptable)
                .orElseGet(() -> fail("Mapping Result did not contain a Signal."));
    }

    private Map<String, String> createValidHeaders() {
        final Map<String, String> validHeader = new HashMap<>();
        validHeader.put(HEADER_HONO_DEVICE_ID, DEVICE_ID);
        validHeader.put(HEADER_HONO_GATEWAY_ID, GATEWAY_ID);
        return validHeader;
    }

    private Thing createExpectedThing(final String thingId, final String policyId, final String gatewayId) {
        return ThingsModelFactory.newThing("{" +
                "\"thingId\": \"" + thingId + "\"," +
                "\"policyId\": \"" + policyId + "\"," +
                "\"attributes\": {" +
                "\"Info\": {" +
                "\"gatewayId\": \"" + gatewayId + "\"" +
                "}" +
                "}" +
                "}");
    }

    private DefaultMessageMapperConfiguration createMapperConfig(final JsonValue thingTemplate,
            final JsonValue commandHeaders, final JsonValue allowPolicyLockout) {
        final Map<String, JsonValue> options = new HashMap<>();
        options.put("thing", thingTemplate);
        options.put("commandHeaders", commandHeaders);
        options.put("allowPolicyLockout", allowPolicyLockout);
        final Map<String, String> incomingConditions = Collections.singletonMap("implicitThingCreation",
                "{{ header:hono_registration_status | fn:filter(header:hono_registration_status,'eq','NEW') }}");
        return DefaultMessageMapperConfiguration.of("valid", options, incomingConditions, Collections.emptyMap());
    }

}
