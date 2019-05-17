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
package org.eclipse.ditto.services.utils.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ConfigUtilTest {

    @Test
    public void transformSecretsAccordingToNameMapWithIdentityFallback() throws Exception {
        final String secretResourcePath = new File(getClass().getResource("/secret").toURI()).getPath();

        final Config secretNameMap =
                ConfigFactory.parseString("secret_names.devops_password=\"devops_password_50021\"");

        final Config secretConfig = ConfigUtil.transformSecretsToConfig(secretResourcePath, secretNameMap);

        assertThat(secretConfig.getString("secrets.devops_password")).isEqualTo("devops_password_50021_content");
        assertThat(secretConfig.getString("secrets.not_renamed_secret")).isEqualTo("not_renamed_secret_content");
    }

}
