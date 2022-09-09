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
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.api.AcksDeclared;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Extension;

/**
 * Interface to access the local and distributed data of declared acknowledgement labels.
 */
public interface DistributedAcks extends Extension {

    /**
     * Receive a snapshot of local acknowledgement declarations on each update.
     * Subscription terminates when the receiver terminates.
     * Treat termination of the receiver as the loss of local subscription data.
     *
     * @param receiver receiver of local acknowledgement declarations; the node-level subscriber actor.
     */
    void receiveLocalDeclaredAcks(final ActorRef receiver);

    /**
     * Receive a snapshot of the distributed data of acknowledgement label declarations on each change.
     * Subscription terminates when the receiver terminates.
     *
     * @param receiver receiver of distributed acknowledgement label declarations; the node-level publisher actor.
     */
    void receiveDistributedDeclaredAcks(final ActorRef receiver);


    /**
     * Remove a subscriber without waiting for acknowledgement.
     *
     * @param subscriber who is being removed.
     */
    void removeSubscriber(ActorRef subscriber);

    /**
     * Declare labels of acknowledgements that a subscriber may send.
     * Each subscriber's declared acknowledgment labels must be different from the labels declared by other subscribers.
     * Subscribers relinquish their declared labels when they terminate.
     *
     * @param acknowledgementLabels the acknowledgement labels to declare.
     * @param subscriber the subscriber.
     * @param group the group in which the actor belongs.
     * @return a future SubAck if the declaration succeeded, or a failed future if it failed.
     */
    CompletionStage<AcksDeclared> declareAcknowledgementLabels(
            Collection<AcknowledgementLabel> acknowledgementLabels, ActorRef subscriber, @Nullable String group);

    /**
     * Declare labels of acknowledgements that a subscriber may send.
     * Each subscriber's declared acknowledgment labels must be different from the labels declared by other subscribers.
     * Subscribers relinquish their declared labels when they terminate.
     *
     * @param acknowledgementLabels the acknowledgement labels to declare.
     * @param subscriber the subscriber.
     * @return a future SubAck if the declaration succeeded, or a failed future if it failed.
     */
    default CompletionStage<AcksDeclared> declareAcknowledgementLabels(
            Collection<AcknowledgementLabel> acknowledgementLabels,
            ActorRef subscriber) {

        return declareAcknowledgementLabels(acknowledgementLabels, subscriber, null);
    }

    /**
     * Remove the acknowledgement label declaration of a subscriber.
     *
     * @param subscriber the subscriber.
     */
    void removeAcknowledgementLabelDeclaration(ActorRef subscriber);

    /**
     * Get the config of this distributed data.
     *
     * @return The config.
     * @since 3.0.0
     */
    DistributedDataConfig getConfig();

    /**
     * Start AcksSupervisor under an ActorContext and expose a DistributedAcks interface.
     * Precondition: the cluster member has the role {@code "acks-aware"}.
     *
     * @param context the actor context in which to start the AcksSupervisor.
     * @return the DistributedAcks interface.
     */
    static DistributedAcks create(final ActorContext context) {
        return DistributedAcksImpl.create(context);
    }

    /**
     * Start AcksSupervisor under the user guardian and expose a DistributedAcks interface.
     * Precondition: the cluster member has the role {@code "acks-aware"}.
     *
     * @param system the actor system.
     * @return the DistributedAcks interface.
     */
    static DistributedAcks create(final ActorSystem system) {
        return DistributedAcksImpl.create(system, system);
    }

    /**
     * Create a dummy {@code DistributedAcks} interface not backed by a distributed data.
     * Useful for cluster members not participating in signal publication.
     *
     * @return an empty distributed acks.
     */
    static DistributedAcks empty(final ActorSystem system) {
        return new DistributedAcksEmptyImpl(system);
    }

    /**
     * Lookup the extension on the publisher side.
     *
     * @param system the actor system.
     * @return a unique instance of DistributedAcks.
     */
    static DistributedAcks lookup(final ActorSystem system) {
        return DistributedAcksImpl.ExtensionId.INSTANCE.get(system);
    }

}
