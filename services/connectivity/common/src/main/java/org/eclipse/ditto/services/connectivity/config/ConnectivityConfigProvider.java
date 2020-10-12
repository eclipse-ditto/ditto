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

/*
 * Copyright Bosch.IO GmbH 2020
 *
 * All rights reserved, also regarding any disposal, exploitation,
 * reproduction, editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch.IO GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch.IO GmbH.
 */

package org.eclipse.ditto.services.connectivity.config;


import java.util.concurrent.CompletionStage;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.actor.ActorRef;

/**
 * TODO DG
 */
@IndexSubclasses
public interface ConnectivityConfigProvider {

    ConnectivityConfig getConnectivityConfig(EntityId connectionId);

    ConnectivityConfig getConnectivityConfig(DittoHeaders dittoHeaders);

    CompletionStage<ConnectivityConfig> getConnectivityConfigAsync(EntityId connectionId);

    CompletionStage<ConnectivityConfig> getConnectivityConfigAsync(DittoHeaders dittoHeaders);

    void registerForChanges(final EntityId connectionId, final ActorRef sender);
}
