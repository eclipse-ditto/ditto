/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

/**
 * Contains the Policy framework around the cornerstone of this package: {@link org.eclipse.ditto.model.policies.Policy}.
 * A Policy consists of {@link org.eclipse.ditto.model.policies.PolicyEntry}s.
 * <p>
 * One {@link org.eclipse.ditto.model.policies.PolicyEntry} is identified by a {@link org.eclipse.ditto.model.policies.Label} and
 * contains {@link org.eclipse.ditto.model.policies.Subjects} and {@link org.eclipse.ditto.model.policies.Resources}. The first
 * is a list of {@link org.eclipse.ditto.model.policies.Subject}'s that define for whom this entry will apply and the later
 * defines the single {@link org.eclipse.ditto.model.policies.Resource}'s together with the granted or revoked permissions
 * for the given subjects.
 *
 * <h3>Object creation</h3>
 * {@link org.eclipse.ditto.model.policies.PoliciesModelFactory} is the main entry point for obtaining objects of this
 * package's interfaces to work with.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.model.policies;
