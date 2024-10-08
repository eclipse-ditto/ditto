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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DefaultJwtAuthenticationResultProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultJwtAuthenticationResultProviderTest {

    private final static ActorSystem ACTOR_SYSTEM =
            ActorSystem.create(UUID.randomUUID().toString(), ConfigFactory.load("test"));

    @Test
    public void getAuthorizationContext() {
        final var dittoExtensionConfig =
                ScopedConfig.dittoExtension(ACTOR_SYSTEM.settings().config());
        final JwtAuthenticationResultProvider underTest =
                JwtAuthenticationResultProvider.get(ACTOR_SYSTEM, dittoExtensionConfig, null);
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(JwtTestConstants.VALID_JWT_TOKEN);
        final AuthorizationSubject myTestSubj = AuthorizationSubject.newInstance("example:myTestSubj");

        final AuthenticationResult authorizationResult =
                underTest.getAuthenticationResult(jsonWebToken, DittoHeaders.empty()).toCompletableFuture().join();

        assertThat(authorizationResult.getAuthorizationContext().getAuthorizationSubjects())
                .containsExactly(myTestSubj);
    }

}
