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
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
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
public final class RequestSigningExtension implements Extension, CredentialsVisitor<RequestSigning> {

    private static final Id ID = new Id();

    private final ActorSystem system;
    private final Map<String, RequestSigningFactory> registry;

    private RequestSigningExtension(final ActorSystem system,
            final Map<String, RequestSigningFactory> registry) {
        this.system = system;
        this.registry = registry;
    }

    /**
     * Lookup this actor system extension.
     *
     * @param system the actor system.
     * @return this extension.
     */
    public static RequestSigningExtension get(final ActorSystem system) {
        return ID.get(system);
    }

    private static RequestSigningFactory instantiate(final ExtendedActorSystem system, final String className) {
        final ClassTag<RequestSigningFactory> tag =
                scala.reflect.ClassTag$.MODULE$.apply(RequestSigningFactory.class);
        return system.dynamicAccess().createInstanceFor(className, List$.MODULE$.empty(), tag).get();
    }

    @Override
    public RequestSigning clientCertificate(final ClientCertificateCredentials credentials) {
        return NoOpRequestSigning.INSTANCE;
    }

    @Override
    public RequestSigning usernamePassword(final UserPasswordCredentials credentials) {
        return NoOpRequestSigning.INSTANCE;
    }

    @Override
    public RequestSigning sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        return NoOpRequestSigning.INSTANCE;
    }

    @Override
    public RequestSigning hmac(final HmacCredentials credentials) {
        final RequestSigningFactory factory = registry.get(credentials.getAlgorithm());
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

    /**
     * The extension ID.
     */
    public static final class Id extends AbstractExtensionId<RequestSigningExtension> {

        @Override
        public RequestSigningExtension createExtension(final ExtendedActorSystem system) {
            final Config config = system.settings().config();
            final Map<String, String> hmacAlgorithms =
                    DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config))
                            .getConnectionConfig()
                            .getHttpPushConfig()
                            .getHmacAlgorithms();
            final Map<String, RequestSigningFactory> factoryMap = hmacAlgorithms.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> instantiate(system, entry.getValue())));
            return new RequestSigningExtension(system, Collections.unmodifiableMap(factoryMap));
        }
    }
}
