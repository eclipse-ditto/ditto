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
/* eslint-disable new-cap */
import 'bootstrap/dist/css/bootstrap.min.css';
import './main.scss';
import {Dropdown} from 'bootstrap';

import * as Authorization from './modules/environments/authorization.js';
import * as Environments from './modules/environments/environments.js';
import * as Attributes from './modules/things/attributes.js';
import * as Features from './modules/things/features.js';
import * as FeatureMessages from './modules/things/featureMessages.js';
import * as Fields from './modules/things/fields.js';
import * as SearchFilter from './modules/things/searchFilter.js';
import * as Things from './modules/things/things.js';
import * as ThingsSearch from './modules/things/thingsSearch.js';
import * as ThingsCRUD from './modules/things/thingsCRUD.js';
import * as ThingsSSE from './modules/things/thingsSSE.js';
import * as MessagesIncoming from './modules/things/messagesIncoming.js';
import * as Connections from './modules/connections/connections.js';
import * as ConnectionsCRUD from './modules/connections/connectionsCRUD.js';
import * as ConnectionsMonitor from './modules/connections/connectionsMonitor.js';
import * as Operations from './modules/operations/operations.js';
import * as Policies from './modules/policies/policies.js';
import * as Utils from './modules/utils.js';
import {WoTDescription} from './modules/things/wotDescription.js';
import './modules/utils/crudToolbar.js';
let resized = false;
let mainNavbar;

document.addEventListener('DOMContentLoaded', async function() {
  Utils.ready();
  await Things.ready();
  ThingsSearch.ready();
  ThingsCRUD.ready();
  ThingsSSE.ready();
  MessagesIncoming.ready();
  Attributes.ready();
  await Fields.ready();
  await SearchFilter.ready();
  Features.ready();
  await FeatureMessages.ready();
  Policies.ready();
  Connections.ready();
  ConnectionsCRUD.ready();
  ConnectionsMonitor.ready();
  Operations.ready();
  Authorization.ready();
  await Environments.ready();

  const thingDescription = WoTDescription({
    itemsId: 'tabItemsThing',
    contentId: 'tabContentThing',
  }, false);
  Things.addChangeListener(thingDescription.onReferenceChanged);
  thingDescription.ready();

  const featureDescription = WoTDescription({
    itemsId: 'tabItemsFeatures',
    contentId: 'tabContentFeatures',
  }, true);
  Features.addChangeListener(featureDescription.onReferenceChanged);
  featureDescription.ready();

  // make dropdowns not cutting off
  new Dropdown(document.querySelector('.dropdown-toggle'), {
    popperConfig: {
      strategy: 'fixed',
    },
  });

  // make top navbar activating and setting the right auth header
  mainNavbar = document.getElementById('mainNavbar');
  mainNavbar.querySelectorAll('.nav-link').forEach((e) => {
    e.addEventListener('click', (event) => {
      mainNavbar.querySelectorAll('.nav-link,.active').forEach((n) => n.classList.remove('active'));
      event.currentTarget.classList.add('active');
    });
  });

  // make tables toggle background on selection
  document.querySelectorAll('.table').forEach((e) => {
    e.addEventListener('click', (event) => {
      const target = event.target as HTMLElement;
      if (target && target.tagName === 'TD') {
        Array.from(target.parentNode.parentNode.children).forEach((n) => {
          if (n !== target.parentNode) {
            n.classList.remove('table-active');
          }
        });
        target.parentElement.classList.toggle('table-active');
      }
    });
  });

  // make ace editor resize when user changes height
  const resizeObserver = new ResizeObserver(() => {
    resized = true;
  });
  document.querySelectorAll('.resizable_pane').forEach((e) => {
    resizeObserver.observe(e);
    e.addEventListener('mouseup', () => {
      if (resized) {
        window.dispatchEvent(new Event('resize'));
        resized = false;
      }
    });
  });

  // Make all input field remove invalid marker on change
  const {get, set} = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
  document.querySelectorAll('input').forEach((input) => {
    input.addEventListener('change', (event) => {
      (event.target as HTMLElement).classList.remove('is-invalid');
    });
    Object.defineProperty(input, 'value', {
      get() {
        return get.call(this);
      },
      set(newVal) {
        input.classList.remove('is-invalid');
        return set.call(this, newVal);
      },
    });
  });
});
