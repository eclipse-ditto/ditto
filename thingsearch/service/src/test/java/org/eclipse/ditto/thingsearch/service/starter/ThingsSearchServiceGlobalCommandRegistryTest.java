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
package org.eclipse.ditto.thingsearch.service.starter;

import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.streaming.SudoStreamPids;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.services.utils.test.GlobalCommandRegistryTestCases;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntities;
import org.eclipse.ditto.signals.commands.devops.ExecutePiggybackCommand;
import org.eclipse.ditto.model.messages.signals.commands.SendClaimMessage;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespace;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;

public final class ThingsSearchServiceGlobalCommandRegistryTest extends GlobalCommandRegistryTestCases {

    public ThingsSearchServiceGlobalCommandRegistryTest() {
        super(SudoStreamPids.class,
                SudoRetrieveThing.class,
                SudoRetrievePolicy.class,
                SudoCountThings.class,
                QueryThings.class,
                CreateSubscription.class,
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
                PublishSignal.class
        );
    }

}
