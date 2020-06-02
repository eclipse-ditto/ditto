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

package org.eclipse.ditto.services.gateway.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.DefaultUserInformation;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.UserInformation;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.services.gateway.util.config.DittoGatewayConfig;
import org.eclipse.ditto.services.gateway.util.config.GatewayConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.RawHeader;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;

/**
 * Unit test for {@link HttpRequestActor}.
 */
public final class HttpRequestActorTest {

    private static ActorSystem system;
    private static GatewayConfig gatewayConfig;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create();
        final DefaultScopedConfig dittoConfig = DefaultScopedConfig.dittoScoped(ConfigFactory.load("test.conf"));
        gatewayConfig = DittoGatewayConfig.of(dittoConfig);
    }

    @AfterClass
    public static void afterClass() {
        if (null != system) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void handlesWhoamiCommand() throws ExecutionException, InterruptedException {
        final DittoHeaders dittoHeaders = createAuthorizedHeaders();
        final Whoami whoami = Whoami.of(dittoHeaders);
        final HttpResponse expectedResponse = createExpectedWhoamiResponse(whoami);
        final HttpRequest request = HttpRequest.GET("/whoami");
        final CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        final ActorRef underTest = createHttpRequestActor(request, responseFuture);
        underTest.tell(Whoami.of(dittoHeaders), ActorRef.noSender());

        assertThat(responseFuture.get()).isEqualTo(expectedResponse);
    }

    private ActorRef createHttpRequestActor(final HttpRequest request, final CompletableFuture<HttpResponse> response) {
        return system.actorOf(HttpRequestActor.props(
                TestProbe.apply(system).ref(),
                HeaderTranslator.empty(),
                request,
                response,
                gatewayConfig.getHttpConfig(),
                gatewayConfig.getCommandConfig()
        ));
    }

    private DittoHeaders createAuthorizedHeaders() {
        final AuthorizationContext context = AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "any-google-user")),
                AuthorizationSubject.newInstance(
                        SubjectId.newInstance(SubjectIssuer.INTEGRATION, "any-integration-subject")));
        return DittoHeaders.newBuilder()
                .authorizationContext(context)
                .build();
    }

    private HttpResponse createExpectedWhoamiResponse(final Whoami whoami) {
        final UserInformation userInformation = DefaultUserInformation.fromAuthorizationContext(
                whoami.getDittoHeaders().getAuthorizationContext());
        final List<HttpHeader> expectedHeaders = whoami.getDittoHeaders().entrySet()
                .stream()
                .map(e -> RawHeader.create(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return HttpResponse.create().withStatus(StatusCodes.OK)
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(userInformation.toJsonString()))
                .withHeaders(expectedHeaders);
    }

}
