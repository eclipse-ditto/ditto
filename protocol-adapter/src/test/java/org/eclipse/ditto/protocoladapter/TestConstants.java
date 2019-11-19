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
package org.eclipse.ditto.protocoladapter;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.Arrays;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 *
 */
final class TestConstants {

    static final String NAMESPACE = "org.eclipse.ditto.test";

    static final String NAME = "myThing";
    static final String NAME2 = "myThing2";


    static final String CORRELATION_ID = "dittoCorrelationId";

    static final ThingId THING_ID = ThingId.of(NAMESPACE, NAME);
    static final ThingId THING_ID2 = ThingId.of(NAMESPACE, NAME2);

    static final String POLICY_NAME = "myPolicy";
    static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, POLICY_NAME);

    static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance("sid");

    static final AclEntry ACL_ENTRY =
            AclEntry.newInstance(AUTHORIZATION_SUBJECT, AccessControlListModelFactory.allPermissions());

    static final AccessControlList ACL = AccessControlListModelFactory.newAcl(ACL_ENTRY);

    static final JsonPointer ATTRIBUTE_POINTER = JsonPointer.of("/foo");

    static final JsonValue ATTRIBUTE_VALUE = JsonValue.of("bar");

    static final JsonObject ATTRIBUTES_JSON = JsonObject.newBuilder().set(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE).build();

    static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributes(ATTRIBUTES_JSON);

    static final String FEATURE_ID = "fluxCompensator";

    static final String SUBJECT = "message:subject";

    static final JsonPointer FEATURE_PROPERTY_POINTER = JsonPointer.of("/baz");

    static final JsonValue FEATURE_PROPERTY_VALUE = JsonValue.of(42);

    static final JsonObject FEATURE_PROPERTIES_JSON =
            JsonObject.newBuilder().set(FEATURE_PROPERTY_POINTER, FEATURE_PROPERTY_VALUE).build();

    static final FeatureProperties FEATURE_PROPERTIES =
            ThingsModelFactory.newFeatureProperties(FEATURE_PROPERTIES_JSON);

    static final FeatureDefinition FEATURE_DEFINITION = FeatureDefinition.fromIdentifier("org.eclipse.ditto:foo:1.0.0");

    static final JsonArray FEATURE_DEFINITION_JSON = FEATURE_DEFINITION.toJson();

    static final Feature FEATURE = Feature.newBuilder().properties(FEATURE_PROPERTIES).withId(FEATURE_ID).build();

    static final Features FEATURES = Features.newBuilder().set(FEATURE).build();

    static final Thing THING = Thing.newBuilder().setId(THING_ID).build();

    static final Thing THING2 = Thing.newBuilder().setId(THING_ID2).build();

    static final DittoHeaders DITTO_HEADERS_V_1 = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .schemaVersion(JsonSchemaVersion.V_1)
            .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
            .build();

    static final DittoHeaders DITTO_HEADERS_V_2 = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .schemaVersion(JsonSchemaVersion.V_2)
            .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
            .putHeader(MessageHeaderDefinition.STATUS_CODE.getKey(), "200")
            .build();

    static final DittoHeaders HEADERS_V_1 = ProtocolFactory.newHeadersWithDittoContentType(DITTO_HEADERS_V_1);

    static final DittoHeaders HEADERS_V_2 = ProtocolFactory.newHeadersWithDittoContentType(DITTO_HEADERS_V_2);

    static final DittoHeaders HEADERS_V_1_NO_CONTENT_TYPE = DittoHeaders.newBuilder(HEADERS_V_1).removeHeader(
            DittoHeaderDefinition.CONTENT_TYPE.getKey()).build();

    static final DittoHeaders HEADERS_V_2_NO_CONTENT_TYPE = DittoHeaders.newBuilder(HEADERS_V_2).removeHeader(
            DittoHeaderDefinition.CONTENT_TYPE.getKey()).build();

    static final long REVISION = 1337;

    static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path) {
        return adaptable(topicPath, path, null, null);
    }

    static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path, final JsonValue value) {
        return adaptable(topicPath, path, value, null);
    }

    static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path, final HttpStatusCode status) {
        return adaptable(topicPath, path, null, status);
    }

    static Adaptable adaptable(final TopicPath topicPath, final JsonPointer path, @Nullable final JsonValue value,
            final HttpStatusCode status) {
        final PayloadBuilder payloadBuilder = Payload.newBuilder(path);

        if (value != null) {
            payloadBuilder.withValue(value);
        }
        if (status != null) {
            payloadBuilder.withStatus(status);
        }

        return Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
    }

    static class Policies {

        static final String POLICY_NAME = "myPolicy";
        static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, POLICY_NAME);
        static final Policy POLICY = PoliciesModelFactory.newPolicyBuilder(POLICY_ID).build();
        static final DittoHeaders HEADERS =
                DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();

        static final Label POLICY_ENTRY_LABEL = Label.of("admin");
        static final Label POLICY_ENTRY_LABEL2 = Label.of("frontend");

        static final SubjectId SUBJECT_ID1 = PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "theSubject1");
        static final SubjectId SUBJECT_ID2 = PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "theSubject2");
        static final Subject SUBJECT1 = PoliciesModelFactory.newSubject(SUBJECT_ID1, SubjectType.GENERATED);
        static final Subject SUBJECT2 = PoliciesModelFactory.newSubject(SUBJECT_ID2, SubjectType.GENERATED);
        static final EffectedPermissions GRANT_READ =
                EffectedPermissions.newInstance(singletonList("READ"), emptyList());
        static final EffectedPermissions GRANT_WRITE =
                EffectedPermissions.newInstance(singletonList("WRITE"), emptyList());
        static final EffectedPermissions GRANT_READ_REVOKE_WRITE =
                EffectedPermissions.newInstance(singletonList("READ"), singletonList("WRITE"));
        static final Resource RESOURCE1 =
                PoliciesModelFactory.newResource("thing", "/thingId", GRANT_READ_REVOKE_WRITE);
        static final Resource RESOURCE2 = PoliciesModelFactory.newResource("message", "/subject", GRANT_WRITE);

        static final PolicyEntry POLICY_ENTRY =
                PolicyEntry.newInstance(POLICY_ENTRY_LABEL, Arrays.asList(SUBJECT1, SUBJECT2),
                        Arrays.asList(RESOURCE1, RESOURCE2));
        static final PolicyEntry POLICY_ENTRY2 =
                PolicyEntry.newInstance(POLICY_ENTRY_LABEL2, Arrays.asList(SUBJECT1), Arrays.asList(RESOURCE2));

        static final Iterable<PolicyEntry> POLICY_ENTRIES = Arrays.asList(POLICY_ENTRY, POLICY_ENTRY2);
        static final Resources RESOURCES = Resources.newInstance(RESOURCE1, RESOURCE2);
        static final Subjects SUBJECTS = Subjects.newInstance(SUBJECT1, SUBJECT2);

        static class TopicPaths {

            static final TopicPath CREATE =
                    TopicPath.newBuilder(POLICY_ID).policies().twin().commands().create().build();
            static final TopicPath MODIFY =
                    TopicPath.newBuilder(POLICY_ID).policies().twin().commands().modify().build();
            static final TopicPath DELETE =
                    TopicPath.newBuilder(POLICY_ID).policies().twin().commands().delete().build();
            static final TopicPath RETRIEVE =
                    TopicPath.newBuilder(POLICY_ID).policies().twin().commands().retrieve().build();
        }

    }

    private TestConstants() {
        throw new AssertionError();
    }

}
