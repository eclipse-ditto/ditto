/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.starter;

import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.purge.PurgeEntities;
import org.eclipse.ditto.base.api.devops.signals.commands.ExecutePiggybackCommand;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespace;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.base.service.cluster.ModifySplitBrainResolver;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoAddConnectionLogEntry;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionIdsByTag;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.internal.models.streaming.SudoStreamPids;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.test.GlobalCommandRegistryTestCases;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;

public final class GatewayServiceGlobalCommandRegistryTest extends GlobalCommandRegistryTestCases {

    public GatewayServiceGlobalCommandRegistryTest() {
        super(
                SudoStreamPids.class,
                SudoRetrieveThing.class,
                SudoRetrievePolicy.class,
                SudoCountThings.class,
                QueryThings.class,
                CreateSubscription.class,
                RetrieveConnection.class,
                OpenConnection.class,
                SudoRetrieveConnectionIdsByTag.class,
                RetrieveFeature.class,
                ModifyFeatureProperty.class,
                ExecutePiggybackCommand.class,
                SendClaimMessage.class,
                Shutdown.class,
                PurgeNamespace.class,
                RetrieveResource.class,
                DeleteSubject.class,
                ActivateTokenIntegration.class,
                RetrieveHealth.class,
                PurgeEntities.class,
                Whoami.class,
                PublishSignal.class,
                ModifySplitBrainResolver.class,
                CleanupPersistence.class,
                SudoAddConnectionLogEntry.class,
                SubscribeForPersistedEvents.class
        );
    }

}
