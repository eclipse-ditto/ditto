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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

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
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
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
 * Actor system extension to load the configured connection signing algorithms.
 */
public final class AmqpConnectionSigningExtension implements Extension, CredentialsVisitor<AmqpConnectionSigning> {

    private static final Id ID = new Id();

    private final Map<String, AmqpConnectionSigningFactory> registry;

    private AmqpConnectionSigningExtension(final Map<String, AmqpConnectionSigningFactory> registry) {
        this.registry = registry;
    }

    /**
     * Lookup this actor system extension.
     *
     * @param system the actor system.
     * @return this extension.
     */
    public static AmqpConnectionSigningExtension get(final ActorSystem system) {
        return ID.get(system);
    }

    @Override
    public AmqpConnectionSigning clientCertificate(final ClientCertificateCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    @Override
    public AmqpConnectionSigning usernamePassword(final UserPasswordCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    @Override
    public AmqpConnectionSigning sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    @Override
    public AmqpConnectionSigning hmac(final HmacCredentials credentials) {
        final AmqpConnectionSigningFactory factory = registry.get(credentials.getAlgorithm());
        if (factory != null) {
            return factory.createAmqpConnectionSigning(credentials);
        } else {
            throw MessageSendingFailedException.newBuilder()
                    .message("Failed to sign AMQP 1.0 connection.")
                    .description(String.format("The configured algorithm '%s' does not exist. Fix this by adding an" +
                                    " implementation for it in the '%s' section of the amqp config.",
                            credentials.getAlgorithm(), Amqp10Config.Amqp10ConfigValue.HMAC_ALGORITHMS.getConfigPath()))
                    .build();
        }
    }

    @Override
    public AmqpConnectionSigning oauthClientCredentials(final OAuthClientCredentials credentials) {
        return NoOpSigning.INSTANCE;
    }

    /**
     * The extension ID.
     */
    public static final class Id extends AbstractExtensionId<AmqpConnectionSigningExtension> {

        private static AmqpConnectionSigningFactory instantiate(final ExtendedActorSystem system,
                final String className) {
            final ClassTag<AmqpConnectionSigningFactory> tag =
                    scala.reflect.ClassTag$.MODULE$.apply(AmqpConnectionSigningFactory.class);
            return system.dynamicAccess().createInstanceFor(className, List$.MODULE$.empty(), tag).get();
        }

        @Override
        public AmqpConnectionSigningExtension createExtension(final ExtendedActorSystem system) {
            final Config config = system.settings().config();
            final Map<String, String> hmacAlgorithms =
                    DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config))
                            .getConnectionConfig()
                            .getAmqp10Config()
                            .getHmacAlgorithms();
            final Map<String, AmqpConnectionSigningFactory> factoryMap = hmacAlgorithms.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> instantiate(system, entry.getValue())));
            return new AmqpConnectionSigningExtension(Collections.unmodifiableMap(factoryMap));
        }
    }

}
