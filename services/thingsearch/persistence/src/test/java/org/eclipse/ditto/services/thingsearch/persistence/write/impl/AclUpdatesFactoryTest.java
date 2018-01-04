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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.junit.Test;

/**
 * Test for AclUpdatesFactory.
 */
public final class AclUpdatesFactoryTest {


    @Test
    public void createUpdateAclEntries() {
        final List<Bson> updates = AclUpdatesFactory.createUpdateAclEntries(TestConstants.Thing.ACL);
        assertThat(updates.size())
                .isEqualTo(2);
    }

    @Test
    public void createUpdateAclEntry() {
        final List<Bson> updates = AclUpdatesFactory.createUpdateAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES);
        assertThat(updates.size())
                .isEqualTo(2);
    }

    @Test
    public void deleteAclEntry() {
        final Bson delete = AclUpdatesFactory.deleteAclEntry("anyId");
        assertThat(delete)
                .isNotNull();
    }

}