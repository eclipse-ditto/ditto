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
package org.eclipse.ditto.internal.utils.cluster;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;

/**
 * Helper class for accessing Akka's {@link DistributedPubSubMediator} messages (e.g. publishing, subscribing, etc.).
 */
@Immutable
public final class DistPubSubAccess {

    private static final String GROUPED_TOPIC_SUFFIX = ":grouped";

    private DistPubSubAccess() {
        throw new AssertionError();
    }

    /**
     * Publishes the passed {@code message} to all subscribers on the passed {@code topic}.
     *
     * @param topic the topic to publish on.
     * @param message the message to publish.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.Publish publish(final String topic, final Object message) {
        return new DistributedPubSubMediator.Publish(topic, message);
    }

    /**
     * Publishes the passed {@code message} to one subscriber subscribed via group on the passed {@code topic} with
     * suffix {@value #GROUPED_TOPIC_SUFFIX}.
     *
     * @param topic the group topic to publish on.
     * @param message the message to publish.
     * @return the message to send the DistributedPubSubMediator.
     * @throws NullPointerException if {@code topic} is {@code null}.
     */
    public static DistributedPubSubMediator.Publish publishViaGroup(final String topic, final Object message) {
        return new DistributedPubSubMediator.Publish(getGroupTopic(topic), message, true);
    }

    /**
     * Extends the specified topic by appending the suffix {@value #GROUPED_TOPIC_SUFFIX}.
     *
     * @param topic the topic to be extended.
     * @return the extended topic.
     * @throws NullPointerException if {@code topic} is {@code null}.
     */
    public static String getGroupTopic(final String topic) {
        return ConditionChecker.checkNotNull(topic, "topic") + GROUPED_TOPIC_SUFFIX;
    }

    /**
     * Subscribes the passed {@code subscriber} on the passed {@code topic}.
     *
     * @param topic the topic to subscribe on.
     * @param subscriber the ActorRef which should get messages on the subscribed topic.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.Subscribe subscribe(final String topic, final ActorRef subscriber) {
        return new DistributedPubSubMediator.Subscribe(topic, subscriber);
    }

    /**
     * Unsubscribes the passed {@code subscriber} on the passed {@code topic}.
     *
     * @param topic the topic to unsubscribe on.
     * @param subscriber the ActorRef which got messages on the subscribed topic.
     * @return the message to send the DistributedPubSubMediator.
     */
    public static DistributedPubSubMediator.Unsubscribe unsubscribe(final String topic, final ActorRef subscriber) {
        return new DistributedPubSubMediator.Unsubscribe(topic, subscriber);
    }

    /**
     * Subscribes the passed {@code subscriber} on the passed {@code topic} with suffix {@value #GROUPED_TOPIC_SUFFIX}.
     *
     * @param topic the group topic to subscribe on.
     * @param subscriber the ActorRef which should get messages on the subscribed group topic.
     * @return the message to send the DistributedPubSubMediator.
     * @throws NullPointerException if {@code topic} is {@code null}.
     */
    public static DistributedPubSubMediator.Subscribe subscribeViaGroup(final String topic, final String group,
            final ActorRef subscriber) {
        return new DistributedPubSubMediator.Subscribe(getGroupTopic(topic), group, subscriber);
    }

    /**
     * Unsubscribes the passed {@code subscriber} on the passed {@code topic} with suffix {@value #GROUPED_TOPIC_SUFFIX}.
     *
     * @param topic the group topic to unsubscribe on.
     * @param subscriber the ActorRef which got messages on the subscribed group topic.
     * @return the message to send the DistributedPubSubMediator.
     * @throws NullPointerException if {@code topic} is {@code null}.
     */
    public static DistributedPubSubMediator.Unsubscribe unsubscribeViaGroup(final String topic, final String group,
            final ActorRef subscriber) {
        return new DistributedPubSubMediator.Unsubscribe(getGroupTopic(topic), group, subscriber);
    }

    /**
     * Puts the passed {@code actorRef} on the distributed pub/sub system so others may send a message to it via
     * {@link #send(String, Object) send(actorPath, message)}.
     *
     * @param actorRef the ActorRef to register on the distributed pub/sub system.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.Put put(final ActorRef actorRef) {
        return new DistributedPubSubMediator.Put(actorRef);
    }

    /**
     * Sends the passed {@code message} to <strong>one of</strong> the ActorRefs at {@code path} previously
     * registered via {@link #put(ActorRef)} by cluster messaging.
     *
     * @param path the ActorRef path to address.
     * @param message the message to send to the ActorRef.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.Send send(final String path, final Object message) {
        return new DistributedPubSubMediator.Send(path, message);
    }

    /**
     * Sends the passed {@code message} to <strong>one of</strong> the ActorRefs at {@code path} previously
     * registered via {@link #put(ActorRef)} by cluster messaging specifying the {@code localAffinity}.
     *
     * @param path the ActorRef path to address.
     * @param message the message to send to the ActorRef.
     * @param localAffinity whether to favor locally running actors of the specified {@code path} when choosing the
     * recipient.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.Send send(final String path, final Object message,
            final boolean localAffinity) {
        return new DistributedPubSubMediator.Send(path, message, localAffinity);
    }

    /**
     * Sends the passed {@code message} to <strong>all of</strong> the ActorRefs at {@code path} previously
     * registered via {@link #put(ActorRef)} by cluster messaging specifying the {@code localAffinity}.
     *
     * @param path the ActorRef path to address.
     * @param message the message to send to the ActorRef.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.SendToAll sendToAll(final String path, final Object message) {
        return new DistributedPubSubMediator.SendToAll(path, message);
    }

    /**
     * Sends the passed {@code message} to <strong>all of</strong> the ActorRefs at {@code path} previously
     * registered via {@link #put(ActorRef)} by cluster messaging specifying whether {@code allButSelf} should be
     * addressed.
     *
     * @param path the ActorRef path to address.
     * @param message the message to send to the ActorRef.
     * @param allButSelf whether a locally running actor should also get the message.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.SendToAll sendToAll(final String path, final Object message,
            final boolean allButSelf) {
        return new DistributedPubSubMediator.SendToAll(path, message, allButSelf);
    }

    /**
     * Removes the passed {@code path} from cluster messaging.
     *
     * @param path the path of the Actor to remove.
     * @return the message to send the DistributedPubSubMediator
     */
    public static DistributedPubSubMediator.Remove remove(final String path) {
        return new DistributedPubSubMediator.Remove(path);
    }

}
