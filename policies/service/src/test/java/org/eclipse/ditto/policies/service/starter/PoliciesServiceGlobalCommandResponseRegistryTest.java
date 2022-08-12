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

import org.eclipse.ditto.base.api.common.RetrieveConfigResponse;
import org.eclipse.ditto.base.api.common.purge.PurgeEntitiesResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveLoggerConfigResponse;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.base.model.namespaces.signals.commands.PurgeNamespaceResponse;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.service.cluster.ModifySplitBrainResolverResponse;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.test.GlobalCommandResponseRegistryTestCases;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegrationResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportsResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResourceResponse;

public final class PoliciesServiceGlobalCommandResponseRegistryTest extends GlobalCommandResponseRegistryTestCases {

    public PoliciesServiceGlobalCommandResponseRegistryTest() {
        super(
                SudoRetrievePolicyResponse.class,
                PurgeNamespaceResponse.class,
                RetrieveResourceResponse.class,
                DeleteSubjectResponse.class,
                ActivateTokenIntegrationResponse.class,
                PolicyErrorResponse.class,
                RetrieveLoggerConfigResponse.class,
                CleanupPersistenceResponse.class,
                RetrieveConfigResponse.class,
                RetrieveHealthResponse.class,
                PurgeEntitiesResponse.class,
                ModifyPolicyImportsResponse.class,

                Acknowledgement.class,
                ModifySplitBrainResolverResponse.class
        );
    }

}
