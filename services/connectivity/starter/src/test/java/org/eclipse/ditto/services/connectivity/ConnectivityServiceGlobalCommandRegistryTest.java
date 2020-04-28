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
package org.eclipse.ditto.services.connectivity;

import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.streaming.SudoStreamPids;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.test.GlobalCommandRegistryTestCases;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntities;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.devops.ExecutePiggybackCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;

public class ConnectivityServiceGlobalCommandRegistryTest extends GlobalCommandRegistryTestCases {

    public ConnectivityServiceGlobalCommandRegistryTest() {
        super(
                SudoStreamPids.class,
                SudoRetrieveThing.class,
                SudoRetrievePolicy.class,
                SudoCountThings.class,
                QueryThings.class,
                CreateSubscription.class,
                RetrieveConnection.class,
                OpenConnection.class,
                RetrieveFeature.class,
                ModifyFeatureProperty.class,
                ExecutePiggybackCommand.class,
                SendClaimMessage.class,
                Shutdown.class,
                PurgeNamespace.class,
                RetrieveResource.class,
                DeleteSubject.class,
                CleanupPersistence.class,
                RetrieveHealth.class,
                PurgeEntities.class
        );
    }

    @Override
    protected boolean isExcluded(final Class<?> clazz) {
        return clazz == StagedCommand.class;
    }
}
