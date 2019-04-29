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
 * Contains the Things framework around the cornerstone of this package: {@link org.eclipse.ditto.model.things.Thing}.
 * A Thing can consist of
 * <ul>
 * <li>an {@link org.eclipse.ditto.model.things.AccessControlList} which contains the
 * {@link org.eclipse.ditto.model.things.Permission}s of {@link org.eclipse.ditto.model.base.auth.AuthorizationSubject
 * AuthorizationSubject}s on that Thing,</li>
 * <li>{@link org.eclipse.ditto.model.things.Attributes} for describing the Thing in more details,</li>
 * <li>and {@link org.eclipse.ditto.model.things.Features} for managing all data and functionality of the Thing.</li>
 * </ul>
 * <p>
 * Furthermore a Thing can have a {@link org.eclipse.ditto.model.things.ThingLifecycle} and a
 * {@link org.eclipse.ditto.model.things.ThingRevision} which is incremented with each change of the Thing.
 *
 * <h3>Object creation</h3>
 * {@link org.eclipse.ditto.model.things.ThingsModelFactory} is the main entry point for obtaining objects of this
 * package's interfaces to work with.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault
package org.eclipse.ditto.model.things;
