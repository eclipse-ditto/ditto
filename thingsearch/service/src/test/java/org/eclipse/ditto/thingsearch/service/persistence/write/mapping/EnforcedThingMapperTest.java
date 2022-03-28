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
import org.junit.Test;

public final class EnforcedThingMapperTest {

    @Test
    public void testV2Thing() {
        final JsonObject thing = JsonFactory.newObject("""
                {
                  "thingId": "hello:world",
                  "_namespace": "hello",
                  "_revision": 1024,
                  "_modified": "2019-01-02T03:04:05.006Z",
                  "policyId": "hello:world",
                  "features": {
                    "hi": {
                      "definition": [
                        "earth:v0:1",
                        "mars:v0:2"
                      ],
                      "properties": {
                        "there": true
                      }
                    }
                  },
                  "attributes": {
                    "hello": "world"
                  }
                }""");

        final var policy =
                PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy", "id"))
                        .forLabel("grant-root")
                        .setSubject("g:0", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/", Permission.READ)
                        .forLabel("grant-d")
                        .setSubject("g:1", SubjectType.GENERATED)
                        .setGrantedPermissions(THING, "/features/hi/properties/there", Permission.READ)
                        .build();

        final long policyRevision = 56L;

        final JsonObject expectedJson = JsonFactory.newObject("""
                {
                  "_id": "hello:world",
                  "_revision": 1024,
                  "_namespace": "hello",
                  "gr":["g:1","g:0"],
                  "policyId": "hello:world",
                  "__policyRev": 56,
                  "s": {
                    "thingId": "hello:world",
                    "_namespace": "hello",
                    "_revision": 1024,
                    "_modified": "2019-01-02T03:04:05.006Z",
                    "policyId": "hello:world",
                    "features": { "hi": { "definition": [ "earth:v0:1", "mars:v0:2" ], "properties": { "there": true } } },
                    "attributes": { "hello": "world" }
                  },
                  "d": [
                    { "k": "/thingId", "v": "hello:world", "g": [ "g:0" ], "r": [] },
                    { "k": "/_namespace", "v": "hello", "g": [ "g:0" ], "r": [] },
                    { "k": "/_revision", "v": 1024, "g": [ "g:0" ], "r": [] },
                    { "k": "/_modified", "v": "2019-01-02T03:04:05.006Z", "g": [ "g:0" ], "r": [] },
                    { "k": "/policyId", "v": "hello:world", "g": [ "g:0" ], "r": [] },
                    { "k": "/features/hi/definition",      "v": "earth:v0:1", "g": [ "g:0" ], "r": [] },
                    { "k": "/features/*/definition",      "v": "earth:v0:1", "g": [ "g:0" ], "r": [] },
                    { "k": "/features/hi/definition",      "v": "mars:v0:2", "g": [ "g:0" ], "r": [] },
                    { "k": "/features/*/definition",      "v": "mars:v0:2", "g": [ "g:0" ], "r": [] },
                    { "k": "/features/hi/properties/there", "v": true, "g": [ "g:1", "g:0" ], "r": [] },
                    { "k": "/features/*/properties/there", "v": true, "g": [ "g:1", "g:0" ], "r": [] },
                    { "k": "/attributes/hello", "v": "world", "g": [ "g:0" ], "r": [] }
                  ]
                }""");

        final BsonDocument result = EnforcedThingMapper.mapThing(thing, policy, policyRevision);

        assertThat(JsonFactory.newObject(result.toJson())).isEqualTo(expectedJson);
    }
}
