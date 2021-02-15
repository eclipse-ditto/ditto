/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;
import org.eclipse.ditto.signals.notifications.policies.SubjectExpiryNotification;

/**
 * Package-private extractor of policy notification topics.
 */
final class PolicyNotificationTopicExtractor implements PubSubTopicExtractor<PolicyNotification<?>> {

    @Override
    public Collection<String> getTopics(final PolicyNotification<?> message) {
        if (message instanceof SubjectExpiryNotification) {
            final SubjectExpiryNotification notification = (SubjectExpiryNotification) message;
            return notification.getExpiringSubjectIds()
                    .stream()
                    .map(SubjectId::toString)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
