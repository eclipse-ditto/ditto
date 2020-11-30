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
package org.eclipse.ditto.services.utils.pubsub.ddata.literal;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriberData;
import org.eclipse.ditto.services.utils.pubsub.ddata.TopicData;

import akka.actor.ActorRef;

/**
 * Local subscriptions for distribution of declared acknowledgement labels as string literals.
 */
@NotThreadSafe
public final class LiteralSubscriptions extends AbstractSubscriptions<String, LiteralUpdate> {

    private LiteralSubscriptions(
            final Map<ActorRef, SubscriberData> subscriberDataMap,
            final Map<String, TopicData> topicToData) {
        super(subscriberDataMap, topicToData);
    }

    /**
     * Create a new subscriptions object.
     *
     * @return the subscriptions object.
     */
    public static LiteralSubscriptions newInstance() {
        return new LiteralSubscriptions(new HashMap<>(), new HashMap<>());
    }

    @Override
    public long estimateSize() {
        return subscriberDataMap.values()
                .stream()
                .mapToLong(subscriberData -> {
                    // value and group bytes estimated by string length because both should consist of ASCII characters
                    final long valueBytes = subscriberData.getTopics().stream().mapToLong(String::length).sum();
                    final long groupBytes = subscriberData.getGroup().map(String::length).orElse(0);
                    return valueBytes + groupBytes;
                })
                .sum();
    }

    @Override
    public LiteralUpdate export() {
        return LiteralUpdate.replaceAll(topicDataMap.keySet());
    }

}
