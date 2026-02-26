/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.base.api.common.checkpermissions.CheckPermissions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Signal mapper for {@link CheckPermissions} commands.
 *
 * @since 3.9.0
 */
final class CheckPermissionsSignalMapper extends AbstractSignalMapper<CheckPermissions> {

    private static final String TOPIC_PATH_STRING = "_/_/common/commands/checkPermissions";

    @Override
    TopicPath getTopicPath(final CheckPermissions signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPath(TOPIC_PATH_STRING);
    }

    @Override
    void enhancePayloadBuilder(final CheckPermissions command, final PayloadBuilder payloadBuilder) {
        final JsonObjectBuilder permChecksBuilder = JsonFactory.newObjectBuilder();
        command.getPermissionChecks().forEach((key, value) -> permChecksBuilder.set(key, value.toJson()));
        payloadBuilder.withValue(permChecksBuilder.build());
    }
}
