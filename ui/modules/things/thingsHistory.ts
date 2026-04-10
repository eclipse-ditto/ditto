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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import thingsHistoryHTML from './thingsHistory.html';
import * as Things from './things.js';
import * as ThingUpdates from './thingUpdates.js';

type DomElements = {
  historyModeSwitch: HTMLInputElement,
  historyBanner: HTMLDivElement,
  historyBannerText: HTMLSpanElement,
  historyControls: HTMLDivElement,
  historyRadioRevision: HTMLInputElement,
  historyRadioTimestamp: HTMLInputElement,
  historyRevisionControls: HTMLDivElement,
  historyTimestampControls: HTMLDivElement,
  historyRevisionSlider: HTMLInputElement,
  historyRevisionInput: HTMLInputElement,
  historyRevisionMin: HTMLSpanElement,
  historyRevisionMax: HTMLSpanElement,
  historyRevisionPrev: HTMLButtonElement,
  historyRevisionNext: HTMLButtonElement,
  historyTimestampInput: HTMLInputElement,
  historyShowUpdates: HTMLButtonElement,
};

let dom: DomElements = {
  historyModeSwitch: null,
  historyBanner: null,
  historyBannerText: null,
  historyControls: null,
  historyRadioRevision: null,
  historyRadioTimestamp: null,
  historyRevisionControls: null,
  historyTimestampControls: null,
  historyRevisionSlider: null,
  historyRevisionInput: null,
  historyRevisionMin: null,
  historyRevisionMax: null,
  historyRevisionPrev: null,
  historyRevisionNext: null,
  historyTimestampInput: null,
  historyShowUpdates: null,
};

let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let currentThingId: string | null = null;
let currentMinRevision = 1;
let currentMaxRevision = 1;
let oldestTimestamp: string | null = null;

document.getElementById('thingsHistoryHTML').innerHTML = thingsHistoryHTML;

export function ready() {
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  dom.historyModeSwitch.onchange = onHistoryModeToggle;
  dom.historyRadioRevision.onchange = onModeRadioChange;
  dom.historyRadioTimestamp.onchange = onModeRadioChange;
  dom.historyRevisionSlider.oninput = onRevisionSliderInput;
  dom.historyRevisionInput.onchange = onRevisionInputChange;
  dom.historyRevisionPrev.onclick = onRevisionPrev;
  dom.historyRevisionNext.onclick = onRevisionNext;
  dom.historyTimestampInput.onchange = onTimestampChange;
  dom.historyShowUpdates.onclick = onShowUpdatesFromHere;
}

function onThingChanged(thing, isNewThingId: boolean) {
  if (!thing) {
    dom.historyModeSwitch.disabled = true;
    dom.historyModeSwitch.checked = false;
    hideHistoryControls();
    currentThingId = null;
    return;
  }

  if (isNewThingId && Things.historyModeActive) {
    Things.setHistoryMode(false);
    dom.historyModeSwitch.checked = false;
    hideHistoryControls();
  }

  // When history mode is active and it's the same thing, the update is from
  // a historical revision load -- don't reset the slider range.
  if (Things.historyModeActive && !isNewThingId) {
    return;
  }

  currentThingId = thing.thingId;
  dom.historyModeSwitch.disabled = false;

  currentMaxRevision = thing._revision || 1;
  currentMinRevision = 1;
  oldestTimestamp = null;
  updateRevisionRange();

  dom.historyRevisionSlider.value = String(currentMaxRevision);
  dom.historyRevisionInput.value = String(currentMaxRevision);

  if (thing._created) {
    dom.historyTimestampInput.min = toDatetimeLocalValue(thing._created);
  }
  if (thing._modified) {
    dom.historyTimestampInput.max = toDatetimeLocalValue(thing._modified);
    dom.historyTimestampInput.value = toDatetimeLocalValue(thing._modified);
  }
}

function onHistoryModeToggle() {
  const active = dom.historyModeSwitch.checked;
  if (active) {
    showHistoryControls();
    Things.setHistoryMode(true);
    probeOldestRevision();
    fetchHistoricalState();
  } else {
    hideHistoryControls();
    Things.setHistoryMode(false);
  }
}

/**
 * Probes for the oldest available revision by opening a historical SSE stream
 * starting at revision 1. The first event received contains the oldest available
 * revision (cleaned-up revisions are skipped by the backend). The stream is
 * closed immediately after receiving the first event.
 */
function probeOldestRevision() {
  if (!currentThingId) return;

  const urlParams = 'from-historical-revision=1&fields=_revision,_modified';
  let probeSource;
  try {
    probeSource = API.getHistoricalEventSource(currentThingId, urlParams);
  } catch (err) {
    return;
  }

  probeSource.onmessage = (event) => {
    probeSource.close();
    if (event.data && event.data !== '') {
      try {
        const data = JSON.parse(event.data);
        if (data._revision) {
          currentMinRevision = data._revision;
          updateRevisionRange();
          // Clamp slider if currently below the available minimum
          const currentVal = parseInt(dom.historyRevisionSlider.value, 10);
          if (currentVal < currentMinRevision) {
            dom.historyRevisionSlider.value = String(currentMinRevision);
            dom.historyRevisionInput.value = String(currentMinRevision);
          }
        }
        if (data._modified) {
          oldestTimestamp = data._modified;
          const oldestLocal = toDatetimeLocalValue(oldestTimestamp);
          dom.historyTimestampInput.min = oldestLocal;
          // Clamp timestamp value if currently older than available
          if (dom.historyTimestampInput.value < oldestLocal) {
            dom.historyTimestampInput.value = oldestLocal;
          }
        }
      } catch (e) {
        // ignore parse errors from probe
      }
    }
  };

  probeSource.onerror = () => {
    probeSource.close();
  };
}

