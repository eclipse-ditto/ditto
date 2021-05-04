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
package org.eclipse.ditto.policies.model.signals.events;

import java.util.Collection;

/**
 * Interface for all policy-action-related events.
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
public interface PolicyActionEvent<T extends PolicyActionEvent<T>> extends PolicyEvent<T> {

    /**
     * Create a policy event that has the same effect as a collection of policy action events together with this event.
     *
     * @param otherPolicyActionEvents the collection of policy action events.
     * @return the aggregated event.
     */
    PolicyEvent<?> aggregateWith(Collection<PolicyActionEvent<?>> otherPolicyActionEvents);
}
