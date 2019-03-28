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
package org.eclipse.ditto.services.thingsearch.starter;

import java.util.Arrays;

import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoRetrieveNamespaceReportResponse;
import org.eclipse.ditto.services.utils.test.GlobalCommandResponseRegistryTestCases;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatchResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfigResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.namespaces.PurgeNamespaceResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.thingsearch.SearchErrorResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

public class ThingsSearchServiceGlobalCommandResponseRegistryTest extends GlobalCommandResponseRegistryTestCases {

    public ThingsSearchServiceGlobalCommandResponseRegistryTest () {
        super(Arrays.asList(
                SudoRetrieveThingResponse.class,
                SudoRetrievePolicyResponse.class,
                QueryThingsResponse.class,
                ExecuteBatchResponse.class,
                RetrieveFeatureResponse.class,
                ModifyFeaturePropertyResponse.class,
                SendClaimMessageResponse.class,
                PurgeNamespaceResponse.class,
                RetrieveResourceResponse.class,
                DeleteSubjectResponse.class,
                SearchErrorResponse.class,
                ThingErrorResponse.class,
                PolicyErrorResponse.class,
                RetrieveLoggerConfigResponse.class,
                SudoRetrieveNamespaceReportResponse.class
        ));
    }
}
