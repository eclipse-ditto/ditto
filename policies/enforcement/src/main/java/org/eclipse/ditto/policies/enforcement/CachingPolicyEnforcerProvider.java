/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.slf4j.Logger;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.Replicator;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Transparent caching layer for {@link org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider}
 */
final class CachingPolicyEnforcerProvider implements PolicyEnforcerProvider {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(CachingPolicyEnforcerProvider.class);
    private final ActorRef cachingPolicyEnforcerProviderActor;

    CachingPolicyEnforcerProvider(final ActorRefFactory actorRefFactory,
            final Cache<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCache,
            final PolicyEnforcerProvider delegate,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final ActorRef pubSubMediator) {
        this.cachingPolicyEnforcerProviderActor = actorRefFactory.actorOf(
                CachingPolicyEnforcerProviderActor.props(policyEnforcerCache, delegate, blockedNamespaces,
                        pubSubMediator));
    }

    @Override
    public CompletionStage<Optional<PolicyEnforcer>> getPolicyEnforcer(@Nullable final PolicyId policyId) {
        if (policyId == null) {
            return CompletableFuture.completedStage(Optional.empty());
        }
        return Patterns.ask(cachingPolicyEnforcerProviderActor, policyId, Duration.ofSeconds(60))
                .thenApply(response -> {
                    final Optional<PolicyEnforcer> result;
                    if (response instanceof Optional<?> optional) {
                        result = optional.map(value -> {
                            if (value instanceof PolicyEnforcer policyEnforcer) {
                                return policyEnforcer;
                            } else {
                                LOGGER.warn("Did receive Optional holding an unexpected type. " +
                                                "Did expect a PolicyEnforcer but got <{}>.",
                                        value.getClass());
                                return null;
                            }
                        });
                    } else {
                        result = Optional.empty();
                    }
                    return result;
                });
    }


    /**
     * Actor which handles the actual cache lookup and invalidation.
     */
    private static final class CachingPolicyEnforcerProviderActor extends AbstractActor {

        private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        private final Cache<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCache;
        private final PolicyEnforcerProvider delegate;

        CachingPolicyEnforcerProviderActor(final Cache<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCache,
                final PolicyEnforcerProvider delegate,
                @Nullable final BlockedNamespaces blockedNamespaces,
                final ActorRef pubSubMediator) {

            this.policyEnforcerCache = policyEnforcerCache;
            this.delegate = delegate;

            if (blockedNamespaces != null) {
                blockedNamespaces.subscribeForChanges(getSelf());
            }

            // subscribe for PolicyTags in order to reload policyEnforcer when "backing policy" was modified
            pubSubMediator.tell(DistPubSubAccess.subscribe(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, getSelf()),
                    getSelf());
        }

        private static Props props(final Cache<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCache,
                final PolicyEnforcerProvider delegate,
                @Nullable final BlockedNamespaces blockedNamespaces,
                final ActorRef pubSubMediator) {
            return Props.create(CachingPolicyEnforcerProviderActor.class,
                    () -> new CachingPolicyEnforcerProviderActor(policyEnforcerCache, delegate, blockedNamespaces,
                            pubSubMediator));
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(PolicyId.class, this::doGetPolicyEnforcer)
                    .match(DistributedPubSubMediator.SubscribeAck.class, s -> log.debug("Got subscribeAck <{}>.", s))
                    .match(PolicyTag.class, policyTag -> policyEnforcerCache.invalidate(policyTag.getEntityId()))
                    .match(Replicator.Changed.class, this::handleChangedBlockedNamespaces)
                    .build();
        }

        private void doGetPolicyEnforcer(final PolicyId policyId) {
            final ActorRef sender = sender();
            final CompletableFuture<Optional<PolicyEnforcer>> policyEnforcerCS = policyEnforcerCache.get(policyId)
                    .thenCompose(optionalEntry -> {
                        if (optionalEntry.isPresent()) {
                            //Value is already cached. Return potentially unavailable value as optional.
                            final Entry<PolicyEnforcer> policyEnforcerEntry = optionalEntry.get();
                            return CompletableFuture.completedStage(policyEnforcerEntry.get());
                        } else {
                            // Value is not yet cached. Try to load it and put it into cache.
                            return delegate.getPolicyEnforcer(policyId)
                                    .thenApply(optionalEnforcer -> {
                                        final Entry<PolicyEnforcer> policyEnforcerEntry =
                                                optionalEnforcer.map(enforcer -> Entry.of(0L, enforcer))
                                                        .orElseGet(Entry::nonexistent);
                                        policyEnforcerCache.put(policyId, policyEnforcerEntry);
                                        return optionalEnforcer;
                                    });
                        }
                    });
            Patterns.pipe(policyEnforcerCS, getContext().dispatcher()).to(sender);
        }

        @SuppressWarnings("unchecked")
        private void handleChangedBlockedNamespaces(final Replicator.Changed<?> changed) {
            if (changed.dataValue() instanceof ORSet<?> orSet) {
                final ORSet<String> namespaces = (ORSet<String>) orSet;
                logNamespaces("Received", namespaces);
                policyEnforcerCache.asMap().keySet().stream()
                        .filter(policyId -> {
                            final String cachedNamespace = policyId.getNamespace();
                            return namespaces.contains(cachedNamespace);
                        })
                        .forEach(policyEnforcerCache::invalidate);
            } else {
                log.warning("Unhandled: <{}>", changed);
            }
        }

        private void logNamespaces(final String verb, final ORSet<String> namespaces) {
            if (namespaces.size() > 25) {
                log.info("{} <{}> namespaces", verb, namespaces.size());
            } else {
                log.info("{} namespaces: <{}>", verb, namespaces);
            }
        }

    }

}
