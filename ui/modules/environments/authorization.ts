/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import * as API from '../api.js';
import * as Utils from '../utils.js';
import { showError, showInfoToast } from '../utils.js';
import authorizationHTML from './authorization.html';
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as Environments from './environments.js';
import { AuthMethod, OidcAuthSettings, URL_OIDC_PROVIDER, URL_PRIMARY_ENVIRONMENT_NAME } from './environments.js';

let dom = {
  mainBearerSection: null,
  bearer: null,
  mainBasicSection: null,
  userName: null,
  password: null,
  devOpsBearerSection: null,
  bearerDevOps: null,
  devOpsBasicSection: null,
  devOpsUserName: null,
  devOpsPassword: null,
  mainPreAuthenticatedSection: null,
  dittoPreAuthenticatedUsername: null,
  mainOidcSection: null,
  oidcProvider: null,
  devOpsOidcSection: null,
  devOpsOidcProvider: null,
  collapseConnections: null,
};

let _forDevops = false;

document.getElementById('authorizationHTML').innerHTML = authorizationHTML;

export function setForDevops(forDevops: boolean) {
  _forDevops = forDevops;
  API.setAuthHeader(_forDevops);
}

export function ready() {
  Utils.getAllElementsById(dom);

  document.getElementById('authorize').onclick = () => {
    let environment = Environments.current();
    let mainAuthMethod = environment.authSettings?.main?.method;
    let devopsAuthMethod = environment.authSettings?.devops?.method;

    if (!mainAuthMethod) {
      if (dom.dittoPreAuthenticatedUsername.value && dom.dittoPreAuthenticatedUsername.value.length > 0) {
        mainAuthMethod = AuthMethod.pre;
      } else if (dom.bearer.value && dom.bearer.value.length > 0) {
        mainAuthMethod = AuthMethod.bearer;
      } else if (dom.userName.value && dom.userName.value.length > 0) {
        mainAuthMethod = AuthMethod.basic;
      }
    }
    if (!devopsAuthMethod) {
      if (dom.bearerDevOps.value && dom.bearerDevOps.value.length > 0) {
        devopsAuthMethod = AuthMethod.bearer;
      } else if (dom.devOpsUserName.value && dom.devOpsUserName.value.length > 0) {
        devopsAuthMethod = AuthMethod.basic;
      }
    }

    Array.from(document.querySelectorAll('input[name="main-auth-method"]')).forEach((inputAuth: HTMLInputElement) => {
      inputAuth.checked = AuthMethod[inputAuth.value as keyof typeof AuthMethod] === mainAuthMethod;
    });

    Array.from(document.querySelectorAll('input[name="devops-auth-method"]')).forEach((inputAuth: HTMLInputElement) => {
      inputAuth.checked = AuthMethod[inputAuth.value as keyof typeof AuthMethod] === devopsAuthMethod;
    });
  };

  document.getElementById('authorizeSubmit').onclick = async (e) => {
    e.preventDefault();
    const mainAuthSelector = document.querySelector('input[name="main-auth-method"]:checked') as HTMLInputElement;
    const mainAuthMethod = mainAuthSelector ? mainAuthSelector.value : undefined;
    const devopsAuthSelector = document.querySelector('input[name="devops-auth-method"]:checked') as HTMLInputElement;
    const devopsAuthMethod = devopsAuthSelector ? devopsAuthSelector.value : undefined;

    let environment = Environments.current();
    environment.authSettings.main.method = AuthMethod[mainAuthMethod as keyof typeof AuthMethod];
    environment.authSettings.devops.method = AuthMethod[devopsAuthMethod as keyof typeof AuthMethod];
    environment.authSettings.main.basic.usernamePassword = dom.userName.value + ':' + dom.password.value;
    environment.authSettings.devops.basic.usernamePassword = dom.devOpsUserName.value + ':' + dom.devOpsPassword.value;
    environment.authSettings.main.bearer.bearerToken = dom.bearer.value;
    environment.authSettings.devops.bearer.bearerToken = dom.bearerDevOps.value;
    environment.authSettings.main.pre.dittoPreAuthenticatedUsername = dom.dittoPreAuthenticatedUsername.value;
    environment.authSettings.main.oidc.defaultProvider = dom.oidcProvider.value;
    environment.authSettings.devops.oidc.defaultProvider = dom.devOpsOidcProvider.value;
    await Environments.environmentsJsonChanged(false);
  };

  document.getElementById('main-oidc-login').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.main.oidc.provider = dom.oidcProvider.value;
    let alreadyLoggedIn = await performSingleSignOn(environment.authSettings.main.oidc)
    if (alreadyLoggedIn) {
      showInfoToast('You are already logged in')
    }
    await Environments.environmentsJsonChanged(false);
  };
  document.getElementById('main-oidc-logout').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.main.oidc.provider = dom.oidcProvider.value;
    await performSingleSignOut(environment.authSettings.main.oidc)
    await Environments.environmentsJsonChanged(false);
  };

  document.getElementById('devops-oidc-login').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.devops.oidc.provider = dom.devOpsOidcProvider.value;
    let alreadyLoggedIn = await performSingleSignOn(environment.authSettings.devops.oidc)
    if (alreadyLoggedIn) {
      showInfoToast('You are already logged in')
    }
    await Environments.environmentsJsonChanged(false);
  };
  document.getElementById('devops-oidc-logout').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.devops.oidc.provider = dom.devOpsOidcProvider.value;
    await performSingleSignOut(environment.authSettings.devops.oidc)
    await Environments.environmentsJsonChanged(false);
  };
}

