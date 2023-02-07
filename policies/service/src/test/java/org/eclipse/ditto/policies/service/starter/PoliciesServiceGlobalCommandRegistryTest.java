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
package org.eclipse.ditto.policies.service.starter;

import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.purge.PurgeEntities;
import org.eclipse.ditto.base.api.devops.signals.commands.ExecutePiggybackCommand;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespace;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.base.service.cluster.ModifySplitBrainResolver;
import org.eclipse.ditto.internal.models.streaming.SudoStreamPids;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.test.GlobalCommandRegistryTestCases;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.SudoDeleteExpiredSubject;

public final class PoliciesServiceGlobalCommandRegistryTest extends GlobalCommandRegistryTestCases {

    public PoliciesServiceGlobalCommandRegistryTest() {
        super(
                SudoStreamPids.class,
                SudoRetrievePolicy.class,
                SudoDeleteExpiredSubject.class,
                ExecutePiggybackCommand.class,
                Shutdown.class,
                PurgeNamespace.class,
                RetrieveResource.class,
                DeleteSubject.class,
                ActivateTokenIntegration.class,
                CleanupPersistence.class,
                RetrieveHealth.class,
                PurgeEntities.class,
                PublishSignal.class,
                ModifyPolicyImports.class,
                ModifySplitBrainResolver.class,
                SubscribeForPersistedEvents.class
        );
    }
}
