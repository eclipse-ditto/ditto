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
package org.eclipse.ditto.services.things.starter;

import org.eclipse.ditto.services.models.streaming.SudoStreamPids;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.services.utils.test.GlobalCommandRegistryTestCases;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntities;
import org.eclipse.ditto.signals.commands.devops.ExecutePiggybackCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.signals.commands.policies.actions.ActivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;

public final class ThingsServiceGlobalCommandRegistryTest extends GlobalCommandRegistryTestCases {

    public ThingsServiceGlobalCommandRegistryTest() {
        super(SudoStreamPids.class,
                SudoRetrieveThing.class,
                RetrieveFeature.class,
                ModifyFeatureProperty.class,
                ExecutePiggybackCommand.class,
                SendClaimMessage.class,
                Shutdown.class,
                PurgeNamespace.class,
                RetrieveResource.class,
                DeleteSubject.class,
                ActivateTokenIntegration.class,
                CleanupPersistence.class,
                RetrieveHealth.class,
                PurgeEntities.class,
                PublishSignal.class
        );
    }

}
