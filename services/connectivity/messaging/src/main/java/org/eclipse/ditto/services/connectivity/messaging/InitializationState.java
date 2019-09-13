/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Holds the current state of the resources that need to be initialized when a connection is started.
 */
public final class InitializationState {

    private final boolean publisherReady;
    private final boolean consumersReady;
    private final int mappersReady;


    /**
     * Instantiates a new {@link InitializationState}.
     *
     * @param expectedMappers the expected number of mapping actors
     */
    InitializationState(final int expectedMappers) {
        this(false, false, expectedMappers);
    }

    private InitializationState(final boolean publisherReady, final boolean consumersReady, final int mappers) {
        this.publisherReady = publisherReady;
        this.consumersReady = consumersReady;
        this.mappersReady = mappers;
    }

    /**
     * @return new instance representing the new state
     */
    InitializationState resourceReady(final ResourceReady ready) {
        switch (ready.resourceType) {
            case PUBLISHER:
                return new InitializationState(true, consumersReady, mappersReady);
            case CONSUMER:
                return new InitializationState(publisherReady, true, mappersReady);
            case MAPPER:
                return new InitializationState(publisherReady, consumersReady, mappersReady - 1);
            default:
                // never happen
                return this;
        }
    }

    /**
     * @return {@code true} if all resources have been initialized successfully
     */
    boolean isFinished() {
        return publisherReady && consumersReady && mappersReady <= 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final InitializationState that = (InitializationState) o;
        return publisherReady == that.publisherReady &&
                consumersReady == that.consumersReady &&
                mappersReady == that.mappersReady;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publisherReady, consumersReady, mappersReady);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "publisherReady=" + publisherReady +
                ", consumersReady=" + consumersReady +
                ", mappersReady=" + mappersReady +
                "]";
    }

    /**
     * A message signalling that a resource is now ready.
     */
    public static class ResourceReady {

        private final ResourceType resourceType;
        @Nullable private final ActorRef resourceRef;

        private ResourceReady(final ResourceType resourceType) {
            this(resourceType, null);
        }

        private ResourceReady(final ResourceType resourceType, @Nullable final ActorRef resourceRef) {
            this.resourceType = resourceType;
            this.resourceRef = resourceRef;
        }

        /**
         * @param ref the {@link ActorRef} of the publisher actor
         * @return new {@link ResourceReady} message for publishers
         */
        public static ResourceReady publisherReady(final ActorRef ref) {
            return new ResourceReady(ResourceType.PUBLISHER, ref);
        }

        /**
         * @return new {@link ResourceReady} message for consumers
         */
        public static ResourceReady consumersReady() {
            return new ResourceReady(ResourceType.CONSUMER);
        }

        /**
         * @return new {@link ResourceReady} message for mapping actors
         */
        public static ResourceReady mapperReady() {
            return new ResourceReady(ResourceType.MAPPER);
        }

        /**
         * @return the reference to the started resource
         */
        Optional<ActorRef> getResourceRef() {
            return Optional.ofNullable(resourceRef);
        }

        /**
         * @return {@code true} if the resource affects a publisher
         */
        boolean isPublisher() {
            return ResourceType.PUBLISHER.equals(resourceType);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ResourceReady that = (ResourceReady) o;
            return resourceType == that.resourceType &&
                    Objects.equals(resourceRef, that.resourceRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceType, resourceRef);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "resourceType=" + resourceType +
                    ", resourceRef=" + resourceRef +
                    "]";
        }
    }

    /**
     * Definition of available resource types.
     */
    enum ResourceType {
        PUBLISHER,
        CONSUMER,
        MAPPER
    }
}
