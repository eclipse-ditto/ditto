/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
