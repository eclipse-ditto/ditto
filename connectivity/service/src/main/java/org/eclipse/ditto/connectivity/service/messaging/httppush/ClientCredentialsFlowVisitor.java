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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;

/**
 * Visitor to create a flow that augment requests with bearer tokens.
 */
final class ClientCredentialsFlowVisitor implements
        CredentialsVisitor<Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed>> {

    private final ActorSystem actorSystem;
    private final HttpPushConfig config;

    private ClientCredentialsFlowVisitor(final ActorSystem actorSystem, final HttpPushConfig config) {
        this.actorSystem = actorSystem;
        this.config = config;
    }

    static Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> eval(
            final ActorSystem actorSystem, final HttpPushConfig config, final Connection connection) {
        return connection.getCredentials()
                .map(credentials -> credentials.accept(new ClientCredentialsFlowVisitor(actorSystem, config)))
                .orElseGet(Flow::create);
    }

    @Override
    public Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> clientCertificate(
            final ClientCertificateCredentials credentials) {
        return Flow.create();
    }

    @Override
    public Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> usernamePassword(
            final UserPasswordCredentials credentials) {
        return Flow.create();
    }

    @Override
    public Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed>
    sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        return Flow.create();
    }

    @Override
    public Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> hmac(
            final HmacCredentials credentials) {
        return Flow.create();
    }

    @Override
    public Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> oauthClientCredentials(
            final OAuthClientCredentials credentials) {
        return ClientCredentialsFlow.of(credentials, config).withToken(Http.get(actorSystem));
    }
}