function isSsoCallbackRequest(urlSearchParams?: URLSearchParams): boolean {
  if (urlSearchParams === undefined) {
    urlSearchParams = new URLSearchParams(window.location.search)
  }
  const requestContainedCode: boolean = urlSearchParams.get('code') !== null
  const requestContainedState: boolean = urlSearchParams.get('state') !== null
  return requestContainedCode && requestContainedState;
}

async function handleSingleSignOnCallback(oidc: OidcAuthSettings) {
  let environment = Environments.current();
  const settings: UserManagerSettings = environment.authSettings.oidc.providers[oidc.provider];
  if (settings !== undefined && settings !== null) {
    const userManager = new UserManager(settings);
    try {
      let user = await userManager.signinCallback(window.location.href)
      if (user) {
        oidc.bearerToken = user.access_token
        window.history.replaceState(null, null, `${settings.redirect_uri}?${user.url_state}`)
        await Environments.environmentsJsonChanged(false)
      }
    } catch (e) {
      console.error(`Could not login due to: ${e}`)
    }
  }
}

async function performSingleSignOn(oidc: OidcAuthSettings): Promise<boolean> {
  let environment = Environments.current();
  const settings: UserManagerSettings = environment.authSettings.oidc.providers[oidc.provider];
  if (settings !== undefined && settings !== null) {
    const urlSearchParams: URLSearchParams = new URLSearchParams(window.location.search);
    const userManager = new UserManager(settings);
    if (isSsoCallbackRequest(urlSearchParams)) {
      await handleSingleSignOnCallback(oidc)
      return false
    } else {
      let user = await userManager.getUser();
      if (user?.access_token !== undefined || user?.expired === true) {
        // a user is still logged in via a valid token stored in the browser's session storage
        oidc.bearerToken = user?.access_token
        return true
      } else {
        urlSearchParams.set(URL_PRIMARY_ENVIRONMENT_NAME, Environments.currentEnvironmentSelector())
        urlSearchParams.set(URL_OIDC_PROVIDER, oidc.provider)
        try {
          await userManager.signinRedirect({
            url_state: urlSearchParams.toString()
          });
        } catch (e) {
          showError(e)
          return false
        }
      }
    }
  }
}

async function performSingleSignOut(oidc: OidcAuthSettings) {
  let environment = Environments.current();
  const settings: UserManagerSettings = environment.authSettings.oidc.providers[oidc.provider];
  if (settings !== undefined && settings !== null) {
    const userManager = new UserManager(settings);
    try {
      const urlSearchParams: URLSearchParams = new URLSearchParams(window.location.search);
      urlSearchParams.set(URL_PRIMARY_ENVIRONMENT_NAME, Environments.currentEnvironmentSelector());
      let postLogoutRedirectUri: string;
      if (settings.post_logout_redirect_uri) {
        // if explicitly configured, use the post_logout_redirect_uri
        postLogoutRedirectUri = settings.post_logout_redirect_uri;
      } else {
        // otherwise, build it dynamically, injecting the current urlSearchParams as query:
        `${settings.redirect_uri}?${urlSearchParams.toString()}`
      }
      await userManager.signoutRedirect({
        post_logout_redirect_uri: postLogoutRedirectUri
      })
    } catch (e) {
      showError(e)
    } finally {
      oidc.bearerToken = undefined
      await Environments.environmentsJsonChanged(false)
    }
  }
}

