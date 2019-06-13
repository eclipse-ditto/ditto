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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.io.Closeable;
import java.util.Collection;
import java.util.function.Function;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespaceResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;

/**
 * Superclass of actors operating on the persistence at the level of namespaces.
 * It subscribes to the commands {@link PurgeNamespace} from the pub-sub mediator and carries them out.
 * Instances of this actor should start as cluster singletons in order to not perform identical operations multiple
 * times on the database.
 *
 * @param <S> type of namespace selection for the underlying persistence.
 */
public abstract class AbstractNamespaceOpsActor<S> extends AbstractActor {

    /**
     * The actor's logger.
     */
    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final NamespaceOps<S> namespaceOps;
    private final ActorMaterializer materializer;

    /**
     * Create a new instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param namespaceOpsFunction implementation of namespace operations on the persistence.
     */
    protected AbstractNamespaceOpsActor(final ActorRef pubSubMediator,
            final Function<MongoDbConfig, NamespaceOps<S>> namespaceOpsFunction) {
        this.pubSubMediator = pubSubMediator;

        final DefaultMongoDbConfig mongoDbConfig = DefaultMongoDbConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        this.namespaceOps = namespaceOpsFunction.apply(mongoDbConfig);
        materializer = ActorMaterializer.create(getContext());
    }

    /**
     * Creates a new instance of this actor using the pub-sub mediator of the actor system in which it is created.
     *
     * @param namespaceOps namespace operations on the persistence.
     */
    protected AbstractNamespaceOpsActor(final NamespaceOps<S> namespaceOps) {
        pubSubMediator = DistributedPubSub.get(getContext().system()).mediator();
        this.namespaceOps = namespaceOps;
        materializer = ActorMaterializer.create(getContext());
    }

    /**
     * Get all documents in a given namespace among all collections in the database.
     *
     * @param namespace the namespace.
     * @return identifier of all persisted entities in the namespace.
     */
    protected abstract Collection<S> selectNamespace(final String namespace);

    @Override
    public void preStart() {
        subscribeForNamespaceCommands();
    }

    @Override
    public void postStop() throws Exception {
        if (namespaceOps instanceof Closeable) {
            ((Closeable) namespaceOps).close();
        }
        super.postStop();
    }

    private void subscribeForNamespaceCommands() {
        final ActorRef self = getSelf();
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(PurgeNamespace.TYPE, self), self);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PurgeNamespace.class, this::purgeNamespace)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::ignoreSubscribeAck)
                .matchAny(message -> log.warning("unhandled: <{}>", message))
                .build();
    }

    private void purgeNamespace(final PurgeNamespace purgeNamespace) {
        LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
        final Collection<S> namespaceSelections = selectNamespace(purgeNamespace.getNamespace());
        log.info("Running <{}>. Affected collections: <{}>.", purgeNamespace, namespaceSelections);
        final ActorRef sender = getSender();
        namespaceOps.purgeAll(namespaceSelections)
                .runWith(Sink.head(), materializer)
                .thenAccept(errors -> {
                    // send response to speed up purge workflow
                    final PurgeNamespaceResponse response;
                    if (errors.isEmpty()) {
                        response = PurgeNamespaceResponse.successful(purgeNamespace.getNamespace(), getResourceType(),
                                purgeNamespace.getDittoHeaders());
                    } else {
                        LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
                        final String namespace = purgeNamespace.getNamespace();
                        errors.forEach(error -> log.error(error, "Error purging namespace <{}>", namespace));
                        response = PurgeNamespaceResponse.failed(namespace, getResourceType(),
                                purgeNamespace.getDittoHeaders());
                    }
                    sender.tell(response, getSelf());
                })
                .exceptionally(error -> {
                    LogUtil.enhanceLogWithCorrelationId(log, purgeNamespace);
                    log.error(error, "Failed to purge namespace <{}>!", purgeNamespace.getNamespace());
                    // Reply nothing - DB errors were converted to stream elements and handled
                    return null;
                });
    }

    /**
     * Returns the resource type this actor operates on.
     *
     * @return the resource type.
     */
    protected abstract String getResourceType();

    private void ignoreSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        // do nothing
    }

}
