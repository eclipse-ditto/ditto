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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 */
final class TestConstants {

    static final String NAMESPACE = "org.eclipse.ditto.test";

    static final String ID = "myThing";
    static final String ID2 = "myThing2";

    static final String CORRELATION_ID = "dittoCorrelationId";

    static final String THING_ID = NAMESPACE + ":" + ID;
    static final String THING_ID2 = NAMESPACE + ":" + ID2;

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

    static final Feature FEATURE = Feature.newBuilder().properties(FEATURE_PROPERTIES).withId(FEATURE_ID).build();

    static final Features FEATURES = Features.newBuilder().set(FEATURE).build();

    static final Thing THING = Thing.newBuilder().setId(THING_ID).build();

    static final Thing THING2 = Thing.newBuilder().setId(THING_ID2).build();

    static final DittoHeaders DITTO_HEADERS_V_1 = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .schemaVersion(JsonSchemaVersion.V_1)
            .build();

    static final DittoHeaders DITTO_HEADERS_V_2 = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .schemaVersion(JsonSchemaVersion.V_2)
            .build();

    static final DittoHeaders HEADERS_V_1 = ProtocolFactory.newHeaders(DITTO_HEADERS_V_1);

    static final DittoHeaders HEADERS_V_2 = ProtocolFactory.newHeaders(DITTO_HEADERS_V_2);

    static final long REVISION = 1337;

    static final TopicPath TOPIC_PATH = TopicPath.newBuilder(THING_ID).things().twin().commands().modify().build();

    static final Payload PAYLOAD = Payload.newBuilder(JsonPointer.empty()).withValue(THING.toJson()).build();

    static final Adaptable ADAPTABLE =
            Adaptable.newBuilder(TOPIC_PATH).withPayload(PAYLOAD).withHeaders(HEADERS_V_2).build();

    private TestConstants() {
        throw new AssertionError();
    }

}
