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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.junit.Test;

public final class EnforcedThingMapperTest {

    @Test
    public void testV2Thing() {
        final JsonObject thing = JsonFactory.newObject("{\n" +
                "  \"thingId\": \"hello:world\",\n" +
                "  \"_namespace\": \"hello\",\n" +
                "  \"_revision\": 1024,\n" +
                "  \"_modified\": \"2019-01-02T03:04:05.006Z\",\n" +
                "  \"policyId\": \"hello:world\",\n" +
                "  \"features\": {\n" +
                "    \"hi\": {\n" +
                "      \"definition\": [\n" +
                "        \"earth:v0:1\",\n" +
                "        \"mars:v0:2\"\n" +
                "      ],\n" +
                "      \"properties\": {\n" +
                "        \"there\": true\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"attributes\": {\n" +
                "    \"hello\": \"world\"\n" +
                "  }\n" +
                "}");

        final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(
                PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy", "id"))
                        .forLabel("grant-root")
                        .setSubject("g:0", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/", Permission.READ)
                        .forLabel("grant-d")
                        .setSubject("g:1", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/features/hi/properties/there", Permission.READ)
                        .build());

        final long policyRevision = 56L;

        final JsonObject expectedJson = JsonFactory.newObject("{\n" +
                "  \"_id\": \"hello:world\",\n" +
                "  \"_revision\": 1024,\n" +
                "  \"_namespace\": \"hello\",\n" +
                "  \"gr\":[\"g:1\",\"g:0\"],\n" +
                "  \"policyId\": \"hello:world\",\n" +
                "  \"__policyRev\": 56,\n" +
                "  \"s\": {\n" +
                "    \"thingId\": \"hello:world\",\n" +
                "    \"_namespace\": \"hello\",\n" +
                "    \"_revision\": 1024,\n" +
                "    \"_modified\": \"2019-01-02T03:04:05.006Z\",\n" +
                "    \"policyId\": \"hello:world\",\n" +
                "    \"features\": { \"hi\": { \"definition\": [ \"earth:v0:1\", \"mars:v0:2\" ], \"properties\": { \"there\": true } } },\n" +
                "    \"attributes\": { \"hello\": \"world\" }\n" +
                "  },\n" +
                "  \"d\": [\n" +
                "    { \"k\": \"/thingId\", \"v\": \"hello:world\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/_namespace\", \"v\": \"hello\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/_revision\", \"v\": 1024, \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/_modified\", \"v\": \"2019-01-02T03:04:05.006Z\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/policyId\", \"v\": \"hello:world\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/features/hi/definition\"," +
                "      \"v\": \"earth:v0:1\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/features/*/definition\"," +
                "      \"v\": \"earth:v0:1\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/features/hi/definition\"," +
                "      \"v\": \"mars:v0:2\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/features/*/definition\"," +
                "      \"v\": \"mars:v0:2\", \"g\": [ \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/features/hi/properties/there\", \"v\": true, \"g\": [ \"g:1\", \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/features/*/properties/there\", \"v\": true, \"g\": [ \"g:1\", \"g:0\" ], \"r\": [] },\n" +
                "    { \"k\": \"/attributes/hello\", \"v\": \"world\", \"g\": [ \"g:0\" ], \"r\": [] }\n" +
                "  ]\n" +
                "}");

        final BsonDocument result = EnforcedThingMapper.mapThing(thing, enforcer, policyRevision);

        assertThat(JsonFactory.newObject(result.toJson())).isEqualTo(expectedJson);
    }
}
