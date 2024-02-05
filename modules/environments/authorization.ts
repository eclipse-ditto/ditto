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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import authorizationHTML from './authorization.html';
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as Environments from './environments.js';

let dom = {
  bearer: null,
  userName: null,
  password: null,
  bearerDevOps: null,
  devOpsUserName: null,
  devOpsPassword: null,
  dittoPreAuthenticatedUsername: null,
  collapseConnections: null,
};

let _forDevops = false;

document.getElementById('authorizationHTML').innerHTML = authorizationHTML;

export function setForDevops(forDevops) {
  _forDevops = forDevops;
  API.setAuthHeader(_forDevops);
}

export function ready() {
  Utils.getAllElementsById(dom);

  document.getElementById('authorize').onclick = (e) => {
    let mainAuth = Environments.current().mainAuth;
    let devopsAuth = Environments.current().devopsAuth;

    if (!mainAuth) {
      if (dom.dittoPreAuthenticatedUsername.value && dom.dittoPreAuthenticatedUsername.value.length > 0) {
        mainAuth = 'pre';
      } else if (dom.bearer.value && dom.bearer.value.length > 0) {
        mainAuth = 'bearer';
      } else if (dom.userName.value && dom.userName.value.length > 0) {
        mainAuth = 'basic';
      }
    }
    if (!devopsAuth) {
      if (dom.bearerDevOps.value && dom.bearerDevOps.value.length > 0) {
        devopsAuth = 'bearer';
      } else if (dom.devOpsUserName.value && dom.devOpsUserName.value.length > 0) {
        devopsAuth = 'basic';
      }
    }

    Array.from(document.querySelectorAll('input[name="main-auth"]')).forEach((inputAuth: HTMLInputElement) => {
      inputAuth.checked = inputAuth.value === mainAuth;
    });

    Array.from(document.querySelectorAll('input[name="devops-auth"]')).forEach((inputAuth: HTMLInputElement) => {
      inputAuth.checked = inputAuth.value === devopsAuth;
    });
  };

  document.getElementById('authorizeSubmit').onclick = (e) => {
    e.preventDefault();
    const mainAuthSelector = document.querySelector('input[name="main-auth"]:checked') as HTMLInputElement;
    const mainAuth = mainAuthSelector ? mainAuthSelector.value : undefined;
    const devopsAuthSelector = document.querySelector('input[name="devops-auth"]:checked') as HTMLInputElement;
    const devopsAuth = devopsAuthSelector ? devopsAuthSelector.value : undefined;
    Environments.current().mainAuth = mainAuth;
    Environments.current().devopsAuth = devopsAuth;
    Environments.current().usernamePassword = dom.userName.value + ':' + dom.password.value;
    Environments.current().usernamePasswordDevOps = dom.devOpsUserName.value + ':' + dom.devOpsPassword.value;
    Environments.current().bearer = dom.bearer.value;
    Environments.current().bearerDevOps = dom.bearerDevOps.value;
    Environments.current().dittoPreAuthenticatedUsername = dom.dittoPreAuthenticatedUsername.value;
    Environments.environmentsJsonChanged();
  };
}

export function onEnvironmentChanged() {
  let usernamePassword = Environments.current().usernamePassword ? Environments.current().usernamePassword : ':';
  dom.userName.value = usernamePassword.split(':')[0];
  dom.password.value = usernamePassword.split(':')[1];
  usernamePassword = Environments.current().usernamePasswordDevOps ?
      Environments.current().usernamePasswordDevOps :
      ':';
  dom.devOpsUserName.value = usernamePassword.split(':')[0];
  dom.devOpsPassword.value = usernamePassword.split(':')[1];
  dom.bearer.value = Environments.current().bearer ? Environments.current().bearer : '';
  dom.bearerDevOps.value = Environments.current().bearerDevOps ? Environments.current().bearerDevOps : '';
  dom.dittoPreAuthenticatedUsername.value = Environments.current().dittoPreAuthenticatedUsername ?
                                            Environments.current().dittoPreAuthenticatedUsername :
                                            '';
  API.setAuthHeader(_forDevops);
}
