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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionEvent;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

/**
 * Factory class that instantiates all available {@link SignalMapper}s.
 *
 * @since 1.1.0
 */
public final class SignalMapperFactory {

    private SignalMapperFactory() {
        throw new AssertionError();
    }

    public static SignalMapper<ThingModifyCommand<?>> newThingModifySignalMapper() {
        return new ThingModifySignalMapper();
    }

    public static SignalMapper<ThingModifyCommandResponse<?>> newThingModifyResponseSignalMapper() {
        return new ThingModifyResponseSignalMapper();
    }

    public static SignalMapper<ThingEvent<?>> newThingEventSignalMapper() {
        return new ThingEventSignalMapper();
    }

    public static SignalMapper<MergeThing> newThingMergeSignalMapper() {
        return new ThingMergeSignalMapper();
    }

    public static SignalMapper<MergeThingResponse> newThingMergeResponseSignalMapper() {
        return new ThingMergeResponseSignalMapper();
    }

    public static SignalMapper<ThingMerged> newThingMergedEventSignalMapper() {
        return new ThingMergedEventSignalMapper();
    }

    public static SignalMapper<ThingQueryCommand<?>> newThingQuerySignalMapper() {
        return new ThingQuerySignalMapper();
    }

    public static SignalMapper<ThingQueryCommandResponse<?>> newThingQueryResponseSignalMapper() {
        return new ThingQueryResponseSignalMapper();
    }

    public static SignalMapper<SubscriptionEvent<?>> newSubscriptionEventSignalMapper() {
        return new SubscriptionEventSignalMapper();
    }

    public static SignalMapper<StreamingSubscriptionCommand<?>> newStreamingSubscriptionCommandSignalMapper() {
        return new StreamingSubscriptionCommandSignalMapper<>();
    }

    public static SignalMapper<StreamingSubscriptionEvent<?>> newStreamingSubscriptionEventSignalMapper() {
        return new StreamingSubscriptionEventSignalMapper();
    }

    public static SignalMapper<RetrieveThings> newRetrieveThingsSignalMapper() {
        return new RetrieveThingsSignalMapper();
    }

    public static SignalMapper<RetrieveThingsResponse> newRetrieveThingsResponseSignalMapper() {
        return new RetrieveThingsResponseSignalMapper();
    }

    public static SignalMapper<ThingSearchCommand<?>> newThingSearchSignalMapper() {
        return new ThingSearchSignalMapper<>();
    }

    public static SignalMapper<PolicyModifyCommand<?>> newPolicyModifySignalMapper() {
        return new PolicyModifySignalMapper();
    }

    public static SignalMapper<PolicyModifyCommandResponse<?>> newPolicyModifyResponseSignalMapper() {
        return new PolicyModifyResponseSignalMapper();
    }

    public static SignalMapper<PolicyQueryCommand<?>> newPolicyQuerySignalMapper() {
        return new PolicyQuerySignalMapper();
    }

    public static SignalMapper<PolicyQueryCommandResponse<?>> newPolicyQueryResponseSignalMapper() {
        return new PolicyQueryResponseSignalMapper();
    }

    public static SignalMapper<PolicyAnnouncement<?>> newPolicyAnnouncementSignalMapper() {
        return new PolicyAnnouncementSignalMapper();
    }

    public static SignalMapper<PolicyEvent<?>> newPolicyEventSignalMapper() {
        return new PolicyEventSignalMapper();
    }

    public static SignalMapper<MessageCommand<?, ?>> newMessageCommandSignalMapper() {
        return MessageSignalMapper.getInstance();
    }

    public static SignalMapper<MessageCommandResponse<?, ?>> newMessageCommandResponseSignalMapper() {
        return MessageSignalMapper.getInstance();
    }

    public static SignalMapper<ConnectivityAnnouncement<?>> newConnectivityAnnouncementSignalMapper() {
        return new ConnectivityAnnouncementSignalMapper();
    }

}
