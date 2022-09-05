/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.headers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link DefaultDittoHeadersValidator}.
 */
public final class DefaultDittoHeadersValidatorTest {

    @Nullable
    private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void validationFailsForTooManyAuthSubjects() {
        assert actorSystem != null;

        final var underTest = DittoHeadersValidator.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()));

        final var authorizationContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                AuthorizationSubject.newInstance("ditto:ditto1"),
                AuthorizationSubject.newInstance("ditto:ditto2"));
        final var dittoHeaders = DittoHeaders.newBuilder()
                .authorizationContext(authorizationContext)
                .build();

        final var validationResult = underTest.validate(dittoHeaders);

        assertThat(validationResult).failsWithin(1, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(DittoHeadersTooLargeException.class);
    }

}