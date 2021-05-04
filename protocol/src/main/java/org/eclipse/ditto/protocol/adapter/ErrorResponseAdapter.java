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
package org.eclipse.ditto.protocol.adapter;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;

/**
 * An {@code Adapter} mixin for error responses.
 *
 * @param <T> the type mapped by this {@code Adapter}.
 * @since 1.1.0
 */
public interface ErrorResponseAdapter<T extends ErrorResponse<?>> extends Adapter<T> {

    @Override
    default Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.ERRORS);
    }

    @Override
    default Set<TopicPath.Action> getActions() {
        return EnumSet.allOf(TopicPath.Action.class);
    }

    @Override
    default boolean isForResponses() {
        return true;
    }
}
