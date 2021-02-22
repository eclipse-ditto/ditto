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
package org.eclipse.ditto.protocoladapter.provider;

import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.announcements.policies.PolicyAnnouncement;

/**
 * Provider for all policy command adapters. This interface mainly defines the generic type arguments and adds
 * default methods for unsupported command types.
 *
 * @since 1.1.0
 */
public interface PolicyCommandAdapterProvider
        extends QueryCommandAdapterProvider<PolicyQueryCommand<?>, PolicyQueryCommandResponse<?>>,
        ModifyCommandAdapterProvider<PolicyModifyCommand<?>, PolicyModifyCommandResponse<?>>,
        ErrorResponseAdapterProvider<PolicyErrorResponse>,
        AdapterProvider {

    /**
     * Retrieve the announcement adapter.
     *
     * @return the announcement adapter.
     */
    Adapter<PolicyAnnouncement<?>> getAnnouncementAdapter();
}
