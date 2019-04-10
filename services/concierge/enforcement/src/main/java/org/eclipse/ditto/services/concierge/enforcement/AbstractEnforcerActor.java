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
package org.eclipse.ditto.services.concierge.enforcement;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Extensible actor to execute enforcement behavior.
 */
public abstract class AbstractEnforcerActor extends AbstractGraphActor<Contextual<Object>> {

    /**
     * Contextual information about this actor.
     */
    protected final Contextual<NotUsed> contextual;

    /**
     * Create an instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub-mediator.
     * @param conciergeForwarder the concierge forwarder.
     * @param enforcerExecutor executor for enforcement steps.
     * @param askTimeout how long to wait for entity actors.
     */
    protected AbstractEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            final Duration askTimeout) {

        contextual = new Contextual<>(NotUsed.getInstance(), getSelf(), getContext().getSystem().deadLetters(),
                pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout, log,
                decodeEntityId(getSelf()),
                createResponseReceiversCache());
    }

    @Override
    protected abstract Flow<Contextual<Object>, Contextual<Object>, NotUsed> getHandler();

    @Override
    @SuppressWarnings("unchecked")
    protected Class<Contextual<Object>> getMessageClass() {
        // trick Java type system into accepting the cast without it understanding the covariance of Contextual<>
        return (Class<Contextual<Object>>) (Object) Contextual.class;
    }

    @Override
    protected Source<Contextual<Object>, NotUsed> mapMessage(final Object message) {
        return Source.single(contextual.withReceivedMessage(message, getSender()));
    }

    private static EntityId decodeEntityId(final ActorRef self) {
        final String name = self.path().name();
        try {
            final String typeWithPath = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
            return EntityId.readFrom(typeWithPath);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }
    }

    private static Cache<String, ActorRef> createResponseReceiversCache() {
        return CaffeineCache.of(Caffeine.newBuilder().expireAfterWrite(120, TimeUnit.SECONDS));
    }
}