function updateRevisionRange() {
  dom.historyRevisionSlider.min = String(currentMinRevision);
  dom.historyRevisionSlider.max = String(currentMaxRevision);
  dom.historyRevisionInput.min = String(currentMinRevision);
  dom.historyRevisionInput.max = String(currentMaxRevision);
  dom.historyRevisionMin.textContent = String(currentMinRevision);
  dom.historyRevisionMax.textContent = String(currentMaxRevision);
}

function showHistoryControls() {
  dom.historyControls.hidden = false;
  dom.historyBanner.classList.remove('d-none');
  dom.historyBanner.classList.add('d-flex');
  document.getElementById('collapseThings')?.classList.add('history-active');
  addHistoryBadges();
}

function hideHistoryControls() {
  dom.historyControls.hidden = true;
  dom.historyBanner.classList.remove('d-flex');
  dom.historyBanner.classList.add('d-none');
  document.getElementById('collapseThings')?.classList.remove('history-active');
  removeHistoryBadges();
}

function addHistoryBadges() {
  addBadgeToSection('badgeAttributeCount');
  addBadgeToSection('badgeFeatureCount');
}

function removeHistoryBadges() {
  document.querySelectorAll('.history-badge').forEach((el) => el.remove());
}

function addBadgeToSection(siblingId: string) {
  const sibling = document.getElementById(siblingId);
  if (sibling && !sibling.parentElement.querySelector('.history-badge')) {
    const badge = document.createElement('span');
    badge.className = 'badge rounded-pill bg-warning text-dark history-badge ms-1';
    badge.style.fontSize = '0.6em';
    badge.style.verticalAlign = 'top';
    badge.textContent = 'Time Travel';
    sibling.after(badge);
  }
}

function onModeRadioChange() {
  const byRevision = dom.historyRadioRevision.checked;
  dom.historyRevisionControls.hidden = !byRevision;
  dom.historyTimestampControls.hidden = byRevision;
  if (Things.historyModeActive) {
    fetchHistoricalState();
  }
}

function onRevisionSliderInput() {
  dom.historyRevisionInput.value = dom.historyRevisionSlider.value;
  debouncedFetch();
}

function onRevisionInputChange() {
  let val = parseInt(dom.historyRevisionInput.value, 10);
  val = Math.max(currentMinRevision, Math.min(val, currentMaxRevision));
  dom.historyRevisionInput.value = String(val);
  dom.historyRevisionSlider.value = String(val);
  fetchHistoricalState();
}

function onRevisionPrev() {
  let val = parseInt(dom.historyRevisionSlider.value, 10);
  if (val > currentMinRevision) {
    val--;
    dom.historyRevisionSlider.value = String(val);
    dom.historyRevisionInput.value = String(val);
    fetchHistoricalState();
  }
}

function onRevisionNext() {
  let val = parseInt(dom.historyRevisionSlider.value, 10);
  if (val < currentMaxRevision) {
    val++;
    dom.historyRevisionSlider.value = String(val);
    dom.historyRevisionInput.value = String(val);
    fetchHistoricalState();
  }
}

function onTimestampChange() {
  if (Things.historyModeActive) {
    fetchHistoricalState();
  }
}

function onShowUpdatesFromHere() {
  if (!currentThingId) return;

  if (dom.historyRadioRevision.checked) {
    const fromRevision = parseInt(dom.historyRevisionSlider.value, 10);
    ThingUpdates.showHistoricalFromRevision(fromRevision, currentMaxRevision);
  } else {
    const fromTimestamp = dom.historyTimestampInput.value;
    const toTimestamp = dom.historyTimestampInput.max || dom.historyTimestampInput.value;
    ThingUpdates.showHistoricalFromTimestamp(fromTimestamp, toTimestamp);
  }
}

function debouncedFetch() {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
  }
  debounceTimer = setTimeout(() => {
    fetchHistoricalState();
    debounceTimer = null;
  }, 200);
}

function fetchHistoricalState() {
  if (!currentThingId) return;

  if (dom.historyRadioRevision.checked) {
    const revision = parseInt(dom.historyRevisionSlider.value, 10);
    dom.historyBannerText.textContent = `Time travel to revision ${revision}`;
    Things.refreshThingAtRevision(currentThingId, revision);
  } else {
    const timestamp = dom.historyTimestampInput.value;
    if (timestamp) {
      const isoTimestamp = new Date(timestamp).toISOString();
      dom.historyBannerText.textContent = `Time travel to ${timestamp.replace('T', ' ')}`;
      Things.refreshThingAtTimestamp(currentThingId, isoTimestamp);
    }
  }
}

function toDatetimeLocalValue(isoString: string): string {
  if (!isoString) return '';
  return isoString.substring(0, 19);
}
