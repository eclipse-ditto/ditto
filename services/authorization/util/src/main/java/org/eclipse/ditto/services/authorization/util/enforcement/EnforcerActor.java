/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.util.enforcement;

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that enforces authorization.
 */
public final class EnforcerActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Enforcement.Context context;
    private final AuthorizationCaches caches;
    private final EntityId entityId;

    private final Set<EnforcementProvider> enforcementProviders;
    @Nullable
    private final PreEnforcementConfig preEnforcementConfig;

    private EnforcerActor(final EntityRegionMap entityRegionMap,
            final AuthorizationCaches caches, final Set<EnforcementProvider> enforcementProviders,
            @Nullable PreEnforcementConfig preEnforcementConfig) {
        this.entityId = decodeEntityId(getSelf());

        this.caches = requireNonNull(caches);
        this.enforcementProviders = requireNonNull(enforcementProviders);
        this.preEnforcementConfig = preEnforcementConfig;

        this.context = new Enforcement.Context(
                Duration.ofSeconds(10), // TODO: make configurable
                requireNonNull(entityRegionMap),
                entityId,
                log,
                caches,
                getSelf());
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param entityRegionMap map from resource types to entity shard regions.
     * @param authorizationCaches cache of information relevant for authorization.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @return the Akka configuration Props object.
     */
    public static Props props(final EntityRegionMap entityRegionMap,
            final AuthorizationCaches authorizationCaches, final Set<EnforcementProvider> enforcementProviders) {

        return Props.create(EnforcerActor.class,
                () -> new EnforcerActor(entityRegionMap, authorizationCaches, enforcementProviders, null));
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param entityRegionMap map from resource types to entity shard regions.
     * @param authorizationCaches cache of information relevant for authorization.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcementConfig a {@link PreEnforcementConfig}, may be {@code null}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final EntityRegionMap entityRegionMap,
            final AuthorizationCaches authorizationCaches, final Set<EnforcementProvider> enforcementProviders,
            @Nullable PreEnforcementConfig preEnforcementConfig) {

        return Props.create(EnforcerActor.class,
                () -> new EnforcerActor(entityRegionMap, authorizationCaches, enforcementProviders,
                        preEnforcementConfig));
    }

    @Override
    public void postStop() {
        // if stopped, remove self from entity ID cache.
        caches.invalidateEntityId(entityId);
    }

    @Override
    public Receive createReceive() {
        final Receive enforcementReceive = createEnforcementReceive();
        final Receive preEnforcementReceive = createPreEnforcementReceive();
        if (preEnforcementReceive == null) {
            return enforcementReceive;
        }

        return preEnforcementReceive.orElse(enforcementReceive);
    }

    @Nullable
    private Receive createPreEnforcementReceive() {
        if (preEnforcementConfig == null) {
            return null;
        }


        final Predicate<WithDittoHeaders> condition = preEnforcementConfig.getCondition();
        final ActorRef forwardee = preEnforcementConfig.getForwardee();

        return ReceiveBuilder.create()
                .match(WithDittoHeaders.class, condition::test,
                        withDittoHeaders -> forwardee.forward(withDittoHeaders, getContext()))
                .build();
    }

    private Receive createEnforcementReceive() {
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        enforcementProviders.forEach(provider -> {
            @SuppressWarnings("unchecked") final Class<Command> commandClass = provider.getCommandClass();
            @SuppressWarnings("unchecked") final FI.UnitApply<Command> commandHandler =
                    cmd -> provider.createEnforcement(context).enforce(cmd, getSender());
            receiveBuilder.match(commandClass, commandHandler);
        });

        receiveBuilder.matchAny(message -> {
            log.warning("Unexpected message: <{}>", message);
            unhandled(message);
        });


        return receiveBuilder.build();
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
}
