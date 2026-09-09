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
import { UserManager, UserManagerSettings } from 'oidc-client-ts';

/*
 * Entry point for silent-callback.html, the page oidc-client-ts loads in a hidden iframe as
 * `silent_redirect_uri` when renewing an access token without a refresh token.
 *
 * The page's only job is to hand the response URL back to the parent frame;
 * `signinSilentCallback()` delegates to IFrameNavigator.callback(), which reads nothing from
 * the settings except the optional `iframeNotifyParentOrigin` (defaulting to this page's own
 * origin). Hence the placeholder settings below: `authority` and `client_id` are required by
 * UserManagerSettings but are never touched on this path.
 *
 * Passing an object at all is what matters - `new UserManager()` throws, because
 * UserManagerSettingsStore dereferences `args.redirect_uri` before any defaulting.
 */
const callbackOnlySettings: UserManagerSettings = {
  authority: '',
  client_id: '',
};

new UserManager(callbackOnlySettings)
    .signinSilentCallback()
    .catch((error) => {
      // Nothing is recoverable from inside the iframe: oidc-client-ts times the silent
      // request out and raises a SilentRenewError on the UserManager that started it.
      console.error('Silent refresh callback failed:', error);
    });
