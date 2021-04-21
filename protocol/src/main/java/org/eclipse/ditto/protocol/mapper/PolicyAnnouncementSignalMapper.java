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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;

final class PolicyAnnouncementSignalMapper extends AbstractSignalMapper<PolicyAnnouncement<?>> {

    @Override
    TopicPath getTopicPath(final PolicyAnnouncement<?> signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPathBuilder(signal.getEntityId())
                .policies()
                .announcements()
                .name(signal.getName())
                .build();
    }

    @Override
    void enhancePayloadBuilder(final PolicyAnnouncement<?> signal, final PayloadBuilder payloadBuilder) {
        if (signal instanceof SubjectDeletionAnnouncement) {
            final SubjectDeletionAnnouncement announcement = (SubjectDeletionAnnouncement) signal;
            final JsonObject payload = getSubjectDeletionAnnouncementPayload(announcement);
            payloadBuilder.withValue(payload).build();
        }
        // otherwise be tolerant and don't expand payload instead of throwing an exception
    }

    private static JsonObject getSubjectDeletionAnnouncementPayload(final SubjectDeletionAnnouncement announcement) {
        return JsonObject.newBuilder()
                .set(SubjectDeletionAnnouncement.JsonFields.DELETE_AT, announcement.getDeleteAt().toString())
                .set(SubjectDeletionAnnouncement.JsonFields.SUBJECT_IDS, announcement.getSubjectIds()
                        .stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }
}
