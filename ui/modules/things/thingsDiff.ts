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

import { Tab } from 'bootstrap';
import * as ace from 'ace-builds/src-noconflict/ace';
import AceDiff from 'ace-diff';
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import thingsDiffHTML from './thingsDiff.html';
import * as Things from './things.js';

const SYSTEM_FIELDS = ['_revision', '_created', '_modified', '_context', '_metadata'];

export function ThingsDiff(targetTab) {
  let diffInstance: AceDiff | null = null;
  let tabLink: HTMLAnchorElement | null = null;
  let viewDirty = false;
  let currentThingId: string | null = null;
  let currentMinRevision = 1;
  let currentMaxRevision = 1;
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let selectedFeatureId: string | null = null;
  let lastLeftJson: any = null;
  let lastRightJson: any = null;
  let syncing = false;
  let fetchToken = 0;
  let activeProbe: { cancel: () => void } | null = null;
  let overviewSvg: SVGSVGElement | null = null;

  type SubDiffEntry = {
    subDiff: any;
    kind: 'attributes' | 'feature';
  };
  const subDiffs: SubDiffEntry[] = [];

  const dom = {
    diffLeftRevision: null as HTMLInputElement,
    diffLeftSlider: null as HTMLInputElement,
    diffLeftMin: null as HTMLSpanElement,
    diffLeftMax: null as HTMLSpanElement,
    diffLeftPrev: null as HTMLButtonElement,
    diffLeftNext: null as HTMLButtonElement,
    diffLeftCurrent: null as HTMLButtonElement,
    diffRightRevision: null as HTMLInputElement,
    diffRightSlider: null as HTMLInputElement,
    diffRightMin: null as HTMLSpanElement,
    diffRightMax: null as HTMLSpanElement,
    diffRightPrev: null as HTMLButtonElement,
    diffRightNext: null as HTMLButtonElement,
    diffRightCurrent: null as HTMLButtonElement,
    diffContainer: null as HTMLDivElement,
  };

  const ready = async () => {
    const tabId = Utils.addTab(
      document.getElementById(targetTab.itemsId),
      document.getElementById(targetTab.contentId),
      '<i class="bi bi-file-diff"></i> Diff',
      thingsDiffHTML,
      'Compare two revisions of this Thing side-by-side'
    );

    tabLink = document.querySelector(`a[data-bs-target="#${tabId}"]`);
    tabLink.addEventListener('shown.bs.tab', onTabActivated);

    Environments.addChangeListener(onEnvironmentChanged);

    Utils.getAllElementsById(dom);

    // Left revision controls
    dom.diffLeftSlider.oninput = () => {
      dom.diffLeftRevision.value = dom.diffLeftSlider.value;
      debouncedFetchAndDiff();
    };
    dom.diffLeftRevision.onchange = () => {
      dom.diffLeftSlider.value = dom.diffLeftRevision.value;
      debouncedFetchAndDiff();
    };
    dom.diffLeftPrev.onclick = () => stepRevision('left', -1);
    dom.diffLeftNext.onclick = () => stepRevision('left', 1);
    dom.diffLeftCurrent.onclick = () => setRevision('left', currentMaxRevision);

    // Right revision controls
    dom.diffRightSlider.oninput = () => {
      dom.diffRightRevision.value = dom.diffRightSlider.value;
      debouncedFetchAndDiff();
    };
    dom.diffRightRevision.onchange = () => {
      dom.diffRightSlider.value = dom.diffRightRevision.value;
      debouncedFetchAndDiff();
    };
    dom.diffRightPrev.onclick = () => stepRevision('right', -1);
    dom.diffRightNext.onclick = () => stepRevision('right', 1);
    dom.diffRightCurrent.onclick = () => setRevision('right', currentMaxRevision);

  };

  const onThingChanged = (thing, isNewThingId: boolean) => {
    if (!thing) {
      currentThingId = null;
      currentMinRevision = 1;
      currentMaxRevision = 1;
      lastLeftJson = null;
      lastRightJson = null;
      viewDirty = false;
      cancelActiveProbe();
      destroyDiff();
      for (const { subDiff } of subDiffs) {
        subDiff.destroy();
      }
      return;
    }

    // When in history mode and it's the same thing, don't reset
    if (Things.isHistoryModeActive() && !isNewThingId) {
      return;
    }

    currentThingId = thing.thingId;
    currentMinRevision = 1;
    currentMaxRevision = thing._revision || 1;
    updateSliderRanges();

    const leftRev = Math.max(currentMinRevision, currentMaxRevision - 1);
    setRevisionSilent('left', leftRev);
    setRevisionSilent('right', currentMaxRevision);

    cancelActiveProbe();
    probeOldestRevision(thing.thingId);

    if (isAnyDiffTabActive()) {
      fetchAndDiff();
    } else {
      viewDirty = true;
    }
  };

  function isAnyDiffTabActive(): boolean {
    if (tabLink && tabLink.classList.contains('active')) return true;
    return subDiffs.some((s) => {
      const link = s.subDiff.getTabLink();
      return link && link.classList.contains('active');
    });
  }

  const addSubDiff = (subDiff, kind: 'attributes' | 'feature') => {
    subDiffs.push({ subDiff, kind });
    subDiff.setOnActivated((link) => syncTabs(link));
  };

  const onFeatureChanged = (featureId: string) => {
    selectedFeatureId = featureId;
    updateSubDiffs();
  };

  return {
    ready,
    onThingChanged,
    addSubDiff,
    onFeatureChanged,
  };

  function destroyDiff() {
    if (diffInstance) {
      diffInstance.destroy();
      diffInstance = null;
    }
    overviewSvg = null;
    if (dom.diffContainer) {
      dom.diffContainer.textContent = '';
    }
  }

  function onTabActivated() {
    if (viewDirty) {
      fetchAndDiff();
      viewDirty = false;
    }
    // Resize editors to fit the container
    if (diffInstance) {
      const editors = diffInstance.getEditors();
      editors.left.resize();
      editors.right.resize();
    }
    syncTabs(tabLink);
  }

  function setRevision(side: 'left' | 'right', value: number) {
    setRevisionSilent(side, value);
    debouncedFetchAndDiff();
  }

  function setRevisionSilent(side: 'left' | 'right', value: number) {
    const input = side === 'left' ? dom.diffLeftRevision : dom.diffRightRevision;
    const slider = side === 'left' ? dom.diffLeftSlider : dom.diffRightSlider;
    input.value = String(value);
    slider.value = String(value);
  }

  function stepRevision(side: 'left' | 'right', delta: number) {
    const input = side === 'left' ? dom.diffLeftRevision : dom.diffRightRevision;
    let val = parseInt(input.value, 10) + delta;
    val = Math.max(currentMinRevision, Math.min(val, currentMaxRevision));
    setRevision(side, val);
  }

  function updateSliderRanges() {
    const min = String(currentMinRevision);
    const max = String(currentMaxRevision);
    dom.diffLeftSlider.min = min;
    dom.diffLeftSlider.max = max;
    dom.diffLeftRevision.min = min;
    dom.diffLeftRevision.max = max;
    dom.diffLeftMin.textContent = min;
    dom.diffLeftMax.textContent = max;
    dom.diffRightSlider.min = min;
    dom.diffRightSlider.max = max;
    dom.diffRightRevision.min = min;
    dom.diffRightRevision.max = max;
    dom.diffRightMin.textContent = min;
    dom.diffRightMax.textContent = max;
  }

  function cancelActiveProbe() {
    if (activeProbe) {
      activeProbe.cancel();
      activeProbe = null;
    }
  }

  function onEnvironmentChanged(modifiedField) {
    if (['pinnedThings', 'filterList', 'messageTemplates', 'recentPolicyIds'].includes(modifiedField)) {
      return;
    }
    cancelActiveProbe();
    destroyDiff();
    currentThingId = null;
    currentMinRevision = 1;
    currentMaxRevision = 1;
    lastLeftJson = null;
    lastRightJson = null;
    viewDirty = false;
  }

  function probeOldestRevision(thingId: string) {
    const probe = Things.probeOldestRevision(thingId);
    activeProbe = probe;

    probe.promise.then((result) => {
      activeProbe = null;
      if (!result || thingId !== currentThingId) return;

      if (result.revision) {
        currentMinRevision = result.revision;
        updateSliderRanges();
        const leftVal = parseInt(dom.diffLeftRevision.value, 10);
        if (leftVal < currentMinRevision) {
          setRevisionSilent('left', currentMinRevision);
        }
      }
    });
  }

  function updateSubDiffs() {
    if (!lastLeftJson || !lastRightJson) return;
    for (const { subDiff, kind } of subDiffs) {
      if (kind === 'attributes') {
        const left = lastLeftJson.attributes || {};
        const right = lastRightJson.attributes || {};
        subDiff.update(Utils.stringifyPretty(left), Utils.stringifyPretty(right));
      } else if (kind === 'feature' && selectedFeatureId) {
        const leftFeatures = lastLeftJson.features || {};
        const rightFeatures = lastRightJson.features || {};
        const left = leftFeatures[selectedFeatureId] || {};
        const right = rightFeatures[selectedFeatureId] || {};
        subDiff.update(Utils.stringifyPretty(left), Utils.stringifyPretty(right));
      }
    }
  }

  function syncTabs(activatedLink) {
    if (syncing) return;
    syncing = true;
    const allLinks = [tabLink, ...subDiffs.map(s => s.subDiff.getTabLink())];
    for (const link of allLinks) {
      if (link && link !== activatedLink && !link.classList.contains('active')) {
        new Tab(link).show();
      }
    }
    // Reset after async Bootstrap shown.bs.tab events have fired
    setTimeout(() => { syncing = false; }, 0);
  }

  function debouncedFetchAndDiff() {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    debounceTimer = setTimeout(() => {
      fetchAndDiff();
      debounceTimer = null;
    }, 300);
  }

  function fetchAndDiff() {
    if (!currentThingId) return;

    const leftRev = parseInt(dom.diffLeftRevision.value, 10);
    const rightRev = parseInt(dom.diffRightRevision.value, 10);

    if (isNaN(leftRev) || isNaN(rightRev) || leftRev < 1 || rightRev < 1) return;

    const token = ++fetchToken;

    Promise.allSettled([
      fetchRevision(currentThingId, leftRev),
      fetchRevision(currentThingId, rightRev),
    ]).then((results) => {
      if (token !== fetchToken) return; // stale response

      if (results[0].status === 'rejected') {
        console.warn(`Diff: failed to fetch left revision ${leftRev}`, results[0].reason);
      }
      if (results[1].status === 'rejected') {
        console.warn(`Diff: failed to fetch right revision ${rightRev}`, results[1].reason);
      }
      if (results[0].status === 'rejected' && results[1].status === 'rejected') {
        Utils.showError(`Failed to fetch both revisions (${leftRev}, ${rightRev}) for diff`);
      }

      const leftJson = results[0].status === 'fulfilled' ? results[0].value : {};
      const rightJson = results[1].status === 'fulfilled' ? results[1].value : {};
      lastLeftJson = leftJson;
      lastRightJson = rightJson;
      const leftStr = Utils.stringifyPretty(leftJson);
      const rightStr = Utils.stringifyPretty(rightJson);
      createOrUpdateDiff(leftStr, rightStr);
      updateSubDiffs();
    });
  }

  function fetchRevision(thingId: string, revision: number): Promise<object> {
    return API.callDittoREST('GET',
      `/things/${thingId}?${Things.HISTORY_FIELDS}`,
      null,
      {'at-historical-revision': String(revision)}
    ).then((thing) => {
      if (thing) {
        const copy = structuredClone(thing);
        SYSTEM_FIELDS.forEach((f) => delete copy[f]);
        return copy;
      }
      return {};
    });
  }

  function createOrUpdateDiff(leftContent: string, rightContent: string) {
    if (diffInstance) {
      const editors = diffInstance.getEditors();
      editors.left.setValue(leftContent, -1);
      editors.right.setValue(rightContent, -1);
      diffInstance.diff();
      renderChangeOverview(diffInstance.diffs);
    } else {
      diffInstance = new AceDiff({
        ace: ace as unknown as { edit: typeof ace.edit },
        element: dom.diffContainer,
        mode: 'ace/mode/json',
        theme: null,
        diffGranularity: 'specific',
        lockScrolling: true,
        showDiffs: true,
        showConnectors: true,
        charDiffs: true,
        maxDiffs: 5000,
        left: {
          content: leftContent,
          editable: false,
          copyLinkEnabled: false,
        },
        right: {
          content: rightContent,
          editable: false,
          copyLinkEnabled: false,
        },
        onDiffReady: renderChangeOverview,
      });
    }
  }

  /**
   * Renders a change overview minimap in the ace-diff gutter.
   * Each diff region is shown as a colored rectangle positioned proportionally
   * to its line number, giving a scrollbar-like overview of all changes.
   * Clicking a marker scrolls both editors to that change.
   */
  function renderChangeOverview(diffs) {
    if (!diffInstance) return;

    const gutterEl = dom.diffContainer.querySelector('.acediff__gutter') as HTMLElement;
    if (!gutterEl) return;

    const editors = diffInstance.getEditors();
    const leftLineCount = editors.left.session.getLength();
    const rightLineCount = editors.right.session.getLength();
    const totalLines = Math.max(leftLineCount, rightLineCount, 1);

    const gutterHeight = gutterEl.clientHeight;
    if (gutterHeight === 0) return;

    const svgNS = 'http://www.w3.org/2000/svg';

    // Reuse existing SVG or create a new one
    if (!overviewSvg) {
      overviewSvg = document.createElementNS(svgNS, 'svg');
      overviewSvg.classList.add('diff-overview');
      overviewSvg.setAttribute('width', '100%');
      overviewSvg.style.position = 'absolute';
      overviewSvg.style.top = '0';
      overviewSvg.style.left = '0';
      overviewSvg.style.right = '0';
      overviewSvg.style.pointerEvents = 'auto';
      overviewSvg.style.zIndex = '10';
      gutterEl.style.position = 'relative';
      gutterEl.appendChild(overviewSvg);
    }

    // Clear previous markers and update height
    while (overviewSvg.firstChild) {
      overviewSvg.removeChild(overviewSvg.firstChild);
    }
    overviewSvg.setAttribute('height', String(gutterHeight));

    for (const diff of diffs) {
      const startLine = Math.min(diff.leftStartLine, diff.rightStartLine);
      const endLine = Math.max(diff.leftEndLine, diff.rightEndLine);

      const y = (startLine / totalLines) * gutterHeight;
      const h = Math.max(((endLine - startLine + 1) / totalLines) * gutterHeight, 3);

      const rect = document.createElementNS(svgNS, 'rect');
      rect.setAttribute('x', '15%');
      rect.setAttribute('width', '70%');
      rect.setAttribute('y', String(Math.round(y)));
      rect.setAttribute('height', String(Math.round(h)));
      rect.setAttribute('rx', '2');
      rect.style.fill = 'rgba(58, 140, 154, 0.6)';
      rect.style.cursor = 'pointer';

      rect.addEventListener('click', (e) => {
        e.stopPropagation();
        // Scroll both editors without animation. Animation caused a
        // bounce: each animated frame triggered AceDiff's proportional
        // scroll sync on the opposite editor, creating oscillation.
        editors.left.scrollToLine(diff.leftStartLine, true, false, () => {});
        editors.right.scrollToLine(diff.rightStartLine, true, false, () => {});
      });
      rect.addEventListener('mouseenter', () => {
        rect.style.fill = 'rgba(58, 140, 154, 0.9)';
      });
      rect.addEventListener('mouseleave', () => {
        rect.style.fill = 'rgba(58, 140, 154, 0.6)';
      });

      overviewSvg.appendChild(rect);
    }
  }
}
