package org.eclipse.ditto.connectivity.service.messaging.hono;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

import akka.actor.ActorSystem;

public class DefaultHonoConnectionFactory extends HonoConnectionFactory {

    private DefaultHonoConnectionFactory(ActorSystem actorSystem, Connection connection) {
        super(actorSystem, connection);
    }

    @Override
    public UserPasswordCredentials getCredentials() {
        return honoConfig.getUserPasswordCredentials();
    }

    @Override
    protected String getTenantId() {
        return "";
    }

    public static Connection getEnrichedConnection(ActorSystem actorSystem, Connection connection) {
        return new DefaultHonoConnectionFactory(actorSystem, connection).enrichConnection();
    }
}
