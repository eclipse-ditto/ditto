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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Policy-related test constants.
     */
    public static final class Policy {

        /**
         * The known type.
         */
        public static final String TYPE = "things";

        /**
         * The known "read" permission.
         */
        public static final String PERMISSION_READ = "READ";

        /**
         * The known "write" permission.
         */
        public static final String PERMISSION_WRITE = "WRITE";

        /**
         * A known Subject Id subject.
         */
        public static final String SUBJECT_ID_SUBJECT = "LukeSkywalker";

        /**
         * A known Subject Id.
         */
        public static final SubjectId SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ID_SUBJECT);

        /**
         * A known resource type.
         */
        public static final String RESOURCE_TYPE = "thing";

        /**
         * A known resource path.
         */
        public static final JsonPointer RESOURCE_PATH = JsonPointer.of("/foo/bar");

        /**
         * A known resource path char sequence.
         */
        public static final ResourceKey RESOURCE_KEY = ResourceKey.newInstance(RESOURCE_TYPE, RESOURCE_PATH);

        /**
         * All known permissions.
         */
        public static final Iterable<String> PERMISSIONS_ALL = Arrays.asList(PERMISSION_READ, PERMISSION_WRITE);

        private Policy() {
            throw new AssertionError();
        }
    }

}