function dynamicallyShowOrHideSection(sectionEnabled: boolean, section: HTMLElement) {
  if (!sectionEnabled && section) {
    section.style.display = 'none'
  } else if (sectionEnabled) {
    section.style.display = 'inherit'
  }
}

export async function onEnvironmentChanged(initialPageLoad: boolean) {
  let environment = Environments.current();
  let usernamePassword = environment.authSettings?.main?.basic?.usernamePassword ?
    environment.authSettings?.main?.basic?.usernamePassword : ':';
  dom.userName.value = usernamePassword.split(':')[0];
  dom.password.value = usernamePassword.split(':')[1];
  usernamePassword = environment.authSettings?.devops?.basic?.usernamePassword ?
    environment.authSettings?.devops?.basic?.usernamePassword : ':';
  dom.devOpsUserName.value = usernamePassword.split(':')[0];
  dom.devOpsPassword.value = usernamePassword.split(':')[1];
  dom.bearer.value = environment.authSettings?.main?.bearer?.bearerToken ? environment.authSettings?.main?.bearer?.bearerToken : '';
  dom.bearerDevOps.value = environment.authSettings?.devops?.bearer?.bearerToken ? environment.authSettings?.devops?.bearer?.bearerToken : '';
  dom.dittoPreAuthenticatedUsername.value = environment.authSettings?.main?.pre?.dittoPreAuthenticatedUsername ?
    environment.authSettings?.main?.pre?.dittoPreAuthenticatedUsername : '';
  if (environment.authSettings?.oidc?.providers) {
    let availableOidcProviders = Object.keys(environment.authSettings.oidc.providers)
      .map(key => ({
        key: key,
        text: environment.authSettings.oidc.providers[key].displayName
      }));
    Utils.setOptionsWithText(dom.oidcProvider, availableOidcProviders);
    Utils.setOptionsWithText(dom.devOpsOidcProvider, availableOidcProviders);
    dom.oidcProvider.value = environment.authSettings.main.oidc.provider;
    dom.devOpsOidcProvider.value = environment.authSettings.devops.oidc.provider;
  }

  dynamicallyShowOrHideSection(environment.authSettings.main.oidc.enabled, dom.mainOidcSection);
  dynamicallyShowOrHideSection(environment.authSettings.main.basic.enabled, dom.mainBasicSection);
  dynamicallyShowOrHideSection(environment.authSettings.main.bearer.enabled, dom.mainBearerSection);
  dynamicallyShowOrHideSection(environment.authSettings.main.pre.enabled, dom.mainPreAuthenticatedSection);
  const anyMainAuthEnabled =
    environment.authSettings.main.oidc.enabled ||
    environment.authSettings.main.basic.enabled ||
    environment.authSettings.main.bearer.enabled ||
    environment.authSettings.main.pre.enabled;
  dynamicallyShowOrHideSection(anyMainAuthEnabled, dom.mainBasicSection.parentElement);

  dynamicallyShowOrHideSection(environment.authSettings.devops.oidc.enabled, dom.devOpsOidcSection);
  dynamicallyShowOrHideSection(environment.authSettings.devops.basic.enabled, dom.devOpsBasicSection);
  dynamicallyShowOrHideSection(environment.authSettings.devops.bearer.enabled, dom.devOpsBearerSection);
  const anyDevOpsAuthEnabled =
    environment.authSettings.devops.oidc.enabled ||
    environment.authSettings.devops.basic.enabled ||
    environment.authSettings.devops.bearer.enabled;
  dynamicallyShowOrHideSection(anyDevOpsAuthEnabled, dom.devOpsBasicSection.parentElement);

  if (initialPageLoad &&
    environment.authSettings?.main?.method === AuthMethod.oidc &&
    environment.authSettings?.main?.oidc?.autoSso === true
  ) {
    await performSingleSignOn(environment.authSettings?.main?.oidc);
    await Environments.environmentsJsonChanged(false);
  } else if (isSsoCallbackRequest()) {
    await handleSingleSignOnCallback(environment.authSettings?.main?.oidc);
  }

  API.setAuthHeader(_forDevops);
}
