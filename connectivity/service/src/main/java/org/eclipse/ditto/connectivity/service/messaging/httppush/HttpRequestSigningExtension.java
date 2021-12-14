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

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.signing.NoOpSigning;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import com.typesafe.config.Config;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag;

/**
 * Actor system extension to load the configured request signing algorithms.
 */
public final class HttpRequestSigningExtension implements Extension, CredentialsVisitor<HttpRequestSigning> {

    private static final Id ID = new Id();

    private final ActorSystem system;
    private final Map<String, HttpRequestSigningFactory> registry;

    private HttpRequestSigningExtension(final ActorSystem system,
            final Map<String, HttpRequestSigningFactory> registry) {
        this.system = system;
        this.registry = registry;
    }

    /**
     * Lookup this actor system extension.
     *
     * @param system the actor system.
     * @return this extension.
     */
    public static HttpRequestSigningExtension get(final ActorSystem system) {
        return ID.get(system);
    }

    @Override
    public HttpRequestSigning clientCertificate(final ClientCertificateCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    @Override
    public HttpRequestSigning usernamePassword(final UserPasswordCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    @Override
    public HttpRequestSigning sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    @Override
    public HttpRequestSigning hmac(final HmacCredentials credentials) {
        final HttpRequestSigningFactory factory = registry.get(credentials.getAlgorithm());
        if (factory != null) {
            return factory.create(system, credentials);
        } else {
            throw MessageSendingFailedException.newBuilder()
                    .message("Failed to sign HTTP request.")
                    .description(String.format("The configured algorithm '%s' does not exist.",
                            credentials.getAlgorithm()))
                    .build();
        }
    }

    @Override
    public HttpRequestSigning oauthClientCredentials(final OAuthClientCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    /**
     * The extension ID.
     */
    public static final class Id extends AbstractExtensionId<HttpRequestSigningExtension> {

        private static HttpRequestSigningFactory instantiate(final ExtendedActorSystem system, final String className) {
            final ClassTag<HttpRequestSigningFactory> tag =
                    scala.reflect.ClassTag$.MODULE$.apply(HttpRequestSigningFactory.class);
            return system.dynamicAccess().createInstanceFor(className, List$.MODULE$.empty(), tag).get();
        }

        @Override
        public HttpRequestSigningExtension createExtension(final ExtendedActorSystem system) {
            final Config config = system.settings().config();
            final Map<String, String> hmacAlgorithms =
                    DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config))
                            .getConnectionConfig()
                            .getHttpPushConfig()
                            .getHmacAlgorithms();
            final Map<String, HttpRequestSigningFactory> factoryMap = hmacAlgorithms.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> instantiate(system, entry.getValue())));
            return new HttpRequestSigningExtension(system, Collections.unmodifiableMap(factoryMap));
        }
    }
}
