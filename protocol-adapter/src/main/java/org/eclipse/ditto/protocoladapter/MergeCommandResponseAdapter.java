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
package org.eclipse.ditto.protocoladapter;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;

/**
 * An {@code Adapter} mixin for merge command responses.
 *
 * TODO adapt @since annotation @since 1.6.0
 */
public interface MergeCommandResponseAdapter extends Adapter<MergeThingResponse> {

    @Override
    default Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.COMMANDS);
    }

    @Override
    default Set<TopicPath.Action> getActions() {
        return EnumSet.of(TopicPath.Action.MERGE);
    }

    @Override
    default boolean isForResponses() {
        return true;
    }
}
