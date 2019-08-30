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
package org.eclipse.ditto.services.thingsearch.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.JsonFactory.newObjectBuilder;
import static org.eclipse.ditto.json.JsonObject.empty;
import static org.eclipse.ditto.json.JsonValue.nullLiteral;
import static org.eclipse.ditto.model.policies.PoliciesResourceType.THING;

import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.junit.Test;

/**
 * Tests {@link EnforcedThingFlattener}
 */
public final class EnforcedThingFlattenerTest {

    @Test
    public void testWithAclEnforcer() {
        final JsonObject inputJson = JsonFactory.newObject("{\n" +
                "  \"a\": [ {\"b\": \"c\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"f\": \"g\",\n" +
                "      \"h\": \"i\"\n" +
                "    },\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012\n" +
                "  }\n" +
                "}");

        final Enforcer enforcer = AclEnforcer.of(ThingsModelFactory.newAcl("{\n" +
                "  \"grant:read-only\": {\n" +
                "    \"READ\": true,\n" +
                "    \"WRITE\": false,\n" +
                "    \"ADMINISTRATE\": false\n" +
                "  },\n" +
                "  \"grant:write-only\": {\n" +
                "    \"READ\": false,\n" +
                "    \"WRITE\": true,\n" +
                "    \"ADMINISTRATE\": false\n" +
                "  }\n" +
                "}"));

        final JsonArray expectedOutputJson = JsonFactory.newArray("[\n" +
                "  {\n" +
                "    \"k\": \"/a/b\",\n" +
                "    \"v\": \"c\",\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/a\",\n" +
                "    \"v\": true,\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/e/f\",\n" +
                "    \"v\": \"g\",\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/e/h\",\n" +
                "    \"v\": \"i\",\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/j\",\n" +
                "    \"v\": true,\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/k\",\n" +
                "    \"v\": 6,\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/l\",\n" +
                "    \"v\": { \"$numberLong\": \"123456789012\" },\n" +
                "    \"g\": [ \"grant:read-only\" ],\n" +
                "    \"r\": []\n" +
                "  }\n" +
                "]");

        final EnforcedThingFlattener underTest = new EnforcedThingFlattener("thing:id", enforcer, -1);

        final String result = underTest.eval(inputJson)
                .map(Document::toJson)
                .collect(Collectors.joining(",", "[", "]"));

        assertThat(JsonFactory.newArray(result)).isEqualTo(expectedOutputJson);

    }

