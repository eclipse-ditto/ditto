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
package org.eclipse.ditto.policies.model.signals.commands.actions;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Aggregates all PolicyCommandResponses returned as result of a {@link org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand}.
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
public interface PolicyActionCommandResponse<T extends PolicyActionCommandResponse<T>> extends
        PolicyCommandResponse<T>, WithOptionalEntity {

    /**
     * Path of Policy actions as part of the the {@link #getResourcePath()}.
     */
    JsonPointer RESOURCE_PATH_ACTIONS = PolicyActionCommand.RESOURCE_PATH_ACTIONS;
}
