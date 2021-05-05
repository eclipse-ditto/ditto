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
package org.eclipse.ditto.protocol;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 *
 */
public final class TestConstants {

    public static final Set<String> NAMESPACES =
            new HashSet<>(Arrays.asList("org.eclipse.ditto.test", "org.eclipse.ditto.footest"));
    public static final String NAMESPACE = "org.eclipse.ditto.test";

    public static final String NAME = "myThing";
    public static final String NAME2 = "myThing2";

    public static final String CORRELATION_ID = "dittoCorrelationId";

    public static final long REVISION = 1337;
    public static final Instant TIMESTAMP = Instant.EPOCH;
    public static final Metadata METADATA = Metadata.newBuilder()
            .set("creator", "The epic Ditto team")
            .build();

    public static final Instant CREATED = Instant.now().minus(Duration.ofDays(100));
    public static final Instant MODIFIED = Instant.now().minus(Duration.ofDays(50));

    public static final String THING_PREFIX = "thing:";
    public static final ThingId THING_ID = ThingId.of(NAMESPACE, NAME);
    public static final ThingId THING_ID2 = ThingId.of(NAMESPACE, NAME2);

    public static final String POLICY_NAME = "myPolicy";
    public static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, POLICY_NAME);

    public static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance("sid");

    public static final JsonValue ATTRIBUTE_VALUE = JsonValue.of("bar");

    public static final ThingDefinition THING_DEFINITION = ThingsModelFactory.newDefinition("example:test:definition");

    public static final JsonValue JSON_THING_DEFINITION = JsonValue.of(THING_DEFINITION);

    public static final String FEATURE_ID = "fluxCompensator";

    public static final String SUBJECT = "message:subject";

    public static final JsonPointer THING_POINTER = JsonPointer.of("/");
    public static final JsonPointer POLICY_ID_POINTER = JsonPointer.of("/policyId");
    public static final JsonPointer THING_DEFINITION_POINTER = JsonPointer.of("/definition");
    public static final JsonPointer THING_ATTRIBUTES_POINTER = JsonPointer.of("/attributes");
    public static final JsonPointer FEATURES_POINTER = JsonPointer.of("/features");

    public static final JsonPointer FEATURE_PROPERTY_POINTER = JsonPointer.of("/baz");
    public static final JsonPointer FEATURE_DESIRED_PROPERTY_POINTER = JsonPointer.of("/bar");

    public static final JsonPointer ATTRIBUTE_POINTER = JsonPointer.of("/foo");
    public static final JsonPointer THING_ATTRIBUTE_POINTER =
            THING_ATTRIBUTES_POINTER.append(ATTRIBUTE_POINTER);
    public static final JsonPointer FEATURE_POINTER = JsonPointer.of(FEATURES_POINTER + "/" + FEATURE_ID);
    public static final JsonPointer FEATURE_PROPERTIES_POINTER = JsonPointer.of(FEATURES_POINTER + "/" + FEATURE_ID +
            "/properties");
    public static final JsonPointer FEATURE_DEFINITION_POINTER = JsonPointer.of(FEATURES_POINTER + "/" + FEATURE_ID +
            "/definition");
    public static final JsonPointer FEATURE_DESIRED_PROPERTIES_POINTER =
            JsonPointer.of(FEATURES_POINTER + "/" + FEATURE_ID +
                    "/desiredProperties");
    public static final JsonPointer FEATURE_PROPERTY_POINTER_ABSOLUTE =
            JsonPointer.of(FEATURES_POINTER + "/" + FEATURE_ID + "/properties" + FEATURE_PROPERTY_POINTER);
    public static final JsonPointer FEATURE_DESIRED_PROPERTIES_POINTER_ABSOLUTE =
            JsonPointer.of(
                    FEATURES_POINTER + "/" + FEATURE_ID + "/desiredProperties" + FEATURE_DESIRED_PROPERTY_POINTER);
    public static final TopicPath TOPIC_PATH_MERGE_THING =
            TopicPath.newBuilder(TestConstants.THING_ID).things().twin().commands().merge().build();

    public static final JsonObject ATTRIBUTES_JSON =
            JsonObject.newBuilder().set(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE).build();
    public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributes(ATTRIBUTES_JSON);

    public static final JsonValue FEATURE_PROPERTY_VALUE = JsonValue.of(42);
    public static final JsonValue FEATURE_DESIRED_PROPERTY_VALUE = JsonValue.of(41);

    public static final JsonObject FEATURE_PROPERTIES_JSON =
            JsonObject.newBuilder().set(FEATURE_PROPERTY_POINTER, FEATURE_PROPERTY_VALUE).build();
    public static final JsonObject FEATURE_DESIRED_PROPERTIES_JSON =
            JsonObject.newBuilder().set(FEATURE_DESIRED_PROPERTY_POINTER, FEATURE_DESIRED_PROPERTY_VALUE).build();

    public static final FeatureProperties FEATURE_PROPERTIES =
            ThingsModelFactory.newFeatureProperties(FEATURE_PROPERTIES_JSON);
    public static final FeatureProperties FEATURE_DESIRED_PROPERTIES =
            ThingsModelFactory.newFeatureProperties(FEATURE_DESIRED_PROPERTIES_JSON);

    public static final FeatureDefinition FEATURE_DEFINITION =
            FeatureDefinition.fromIdentifier("org.eclipse.ditto:foo:1.0.0");

    public static final JsonArray FEATURE_DEFINITION_JSON = FEATURE_DEFINITION.toJson();

    public static final Feature FEATURE =
            Feature.newBuilder()
                    .properties(FEATURE_PROPERTIES)
                    .desiredProperties(FEATURE_DESIRED_PROPERTIES)
                    .withId(FEATURE_ID)
                    .build();

    public static final Features FEATURES = Features.newBuilder().set(FEATURE).build();

    public static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .setAttributes(ATTRIBUTES)
            .setDefinition(THING_DEFINITION)
            .setFeatures(FEATURES)
            .setLifecycle(ThingLifecycle.ACTIVE)
            .setPolicyId(POLICY_ID)
            .setRevision(REVISION)
            .setModified(MODIFIED)
            .setCreated(CREATED)
            .build();

    public static final Thing THING2 = Thing.newBuilder().setId(THING_ID2).build();

    public static final DittoHeaders DITTO_HEADERS_V_2 = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .schemaVersion(JsonSchemaVersion.V_2)
            .putHeader(MessageHeaderDefinition.STATUS_CODE.getKey(), "200")
            .build();

    public static final DittoHeaders DITTO_HEADERS_V_2_NO_STATUS = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .schemaVersion(JsonSchemaVersion.V_2)
            .build();

    public static final DittoHeaders HEADERS_V_2 = ProtocolFactory.newHeadersWithDittoContentType(DITTO_HEADERS_V_2);

    public static final DittoHeaders HEADERS_V_2_FOR_MERGE_COMMANDS =
            ProtocolFactory.newHeadersWithJsonMergePatchContentType(DITTO_HEADERS_V_2);

    public static final DittoHeaders HEADERS_V_2_NO_CONTENT_TYPE = DittoHeaders.newBuilder(HEADERS_V_2).removeHeader(
            DittoHeaderDefinition.CONTENT_TYPE.getKey()).build();

    public static final List<JsonPointer> THING_POINTERS = Arrays.asList(
            JsonPointer.empty(),
            TestConstants.POLICY_ID_POINTER,
            TestConstants.THING_ATTRIBUTES_POINTER,
            TestConstants.THING_DEFINITION_POINTER,
            TestConstants.FEATURES_POINTER,
            TestConstants.FEATURE_PROPERTIES_POINTER,
            TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER,
            TestConstants.FEATURE_DEFINITION_POINTER,
            TestConstants.FEATURE_PROPERTY_POINTER_ABSOLUTE,
            TestConstants.FEATURE_DESIRED_PROPERTIES_POINTER_ABSOLUTE,
            TestConstants.THING_ATTRIBUTE_POINTER
    );

    public static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path) {
        return adaptable(topicPath, path, null, null);
    }

    public static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path, final JsonValue value) {
        return adaptable(topicPath, path, value, null);
    }

    public static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path, final HttpStatus status) {
        return adaptable(topicPath, path, null, status);
    }

    public static Adaptable adaptable(final TopicPath topicPath,
            final JsonPointer path,
            @Nullable final JsonValue value,
            final HttpStatus status) {

        final DittoHeaders dittoHeaders;
        final PayloadBuilder payloadBuilder = Payload.newBuilder(path);

        if (value != null) {
            payloadBuilder.withValue(value);
        }
        if (status != null) {
            payloadBuilder.withStatus(status);
        }

        if (topicPath.getAction().filter(topicPath1 -> topicPath1.equals(TopicPath.Action.MERGE)).isPresent()) {
            dittoHeaders = TestConstants.HEADERS_V_2_FOR_MERGE_COMMANDS;
        } else {
            dittoHeaders = TestConstants.HEADERS_V_2;
        }

        return Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(dittoHeaders)
                .build();
    }

    public static final String FILTER = "eq(attributes/foo, bar)";

    public static final String OPTIONS = "sort(+thingId), cursor(200)";

    public static final JsonFieldSelector FIELDS = JsonFieldSelector.newInstance("/attributes", "/definition");

    public static final String SUBSCRIPTION_ID = "123456781234";

    public static final long DEMAND = 12;

    public static final JsonArray ITEMS =
            JsonArray.of(THING.toJson(), THING2.toJson());

    public static final DittoRuntimeException EXCEPTION =
            ThingIdInvalidException.newBuilder("TestException").build();

    public static class Policies {

        static final String POLICY_NAME = "myPolicy";
        public static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, POLICY_NAME);
        public static final Policy POLICY = PoliciesModelFactory.newPolicyBuilder(POLICY_ID).build();
        public static final DittoHeaders HEADERS =
                DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();

        public static final Label POLICY_ENTRY_LABEL = Label.of("admin");
        public static final Label POLICY_ENTRY_LABEL2 = Label.of("frontend");

        public static final SubjectId SUBJECT_ID1 =
                PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "theSubject1");
        static final SubjectId SUBJECT_ID2 = PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "theSubject2");
        public static final Subject SUBJECT1 = PoliciesModelFactory.newSubject(SUBJECT_ID1, SubjectType.GENERATED);
        static final Subject SUBJECT2 = PoliciesModelFactory.newSubject(SUBJECT_ID2, SubjectType.GENERATED);
        static final EffectedPermissions GRANT_WRITE =
                EffectedPermissions.newInstance(singletonList("WRITE"), emptyList());
        static final EffectedPermissions GRANT_READ_REVOKE_WRITE =
                EffectedPermissions.newInstance(singletonList("READ"), singletonList("WRITE"));
        public static final Resource RESOURCE1 =
                PoliciesModelFactory.newResource("thing", "/thingId", GRANT_READ_REVOKE_WRITE);
        public static final Resource RESOURCE2 = PoliciesModelFactory.newResource("message", "/subject", GRANT_WRITE);

        public static final PolicyEntry POLICY_ENTRY =
                PolicyEntry.newInstance(POLICY_ENTRY_LABEL, Arrays.asList(SUBJECT1, SUBJECT2),
                        Arrays.asList(RESOURCE1, RESOURCE2));
        static final PolicyEntry POLICY_ENTRY2 =
                PolicyEntry.newInstance(POLICY_ENTRY_LABEL2, singletonList(SUBJECT1), singletonList(RESOURCE2));

        public static final Iterable<PolicyEntry> POLICY_ENTRIES =
                new HashSet<>(Arrays.asList(POLICY_ENTRY, POLICY_ENTRY2));
        public static final Resources RESOURCES = Resources.newInstance(RESOURCE1, RESOURCE2);
        public static final Subjects SUBJECTS = Subjects.newInstance(SUBJECT1, SUBJECT2);

        public static class TopicPaths {

            public static final TopicPath CREATE =
                    TopicPath.newBuilder(POLICY_ID).policies().commands().create().build();
            public static final TopicPath MODIFY =
                    TopicPath.newBuilder(POLICY_ID).policies().commands().modify().build();
            public static final TopicPath DELETE =
                    TopicPath.newBuilder(POLICY_ID).policies().commands().delete().build();
            public static final TopicPath RETRIEVE =
                    TopicPath.newBuilder(POLICY_ID).policies().commands().retrieve().build();

        }

    }

    public static class Connectivity {

        public static final ConnectionId CONNECTION_ID = ConnectionId.of("bumlux");
        public static final Instant TIMESTAMP = Instant.now();

        public static class TopicPaths {

            public static TopicPath announcement(final String name) {
                return ProtocolFactory.newTopicPathBuilderFromName(CONNECTION_ID.toString())
                        .connections()
                        .announcements()
                        .name(name)
                        .build();
            }

        }

    }

    private TestConstants() {
        throw new AssertionError();
    }

}