    @Test
    public void testWithPolicyEnforcer() {
        final JsonObject inputJson = JsonFactory.newObject("{\n" +
                "  \"thingId\":\"solar.system:pluto\"," +
                "  \"_namespace\":\"solar.system\"," +
                "  \"a\": [ {\"b\": \"c\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"f\": \"g\",\n" +
                "      \"h\": \"i\"\n" +
                "    },\n" +
                "    \"j\": true,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012\n" +
                "  }\n" +
                "}");

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(
                PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy","id"))
                        .forLabel("grant-root")
                        .setSubject("grant:root", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/", Permission.READ)
                        .forLabel("grant-d")
                        .setSubject("grant:d.e", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/d/e", Permission.READ)
                        .forLabel("revoke")
                        .setSubject("revoke:d.e", SubjectType.GENERATED)
                        .setRevokedPermissions(THING, "/d/e", Permission.READ)
                        .build());

        final JsonArray expectedOutputJson = JsonFactory.newArray("[\n" +
                "  {\n" +
                "    \"k\": \"/thingId\",\n" +
                "    \"v\": \"solar.system:pluto\",\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/_namespace\",\n" +
                "    \"v\": \"solar.system\",\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/a/b\",\n" +
                "    \"v\": \"c\",\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/a\",\n" +
                "    \"v\": true,\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/e/f\",\n" +
                "    \"v\": \"g\",\n" +
                "    \"g\": [ \"grant:d.e\", \"grant:root\" ],\n" +
                "    \"r\": [ \"revoke:d.e\" ]\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/e/h\",\n" +
                "    \"v\": \"i\",\n" +
                "    \"g\": [ \"grant:d.e\", \"grant:root\" ],\n" +
                "    \"r\": [ \"revoke:d.e\" ]\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/j\",\n" +
                "    \"v\": true,\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/k\",\n" +
                "    \"v\": 6,\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/d/l\",\n" +
                "    \"v\": { \"$numberLong\": \"123456789012\" },\n" +
                "    \"g\": [ \"grant:root\" ],\n" +
                "    \"r\": []\n" +
                "  }\n" +
                "]");

        final EnforcedThingFlattener underTest = new EnforcedThingFlattener("thing:id", enforcer, -1);

        final String result = underTest.eval(inputJson)
                .map(Document::toJson)
                .collect(Collectors.joining(",", "[", "]"));

        assertThat(JsonFactory.newArray(result)).isEqualTo(expectedOutputJson);
    }

    @Test
    public void testFeatures() {
        final JsonObject inputJson = JsonFactory.newObject("{\n" +
                "  \"features\": {\n" +
                "    \"f1\": {\n" +
                "      \"definition\": [\"ns:def1:v0\",\"ns:def1:v2\"]\n" +
                "    }," +
                "    \"f2\": {\n" +
                "      \"properties\": {\"x\":5}\n" +
                "    }" +
                "  }\n" +
                "}");

        final Enforcer emptyEnforcer = AclEnforcer.of(ThingsModelFactory.newAclBuilder().build());

        final JsonArray expectedOutputJson = JsonFactory.newArray("[\n" +
                "  {\n" +
                "    \"k\": \"/features/f1/definition\",\n" +
                "    \"v\": \"ns:def1:v0\",\n" +
                "    \"g\": [],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/features/*/definition\",\n" +
                "    \"v\": \"ns:def1:v0\",\n" +
                "    \"g\": [],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/features/f1/definition\",\n" +
                "    \"v\": \"ns:def1:v2\",\n" +
                "    \"g\": [],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/features/*/definition\",\n" +
                "    \"v\": \"ns:def1:v2\",\n" +
                "    \"g\": [],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/features/f2/properties/x\",\n" +
                "    \"v\": 5,\n" +
                "    \"g\": [],\n" +
                "    \"r\": []\n" +
                "  },\n" +
                "  {\n" +
                "    \"k\": \"/features/*/properties/x\",\n" +
                "    \"v\": 5,\n" +
                "    \"g\": [],\n" +
                "    \"r\": []\n" +
                "  }\n" +
                "]");

        final EnforcedThingFlattener underTest = new EnforcedThingFlattener("thing:id", emptyEnforcer, -1);

        final String result = underTest.eval(inputJson)
                .map(Document::toJson)
                .collect(Collectors.joining(",", "[", "]"));

        assertThat(JsonFactory.newArray(result)).isEqualTo(expectedOutputJson);
    }

    @Test
    public void testNullOrEmptyAttributes() {
        final Enforcer emptyEnforcer = AclEnforcer.of(ThingsModelFactory.newAclBuilder().build());
        final EnforcedThingFlattener underTest = new EnforcedThingFlattener("thing:id", emptyEnforcer, -1);

        final JsonObject inputJson = newObjectBuilder()
                .set("attributes",
                        newObjectBuilder()
                                .set("tag1", empty())
                                .set("tag2", nullLiteral())
                                .set("tag3", newObjectBuilder().set("foo", nullLiteral()).build())
                                .set("tag4", newObjectBuilder().set("foo", empty()).build())
                                .set("tag5", JsonArray.empty())
                                .set("tag6", JsonArray.of(JsonArray.of(JsonArray.empty())))
                                .build()
                ).build();

        final JsonArray result = underTest.eval(inputJson)
                .map(Document::toJson)
                .map(JsonFactory::readFrom)
                .collect(JsonCollectors.valuesToArray());

        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/tag1", JsonObject.empty()));
        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/tag2", JsonValue.nullLiteral()));
        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/tag3/foo", JsonValue.nullLiteral()));
        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/tag4/foo", JsonObject.empty()));
        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/tag5", JsonObject.empty()));
        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/tag6", JsonObject.empty()));
    }

    @Test
    public void testZeroMaxArraySize() {
        final Enforcer emptyEnforcer = AclEnforcer.of(ThingsModelFactory.newAclBuilder().build());
        final EnforcedThingFlattener underTest = new EnforcedThingFlattener("thing:id", emptyEnforcer, 0);

        final JsonObject inputJson = newObjectBuilder()
                .set("attributes",
                        newObjectBuilder()
                                .set("trimmedArray", JsonArray.of(0, 1, 2))
                                .build()
                ).build();

        final JsonArray result = underTest.eval(inputJson)
                .map(Document::toJson)
                .map(JsonFactory::readFrom)
                .collect(JsonCollectors.valuesToArray());

        DittoJsonAssertions.assertThat(result).contains(flattened("/attributes/trimmedArray", JsonObject.empty()));
    }

    private JsonValue flattened(final String path, JsonValue value) {
        return JsonFactory.newObjectBuilder()
                .set("k", path)
                .set("v", value)
                .set("g", JsonArray.empty())
                .set("r", JsonArray.empty())
                .build();
    }
}
