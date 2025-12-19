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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;

/**
 * Base properties of each {@link AbstractRoute} implementation.
 */
@NotThreadSafe
public final class RouteBaseProperties {

    private final ActorRef proxyActor;
    private final ActorSystem actorSystem;
    private final GatewayConfig gatewayConfig;
    private final HeaderTranslator headerTranslator;

    private RouteBaseProperties(final Builder builder) {
        proxyActor = ConditionChecker.checkNotNull(builder.proxyActor, "builder.proxyActor");
        actorSystem = ConditionChecker.checkNotNull(builder.actorSystem, "builder.actorSystem");
        gatewayConfig = ConditionChecker.checkNotNull(builder.gatewayConfig, "builder.gatewayConfig");
        headerTranslator = ConditionChecker.checkNotNull(builder.headerTranslator, "builder.headerTranslator");
    }

    /**
     * Returns a mutable builder with a fluent API for creating a {@code RouteBaseProperties} object.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns a mutable builder with a fluent API for creating a {@code RouteBaseProperties} object.
     *
     * @param routeBaseProperties the initial properties of the returned builder
     * @return the builder.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public static Builder newBuilder(final RouteBaseProperties routeBaseProperties) {
        ConditionChecker.checkNotNull(routeBaseProperties, "routeBaseProperties");
        return newBuilder()
                .proxyActor(routeBaseProperties.getProxyActor())
                .actorSystem(routeBaseProperties.getActorSystem())
                .gatewayConfig(routeBaseProperties.getGatewayConfig())
                .headerTranslator(routeBaseProperties.getHeaderTranslator());
    }

    /**
     * Returns the proxy actor ref.
     *
     * @return the proxy actor ref.
     */
    public ActorRef getProxyActor() {
        return proxyActor;
    }

    /**
     * Returns the actor system.
     *
     * @return the actor system.
     */
    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    /**
     * Returns the Gateway config.
     *
     * @return the Gateway config.
     */
    public GatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    /**
     * Returns the {@link HeaderTranslator}.
     *
     * @return the {@code HeaderTranslator}.
     */
    public HeaderTranslator getHeaderTranslator() {
        return headerTranslator;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (RouteBaseProperties) o;
        return Objects.equals(proxyActor, that.proxyActor) &&
                Objects.equals(actorSystem, that.actorSystem) &&
                Objects.equals(gatewayConfig, that.gatewayConfig) &&
                Objects.equals(headerTranslator, that.headerTranslator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proxyActor,
                actorSystem,
                gatewayConfig,
                headerTranslator);
    }

    @NotThreadSafe
    public static final class Builder {

        private ActorRef proxyActor;
        private ActorSystem actorSystem;
        private GatewayConfig gatewayConfig;
        private HeaderTranslator headerTranslator;

        private Builder() {
            proxyActor = null;
            actorSystem = null;
            gatewayConfig = null;
            headerTranslator = null;
        }

        /**
         * Sets the specified proxy actor reference argument.
         *
         * @param proxyActor the proxy actor reference to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code proxyActor} is {@code null}.
         */
        public Builder proxyActor(final ActorRef proxyActor) {
            this.proxyActor = ConditionChecker.checkNotNull(proxyActor, "proxyActor");
            return this;
        }

        /**
         * Sets the specified actor system argument.
         *
         * @param actorSystem the actor system to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code actorSystem} is {@code null}.
         */
        public Builder actorSystem(final ActorSystem actorSystem) {
            this.actorSystem = ConditionChecker.checkNotNull(actorSystem, "actorSystem");
            return this;
        }

        /**
         * Sets the specified {@code GatewayConfig} argument.
         *
         * @param gatewayConfig the config to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code gatewayConfig} is {@code null}.
         */
        public Builder gatewayConfig(final GatewayConfig gatewayConfig) {
            this.gatewayConfig = ConditionChecker.checkNotNull(gatewayConfig, "gatewayConfig");
            return this;
        }

        /**
         * Sets the specified {@code HeaderTranslator} argument.
         *
         * @param headerTranslator the header translator to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code headerTranslator} is {@code null}.
         */
        public Builder headerTranslator(final HeaderTranslator headerTranslator) {
            this.headerTranslator = ConditionChecker.checkNotNull(headerTranslator, "headerTranslator");
            return this;
        }

        /**
         * Builds a {@code RouteBaseProperties} object with the properties set to this builder.
         *
         * @return the {@code RouteBaseProperties}.
         * @throws NullPointerException if any required property is {@code null}.
         */
        public RouteBaseProperties build() {
            return new RouteBaseProperties(this);
        }

    }

}
