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
package org.eclipse.ditto.model.policies;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonPointer;

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
         * A known Subject Issuer.
         */
        public static final SubjectIssuer SUBJECT_ISSUER = SubjectIssuer.GOOGLE;

        /**
         * A known Subject Id.
         */
        public static final SubjectId SUBJECT_ID = SubjectId.newInstance(SUBJECT_ISSUER, SUBJECT_ID_SUBJECT);

        /**
         * A known Subject Type.
         */
        public static final SubjectType SUBJECT_TYPE = SubjectType.newInstance("testSubjectType");

        /**
         * A known Subject.
         */
        public static final Subject SUBJECT = Subject.newInstance(SUBJECT_ID, SUBJECT_TYPE);

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
