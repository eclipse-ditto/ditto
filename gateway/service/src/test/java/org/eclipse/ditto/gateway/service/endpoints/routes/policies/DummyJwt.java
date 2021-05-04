/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.jwt.model.Audience;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * Dummy JSON web token for tests.
 * Do not make final in order to have a subclass.
 */
class DummyJwt implements JsonWebToken {

    static final Instant EXPIRY = Instant.now().plus(Duration.ofDays(1));

    @Override
    public String getToken() {
        return "token";
    }

    @Override
    public JsonObject getHeader() {
        return JsonObject.empty();
    }

    @Override
    public JsonObject getBody() {
        return JsonObject.newBuilder()
                .set("sub", "dummy-subject")
                .set("iss", "dummy-issuer")
                .set("aud", JsonArray.of("aud-1", "aud-2"))
                .set("foo", JsonArray.of("bar1", "bar2", "bar3"))
                .set("/single/nested", JsonArray.newBuilder().add("I am a nested single").build())
                .build();
    }

    @Override
    public String getKeyId() {
        return "dummy-key-id";
    }

    @Override
    public String getIssuer() {
        return "dummy-issuer";
    }

    @Override
    public String getSignature() {
        return "dummy-signature";
    }

    @Override
    public List<String> getSubjects() {
        return List.of("dummy-subject");
    }

    @Override
    public Audience getAudience() {
        return Audience.empty();
    }

    @Override
    public String getAuthorizedParty() {
        return "dummy-authorized-party";
    }

    @Override
    public List<String> getScopes() {
        return List.of("dummy-scope");
    }

    @Override
    public Instant getExpirationTime() {
        return EXPIRY;
    }

    @Override
    public boolean isExpired() {
        return false;
    }
}
