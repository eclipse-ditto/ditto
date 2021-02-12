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
package org.eclipse.ditto.protocoladapter.signals;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownSignalException;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;
import org.eclipse.ditto.signals.notifications.policies.SubjectExpiryNotification;

final class PolicyNotificationSignalMapper extends AbstractSignalMapper<PolicyNotification<?>> {

    @Override
    TopicPath getTopicPath(final PolicyNotification<?> signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPathBuilder(signal.getEntityId())
                .policies()
                .notifications()
                .name(signal.getName())
                .build();
    }

    @Override
    void enhancePayloadBuilder(final PolicyNotification<?> signal, final PayloadBuilder payloadBuilder) {
        if (signal instanceof SubjectExpiryNotification) {
            final SubjectExpiryNotification subjectExpiryNotification = (SubjectExpiryNotification) signal;
            final JsonObject payload = getSubjectExpiryNotificationPayload(subjectExpiryNotification);
            payloadBuilder.withValue(payload).build();
        } else {
            throw UnknownSignalException.newBuilder(signal.getType())
                    .dittoHeaders(signal.getDittoHeaders())
                    .build();
        }
    }

    private static JsonObject getSubjectExpiryNotificationPayload(final SubjectExpiryNotification notification) {
        return JsonObject.newBuilder()
                .set(SubjectExpiryNotification.JsonFields.EXPIRY, notification.getExpiry().toString())
                .set(SubjectExpiryNotification.JsonFields.EXPIRING_SUBJECTS, notification.getExpiringSubjectIds()
                        .stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }
}
