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

import * as Environments from './environments/environments.js';
import * as Utils from './utils.js';

export let authHeader;

/**
 * Activates authorization header for api calls
 * @param {boolean} forDevOps if true, the credentials for the dev ops api will be used.
 */
export function setAuthHeader(forDevOps) {
  if (!Environments.current().bearer && !Environments.current().usernamePassword) {
    return;
  }
  if (Environments.current().useBasicAuth) {
    if (forDevOps && Environments.current().usernamePasswordDevOps) {
      authHeader = 'Basic ' + window.btoa(Environments.current().usernamePasswordDevOps);
    } else {
      authHeader = 'Basic ' + window.btoa(Environments.current().usernamePassword);
    }
  } else {
    authHeader ='Bearer ' + Environments.current().bearer;
  }
}

/**
 * Calls the Ditto api
 * @param {String} method 'POST', 'GET', 'DELETE', etc.
 * @param {String} path of the Ditto call (e.g. '/things')
 * @param {Object} body payload for the api call
 * @return {Object} result as json object
 */
export async function callDittoREST(method, path, body) {
  try {
    const response = await fetch(Environments.current().api_uri + '/api/2' + path, {
      method: method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': authHeader,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      response.json()
          .then((dittoErr) => {
            Utils.showError(dittoErr.message, dittoErr.error, dittoErr.status);
          })
          .catch((err) => {
            Utils.showError('No error details from Ditto', response.statusText, response.status);
          });
      throw new Error('An error occured: ' + response.status);
    }
    if (response.status !== 204) {
      return response.json();
    } else {
      return null;
    }
  } catch (err) {
    Utils.showError(err);
    throw err;
  }
}
