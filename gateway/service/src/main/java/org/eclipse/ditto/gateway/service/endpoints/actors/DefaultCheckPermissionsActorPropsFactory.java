package org.eclipse.ditto.gateway.service.endpoints.actors;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;

import com.typesafe.config.Config;

import java.time.Duration;

/*
 * Default creator of Props of CheckPermissions actors.
 */
public final class DefaultCheckPermissionsActorPropsFactory implements CheckPermissionsActorPropsFactory {

    private DefaultCheckPermissionsActorPropsFactory(final ActorSystem actorSystem, final Config config) {
        // NoOp Constructor to match extension instantiation
    }


    @Override
    public Props getActorProps(final ActorRef edgeCommandForwarder,
            final ActorRef sender, final Duration defaultTimeout) {
        return CheckPermissionsActor.props(edgeCommandForwarder, sender, defaultTimeout);
    }

}