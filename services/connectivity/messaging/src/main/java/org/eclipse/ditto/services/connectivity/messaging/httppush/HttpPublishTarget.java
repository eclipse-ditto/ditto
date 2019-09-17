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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;

final class HttpPublishTarget implements PublishTarget {

    private final String path;

    private HttpPublishTarget(final String path) {
        this.path = path;
    }

    String[] getPathSegments() {
        return path.split("/");
    }

    static HttpPublishTarget of(final String path) {
        return new HttpPublishTarget(path);
    }
}
