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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import akka.actor.ActorRef;

/**
 * A package of ddata reader, writer, and creator of local subscriptions to plug into a pub-sub framework.
 *
 * @param <R> type of reads from the distributed data.
 * @param <W> type of writes from the distributed data.
 */
public interface DData<R, W> {

    /**
     * @return the distributed data reader.
     */
    DDataReader<R> getReader();

    /**
     * @return the distributed data writer.
     */
    DDataWriter<W> getWriter();

    /**
     * @return a new, empty subscriptions object.
     */
    Subscriptions<W> createSubscriptions();

    /**
     * Retrieve subscribers of a collection of topics from the distributed data.
     * Useful for circumventing lackluster existential type implementation when the reader type parameter isn't known.
     *
     * @param topics the topics.
     * @return subscribers of those topics in the distributed data.
     */
    default CompletionStage<Collection<ActorRef>> getSubscribers(final Collection<String> topics) {
        return getReader().getSubscribers(topics.stream().map(getReader()::approximate).collect(Collectors.toSet()));
    }
}
