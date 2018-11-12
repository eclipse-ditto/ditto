/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.suffixes;

import akka.contrib.persistence.mongodb.CanSuffixCollectionNames;

/**
 * Class that does nothing for configuring Akka persistence MongoDB plugin suffix builder to do nothing.
 */
@SuppressWarnings("unused")
public final class NamespaceSuffixCollectionNamesDisabled implements CanSuffixCollectionNames {

    @Override
    public String getSuffixFromPersistenceId(final String persistenceId) {
        return "";
    }

    @Override
    public String validateMongoCharacters(final String input) {
        return NamespaceSuffixCollectionNames.doValidateMongoCharacters(input);
    }
}
