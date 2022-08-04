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

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Base properties of each {@link AbstractRoute} implementation.
 */
@NotThreadSafe
public final class RouteBaseProperties {

    private final ActorRef proxyActor;
    private final ActorSystem actorSystem;
    private final HttpConfig httpConfig;
    private final CommandConfig commandConfig;
    private final HeaderTranslator headerTranslator;

    private RouteBaseProperties(final Builder builder) {
        proxyActor = ConditionChecker.checkNotNull(builder.proxyActor, "builder.proxyActor");
        actorSystem = ConditionChecker.checkNotNull(builder.actorSystem, "builder.actorSystem");
        httpConfig = ConditionChecker.checkNotNull(builder.httpConfig, "builder.httpConfig");
        commandConfig = ConditionChecker.checkNotNull(builder.commandConfig, "builder.commandConfig");
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
                .httpConfig(routeBaseProperties.getHttpConfig())
                .commandConfig(routeBaseProperties.getCommandConfig())
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
     * Returns the HTTP config.
     *
     * @return the HTTP config.
     */
    public HttpConfig getHttpConfig() {
        return httpConfig;
    }

    /**
     * Returns the command config.
     *
     * @return the command config.
     */
    public CommandConfig getCommandConfig() {
        return commandConfig;
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
                Objects.equals(httpConfig, that.httpConfig) &&
                Objects.equals(commandConfig, that.commandConfig) &&
                Objects.equals(headerTranslator, that.headerTranslator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proxyActor,
                actorSystem,
                httpConfig,
                commandConfig,
                headerTranslator);
    }

    @NotThreadSafe
    public static final class Builder {

        private ActorRef proxyActor;
        private ActorSystem actorSystem;
        private HttpConfig httpConfig;
        private CommandConfig commandConfig;
        private HeaderTranslator headerTranslator;

        private Builder() {
            proxyActor = null;
            actorSystem = null;
            httpConfig = null;
            commandConfig = null;
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
         * Sets the specified {@code HttpConfig} argument.
         *
         * @param httpConfig the config to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code httpConfig} is {@code null}.
         */
        public Builder httpConfig(final HttpConfig httpConfig) {
            this.httpConfig = ConditionChecker.checkNotNull(httpConfig, "httpConfig");
            return this;
        }

        /**
         * Sets the specified {@code CommandConfig} argument.
         *
         * @param commandConfig the config to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code commandConfig} is {@code null}.
         */
        public Builder commandConfig(final CommandConfig commandConfig) {
            this.commandConfig = ConditionChecker.checkNotNull(commandConfig, "commandConfig");
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
