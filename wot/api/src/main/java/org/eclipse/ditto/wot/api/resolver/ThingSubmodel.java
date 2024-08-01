/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.api.resolver;

import org.eclipse.ditto.wot.model.IRI;

/**
 * Bundles the {@code instanceName} and the {@code href} of a {@code tm:submodel} contained in the links of a
 * ThingModel.
 *
 * @param instanceName the instance name of the submodel, translates to the "feature ID" in Ditto
 * @param href the link where the submodel's TM is defined
 */
public record ThingSubmodel(String instanceName, IRI href) {
}
