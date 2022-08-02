/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.api.AcksDeclared;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * An implementation of {@link DistributedAcks}
 * for cluster members that do not participate in acknowledgement label declaration or lookup.
 */
final class DistributedAcksEmptyImpl implements DistributedAcks {

    private final DistributedDataConfig config;

    DistributedAcksEmptyImpl(final ActorSystem system) {
        // replicator name and role are placeholders because no distributed acks data is started on this node
        config = DistributedData.createConfig(system, "dummyReplicator", "dummyRole");
    }

    @Override
    public void receiveLocalDeclaredAcks(final ActorRef receiver) {
        // do nothing
    }

    @Override
    public void receiveDistributedDeclaredAcks(final ActorRef receiver) {
        // do nothing
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        // do nothing
    }

    @Override
    public CompletionStage<AcksDeclared> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels, final ActorRef subscriber,
            @Nullable final String group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<AcksDeclared> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels, final ActorRef subscriber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAcknowledgementLabelDeclaration(final ActorRef subscriber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DistributedDataConfig getConfig() {
        return config;
    }
}
