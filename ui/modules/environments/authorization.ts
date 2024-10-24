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
import { fillSearchFilterEdit } from '../things/searchFilter';
import { ThingsSearchGlobalVars } from '../things/thingsSearch';
import * as Utils from '../utils.js';
import { showError, showInfoToast } from '../utils.js';
import authorizationHTML from './authorization.html';
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as Environments from './environments.js';
import {
  AuthMethod,
  OidcAuthSettings,
  OidcProviderConfiguration,
  URL_OIDC_PROVIDER,
  URL_PRIMARY_ENVIRONMENT_NAME
} from './environments.js';

let dom = {
  mainBearerSection: null,
  oidcBearer: null,
  bearer: null,
  mainBasicSection: null,
  userName: null,
  password: null,
  devOpsBearerSection: null,
  oidcBearerDevOps: null,
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

type OidcState = {
  mainAuth: boolean,
  devopsAuth: boolean
}

function OidcState(state: OidcState): void {
  Object.assign(this, state);
}

let _forDevops = false;

document.getElementById('authorizationHTML').innerHTML = authorizationHTML;

export function setForDevops(forDevops: boolean) {
  _forDevops = forDevops;
  API.setAuthHeader(_forDevops);
}

export function mainUsernamePassword(): string {
  return dom.userName.value + ':' + dom.password.value;
}

export function mainBearerToken(): string {
  return dom.bearer.value;
}

export function mainOidcBearerToken(): string {
  return dom.oidcBearer.value;
}

export function devopsUsernamePassword(): string {
  return dom.devOpsUserName.value + ':' + dom.devOpsPassword.value;
}

export function devopsBearerToken(): string {
  return dom.bearerDevOps.value;
}

export function devopsOidcBearerToken(): string {
  return dom.oidcBearerDevOps.value;
}

export function fillMainUsernamePassword(usernamePassword: string) {
  if (usernamePassword && usernamePassword.length > 0) {
    dom.userName.value = usernamePassword.split(':')[0];
    dom.password.value = usernamePassword.split(':')[1];
  }
}

export function fillDevopsUsernamePassword(usernamePassword: string) {
  if (usernamePassword && usernamePassword.length > 0) {
    dom.devOpsUserName.value = usernamePassword.split(':')[0];
    dom.devOpsPassword.value = usernamePassword.split(':')[1];
  }
}

function fillMainOidcBearerToken(oidcToken: string) {
  dom.oidcBearer.value = oidcToken;
}

function fillDevopsOidcBearerToken(oidcToken: string) {
  dom.oidcBearerDevOps.value = oidcToken;
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
    environment.authSettings.main.pre.dittoPreAuthenticatedUsername = dom.dittoPreAuthenticatedUsername.value;
    environment.authSettings.main.oidc.defaultProvider = dom.oidcProvider.value;
    environment.authSettings.devops.oidc.defaultProvider = dom.devOpsOidcProvider.value;
    await Environments.environmentsJsonChanged(false);
  };

  document.getElementById('main-oidc-login').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.main.oidc.provider = dom.oidcProvider.value;
    Environments.saveEnvironmentsToLocalStorage();
    let alreadyLoggedIn = await performSingleSignOn(true)
    if (alreadyLoggedIn) {
      environment.authSettings.main.method = AuthMethod.oidc
      showInfoToast('You are already logged in')
    }
    await Environments.environmentsJsonChanged(false);
  };
  document.getElementById('main-oidc-logout').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.main.oidc.provider = dom.oidcProvider.value;
    await performSingleSignOut(environment.authSettings.main.oidc)
  };

  document.getElementById('devops-oidc-login').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.devops.oidc.provider = dom.devOpsOidcProvider.value;
    Environments.saveEnvironmentsToLocalStorage();
    let alreadyLoggedIn = await performSingleSignOn(false)
    if (alreadyLoggedIn) {
      environment.authSettings.devops.method = AuthMethod.oidc
      showInfoToast('You are already logged in')
    }
    await Environments.environmentsJsonChanged(false);
  };
  document.getElementById('devops-oidc-logout').onclick = async (e) => {
    e.preventDefault();
    let environment = Environments.current();
    environment.authSettings.devops.oidc.provider = dom.devOpsOidcProvider.value;
    await performSingleSignOut(environment.authSettings.devops.oidc)
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

async function handleSingleSignOnCallback(urlSearchParams: URLSearchParams) {
  let environment = Environments.current();
  const oidcProviderId = urlSearchParams.get(URL_OIDC_PROVIDER) || environment.authSettings?.main?.oidc.provider;
  let oidcProvider: OidcProviderConfiguration = environment.authSettings.oidc.providers[oidcProviderId];
  const settings: UserManagerSettings = oidcProvider;
  if (settings !== undefined && settings !== null) {
    const userManager = new UserManager(settings);
    try {
      let user = await userManager.signinCallback(window.location.href)
      if (user) {
        let oidcState = user.state as OidcState
        if (oidcState.mainAuth) {
          environment.authSettings.main.method = AuthMethod.oidc;
          fillMainOidcBearerToken(user[oidcProvider.extractBearerTokenFrom]);
        }
        if (oidcState.devopsAuth) {
          environment.authSettings.devops.method = AuthMethod.oidc;
          fillDevopsOidcBearerToken(user[oidcProvider.extractBearerTokenFrom]);
        }
        window.history.replaceState(null, null, `${settings.redirect_uri}?${atob(user.url_state)}`)
        await Environments.environmentsJsonChanged(false)
      }
    } catch (e) {
      console.error(`Could not login due to: ${e}`)
    }
  }
}

async function performSingleSignOn(forMainAuth: boolean): Promise<boolean> {
  let environment = Environments.current();
  let oidc: OidcAuthSettings;
  if (forMainAuth) {
    oidc = environment.authSettings?.main?.oidc;
  } else {
    oidc = environment.authSettings?.devops?.oidc;
  }
  let sameProviderForMainAndDevops =
    environment.authSettings?.main?.oidc.provider == environment.authSettings?.devops?.oidc.provider;
  let oidcProvider = environment.authSettings.oidc.providers[oidc.provider];
  const settings: UserManagerSettings = oidcProvider;
  if (settings !== undefined && settings !== null) {
    const urlSearchParams: URLSearchParams = new URLSearchParams(window.location.search);
    const userManager = new UserManager(settings);
    if (isSsoCallbackRequest(urlSearchParams)) {
      await handleSingleSignOnCallback(urlSearchParams)
      return false
    } else {
      let user = await userManager.getUser();
      if (user?.[oidcProvider.extractBearerTokenFrom] !== undefined || user?.expired === true) {
        // a user is still logged in via a valid token stored in the browser's session storage
        if (sameProviderForMainAndDevops) {
          fillMainOidcBearerToken(user[oidcProvider.extractBearerTokenFrom]);
          fillDevopsOidcBearerToken(user[oidcProvider.extractBearerTokenFrom]);
        } else {
          fillMainOidcBearerToken(user[oidcProvider.extractBearerTokenFrom]);
        }
        return true
      } else {
        urlSearchParams.set(URL_PRIMARY_ENVIRONMENT_NAME, Environments.currentEnvironmentSelector())
        urlSearchParams.set(URL_OIDC_PROVIDER, oidc.provider)
        try {
          await userManager.signinRedirect({
            state: new OidcState({
              mainAuth: forMainAuth || sameProviderForMainAndDevops,
              devopsAuth: !forMainAuth || sameProviderForMainAndDevops
            }),
            url_state: btoa(urlSearchParams.toString()) // base64 encode to also support e.g. "&"
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
        postLogoutRedirectUri = `${settings.redirect_uri}?${urlSearchParams.toString()}`
      }
      await userManager.signoutRedirect({
        post_logout_redirect_uri: postLogoutRedirectUri
      })
    } catch (e) {
      showError(e)
    } finally {
      fillMainOidcBearerToken(undefined);
      Environments.saveEnvironmentsToLocalStorage();
    }
  }
}

function dynamicallyShowOrHideSection(sectionEnabled: boolean, section: HTMLElement) {
  if (!sectionEnabled && section) {
    section.style.display = 'none'
  } else if (sectionEnabled) {
    section.style.display = null
  }
}

export async function onEnvironmentChanged(initialPageLoad: boolean) {
  let environment = Environments.current();
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

  let urlSearchParams = new URLSearchParams(window.location.search);
  if (initialPageLoad &&
    environment.authSettings?.main?.method === AuthMethod.oidc &&
    environment.authSettings?.main?.oidc?.autoSso === true
  ) {
    await performSingleSignOn(true);
    Environments.saveEnvironmentsToLocalStorage();
  } else if (initialPageLoad &&
    environment.authSettings?.devops?.method === AuthMethod.oidc &&
    environment.authSettings?.devops?.oidc?.autoSso === true
  ) {
    await performSingleSignOn(false);
    Environments.saveEnvironmentsToLocalStorage();
  } else if (isSsoCallbackRequest(urlSearchParams)) {
    await handleSingleSignOnCallback(urlSearchParams);
  }

  API.setAuthHeader(_forDevops);

  let filter = urlSearchParams.get('filter');
  if (filter) {
    ThingsSearchGlobalVars.lastSearch = filter;
    fillSearchFilterEdit(filter);
  }
}
