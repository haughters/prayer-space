import './shared/apiClient.js';
import './styles/home.css';
import { Html5Qrcode } from 'html5-qrcode';

console.log("Prayer Link Client Version:", __APP_VERSION__);

const CONFIG = {
    acronyms: ["Let", "Us"],
    managementWord: "PRAY",
    chatPlaceholder: "Enter your prayer here...",
    navNewLabel: "New Prayer",
    navMineLabel: "My Prayers",
    timings: { start: 400, afterLet: 900, afterUs: 1000, afterPray: 1100, afterPill: 250 },
};

const sleep = (ms) => new Promise((r => setTimeout(r, ms)));

let prayersList = [];
let currentDeviceId = null;
let selectedGroupId = null;
let selectedGroupName = 'Any Prayer Group';
let visibleCount = 20;
let html5QrCode = null;

const groupNamesCache = {};

async function fetchAndCacheGroupName(groupId, spanId) {
    if (!groupId) return;
    if (groupNamesCache[groupId]) {
        const el = document.getElementById(spanId);
        if (el) el.textContent = groupNamesCache[groupId];
        return;
    }
    try {
        const res = await fetch(`/api/groups/${groupId}`);
        if (res.ok) {
            const data = await res.json();
            groupNamesCache[groupId] = data.name;
            const el = document.getElementById(spanId);
            if (el) el.textContent = data.name;
        }
    } catch (e) {
        console.warn('Failed to fetch group name', e);
        const el = document.getElementById(spanId);
        if (el) el.textContent = 'Private Circle';
    }
}


// DOM Elements
let letWord, us, acronyms, pray, orbs, pill, glyphsEl, nav;
let promptForm, promptInput, circleSelect, circleLabel, prayersView, cardsEl;
let circleModal, passcodeInput, anyCircleBtn, confirmCircleBtn;
let tabPasscode, tabQr, panePasscode, paneQr, btnStartQr;
let passcodeFeedback, qrFeedback;
let detailModal, detailCloseBtnX, detailContentArea;
let btnLoadMore;

document.addEventListener('DOMContentLoaded', async () => {
    // 1. Bind elements
    letWord = document.getElementById("w-let");
    us = document.getElementById("w-us");
    acronyms = document.querySelector(".acronyms");
    pray = document.getElementById("w-pray");
    orbs = document.getElementById("orbField");
    pill = document.getElementById("pillWrap");
    glyphsEl = document.getElementById("prayGlyphs");
    nav = document.getElementById("nav");
    promptForm = document.getElementById("promptForm");
    promptInput = document.getElementById("promptInput");
    circleSelect = document.getElementById("circleSelect");
    circleLabel = document.getElementById("circleLabel");
    prayersView = document.getElementById("prayersView");
    cardsEl = document.getElementById("cards");

    // Modal elements
    circleModal = document.getElementById("circleModal");
    passcodeInput = document.getElementById("passcodeInput");
    anyCircleBtn = document.getElementById("anyCircleBtn");
    confirmCircleBtn = document.getElementById("confirmCircleBtn");
    tabPasscode = document.getElementById("tab-passcode");
    tabQr = document.getElementById("tab-qr");
    panePasscode = document.getElementById("pane-passcode");
    paneQr = document.getElementById("pane-qr");
    btnStartQr = document.getElementById("btn-start-qr");
    passcodeFeedback = document.getElementById("passcode-feedback");
    qrFeedback = document.getElementById("qr-feedback");

    // Detail modal
    detailModal = document.getElementById("detailModal");
    detailCloseBtnX = document.getElementById("btn-detail-close-x");
    detailContentArea = document.getElementById("detail-content-area");

    // Pagination
    btnLoadMore = document.getElementById("btn-load-more");

    // 2. Initialize device ID
    currentDeviceId = await initDevice();

    // 3. Setup listeners
    initEventListeners();

    // 4. Fetch prayers
    await fetchUserPrayers();

    // 4.5. Check for groupId query param to pre-select circle
    await checkUrlForGroup();

    // 5. Play introduction animations
    play();
});

async function checkUrlForGroup() {
    const urlParams = new URLSearchParams(window.location.search);
    const groupId = urlParams.get('groupId');
    if (!groupId) return;

    try {
        const res = await fetch(`/api/groups/${groupId}`);
        if (res.ok) {
            const group = await res.json();
            selectedGroupId = group.groupId;
            selectedGroupName = group.name;
            circleLabel.textContent = group.name;
            circleSelect.classList.add("specific");
            circleSelect.setAttribute("aria-label", "Select circle - currently " + group.name);
        }
    } catch (e) {
        console.warn('Failed to pre-select group from URL param', e);
    }
}

// === 1. Device ID Management ===
async function initDevice() {
    let deviceId = localStorage.getItem('prayer-link-device-id');
    if (deviceId) {
        // Ping seen status in the background
        fetch(`/api/identity/${deviceId}/seen`, { method: 'PUT' }).catch((err) =>
            console.warn('Failed to update seen status', err)
        );
        return deviceId;
    }

    try {
        const res = await fetch('/api/identity/me');
        if (res.ok) {
            const data = await res.json();
            deviceId = data.deviceId;
            localStorage.setItem('prayer-link-device-id', deviceId);
            return deviceId;
        }
    } catch (e) {
        console.warn('Failed to lookup device from /me endpoint', e);
    }

    deviceId = generateUUID();
    try {
        const res = await fetch('/api/identity/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ deviceId }),
        });
        if (res.ok) {
            localStorage.setItem('prayer-link-device-id', deviceId);
        }
    } catch (e) {
        console.error('Failed to register new device ID', e);
    }
    return deviceId;
}

function generateUUID() {
    return ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, (c) =>
        (c ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (c / 4)))).toString(16)
    );
}

// === 2. Fetch User Prayers ===
async function fetchUserPrayers() {
    if (!currentDeviceId) return;
    try {
        const res = await fetch(`/api/prayers?deviceId=${currentDeviceId}`);
        if (res.ok) {
            prayersList = await res.json();
            if (document.body.classList.contains("view-mine")) {
                renderCards();
            }
        }
    } catch (e) {
        console.error('Failed to fetch user prayers', e);
    }
}

// === 3. Render Cards ===
function renderCards() {
    cardsEl.innerHTML = "";
    if (prayersList.length === 0) {
        cardsEl.innerHTML = '<p class="cards-empty">No prayers yet - submit from the New Prayers screen.</p>';
        btnLoadMore.style.display = 'none';
        return;
    }

    const visiblePrayers = prayersList.slice(0, visibleCount);

    visiblePrayers.forEach((p, i) => {
        const card = document.createElement("article");
        card.className = "pcard";
        if (p._isNewSubmission) {
            card.classList.add("just-added");
            delete p._isNewSubmission;
        }
        card.style.setProperty("--d", (i * 0.06) + "s");

        const targetGroupId = p.groupId || p.assignedGroupId;
        const isSpecific = !!p.groupId;
        const relativeTime = getRelativeTime(new Date(p.createdAt));
        const isClosed = p.status === 'CLOSED';
        const answerText = p.updates && p.updates.length > 0 ? p.updates[0].updateText : '';

        const spanId = `group-label-${p.prayerId}`;
        let initialGroupLabel = 'Any Prayer Group';
        if (targetGroupId) {
            if (groupNamesCache[targetGroupId]) {
                initialGroupLabel = groupNamesCache[targetGroupId];
            } else {
                initialGroupLabel = 'Loading...';
                setTimeout(() => fetchAndCacheGroupName(targetGroupId, spanId), 0);
            }
        }

        let html = `
            <div class="pcard-top">
                <span class="pcard-circle${isSpecific ? ' specific' : ''}" id="${spanId}">${escapeHtml(initialGroupLabel)}</span>
                <span class="pcard-time">${escapeHtml(relativeTime)}</span>
            </div>
            <p class="pcard-text">"${escapeHtml(p.prayerText)}"</p>
        `;

        if (isClosed && answerText) {
            html += `
                <div class="pcard-answer">
                    <span class="pcard-answer-label">Answer</span>
                    ${escapeHtml(answerText)}
                </div>
            `;
        }

        html += `
            <div style="display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem; margin-top: auto;">
                <span class="pcard-status${isClosed ? ' answered' : ''}">${isClosed ? 'Answered' : 'Praying'}</span>
                <span class="badge-prayer">🙏 ${p.prayedForCount || 0}</span>
            </div>
        `;

        card.innerHTML = html;

        // Click to open detailed modal
        card.addEventListener('click', () => openDetailModal(p.prayerId));

        cardsEl.appendChild(card);
    });

    // Pagination visibility
    if (prayersList.length > visibleCount) {
        btnLoadMore.style.display = 'inline-block';
    } else {
        btnLoadMore.style.display = 'none';
    }
}

// === 4. Introduction Animations ===
function buildPray() {
    glyphsEl.innerHTML = "";
    CONFIG.managementWord.split("").forEach((ch, i) => {
        const ltr = document.createElement("span");
        ltr.className = "ltr";
        ltr.style.setProperty("--i", i);
        const glyph = document.createElement("span");
        glyph.className = "glyph";
        glyph.textContent = ch;
        ltr.appendChild(glyph);
        glyphsEl.appendChild(ltr);
    });
    pray.className = "management k-fill";
}

function reset() {
    letWord.classList.remove("in");
    us.classList.remove("in");
    acronyms.classList.remove("to-outline");
    orbs.classList.remove("orbs-live");
    pill.classList.remove("in");
    nav.classList.remove("in");
    buildPray();
}

async function play() {
    reset();
    void pray.offsetWidth;
    await sleep(CONFIG.timings.start);
    letWord.classList.add("in");
    await sleep(CONFIG.timings.afterLet);
    us.classList.add("in");
    await sleep(CONFIG.timings.afterUs);
    orbs.classList.add("orbs-live");
    pray.classList.add("in");
    acronyms.classList.add("to-outline");
    await sleep(CONFIG.timings.afterPray);
    pill.classList.add("in");
    await sleep(CONFIG.timings.afterPill);
    nav.classList.add("in");
}

// === 5. View Switcher ===
function showView(view) {
    nav.querySelectorAll(".nav-btn").forEach((b) =>
        b.classList.toggle("active", b.dataset.view == view)
    );
    if (view === "mine") {
        document.body.classList.add("view-mine");
        prayersView.setAttribute("aria-hidden", "false");
        orbs.classList.add("orbs-live");
        renderCards();
    } else {
        document.body.classList.remove("view-mine");
        prayersView.setAttribute("aria-hidden", "true");
    }
}

// === 6. Modal Functions ===
function openCircleModal() {
    circleModal.classList.add("open");
    passcodeInput.focus();
}

async function stopScanner() {
    if (html5QrCode) {
        try {
            await html5QrCode.stop();
        } catch (e) {
            console.warn(e);
        }
        html5QrCode = null;
    }
    btnStartQr.disabled = false;
    btnStartQr.textContent = '📷 Launch Scanner';
}

async function closeCircleModal() {
    circleModal.classList.remove("open");
    await stopScanner();
    passcodeFeedback.style.display = 'none';
    qrFeedback.style.display = 'none';
}

/* v8 ignore start */
function resetToAllCircles() {
    selectedGroupId = null;
    selectedGroupName = 'Any Prayer Group';
    circleLabel.textContent = "Any Prayer Group";
    circleSelect.classList.remove("specific");
    circleSelect.setAttribute("aria-label", "Select Circle - Current Any Prayer Group");
    passcodeInput.value = "";
}
/* v8 ignore stop */

async function handlePasscodeConfirm() {
    const code = passcodeInput.value.trim().toUpperCase();
    /* v8 ignore start */
    if (!code) {
        resetToAllCircles();
        closeCircleModal();
        return;
    }

    if (code.length < 3 || code.length > 12) {
        passcodeFeedback.textContent = "Passcode must be between 3 and 12 characters.";
        passcodeFeedback.className = "feedback-msg error";
        passcodeFeedback.style.display = "block";
        return;
    }
    /* v8 ignore stop */

    passcodeFeedback.textContent = "Validating...";
    passcodeFeedback.className = "feedback-msg";
    passcodeFeedback.style.display = "block";

    try {
        const res = await fetch(`/api/groups/validate?passcode=${code}`);
        if (res.ok) {
            const group = await res.json();
            selectedGroupId = group.groupId;
            selectedGroupName = group.name;

            circleLabel.textContent = group.name;
            circleSelect.classList.add("specific");
            circleSelect.setAttribute("aria-label", "Select circle - currently " + group.name);
            closeCircleModal();
        } else {
            throw new Error('Invalid code');
        }
    } catch (e) {
        passcodeFeedback.textContent = "Invalid passcode. Please try again.";
        passcodeFeedback.className = "feedback-msg error";
    }
}

// === 7. Detail & Update Modal ===
async function openDetailModal(prayerId) {
    detailContentArea.innerHTML = `
        <div style="text-align: center; padding: 2rem;">
            <div class="spinner"></div>
            <p style="margin-top: 1rem; color: var(--muted); font-size: 0.9rem;">Fetching details...</p>
        </div>
    `;
    detailModal.classList.add('open');

    try {
        const res = await fetch(`/api/prayers/${prayerId}`);
        if (!res.ok) throw new Error('Failed to load details.');
        const prayer = await res.json();

        const relativeTime = getRelativeTime(new Date(prayer.createdAt));
        const targetGroupId = prayer.groupId || prayer.assignedGroupId;
        let groupLabel = 'Any Prayer Group';
        if (targetGroupId) {
            if (groupNamesCache[targetGroupId]) {
                groupLabel = groupNamesCache[targetGroupId];
            } else {
                /* v8 ignore start */
                groupLabel = 'Loading...';
                fetch(`/api/groups/${targetGroupId}`)
                    .then(res => res.ok ? res.json() : null)
                    .then(group => {
                        if (group) {
                            groupNamesCache[targetGroupId] = group.name;
                            const el = document.getElementById('detail-group-label');
                            if (el) el.textContent = group.name;
                        }
                    })
                    .catch(() => {
                        const el = document.getElementById('detail-group-label');
                        if (el) el.textContent = 'Private Circle';
                    });
                /* v8 ignore stop */
            }
        }
        const isClosed = prayer.status === 'CLOSED';
        const answerText = prayer.updates && prayer.updates.length > 0 ? prayer.updates[0].updateText : '';

        let innerHTML = `
            <div class="pcard-top" style="margin-bottom: 1rem;">
                <span class="pcard-circle${prayer.groupId ? ' specific' : ''}" id="detail-group-label">${escapeHtml(groupLabel)}</span>
                <span class="pcard-time">${escapeHtml(relativeTime)}</span>
            </div>
            <blockquote style="font-size: 1.15rem; line-height: 1.5; color: var(--offwhite); margin-bottom: 1.5rem; font-style: italic;">
                "${escapeHtml(prayer.prayerText)}"
            </blockquote>
            <div style="display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem;">
                <span class="pcard-status${isClosed ? ' answered' : ''}">${isClosed ? 'Answered' : 'Praying'}</span>
                <span class="badge-prayer" style="background: rgba(244, 241, 234, 0.1);">🙏 ${prayer.prayedForCount || 0} intercessors</span>
            </div>
        `;

        if (isClosed) {
            if (answerText) {
                innerHTML += `
                    <div class="pcard-answer" style="margin-bottom: 1.5rem;">
                        <span class="pcard-answer-label">Answer</span>
                        ${escapeHtml(answerText)}
                    </div>
                `;
            }
            innerHTML += `
                <div class="circle-actions">
                    <button type="button" class="btn-ghost" id="btn-close-detail">Close</button>
                </div>
            `;
        } else {
            // Open prayer - show update form
            innerHTML += `
                <form id="update-prayer-form" style="border-top: 1px solid rgba(244, 241, 234, 0.1); padding-top: 1.5rem; margin-top: 1rem;">
                    <p style="font-size: 0.85rem; color: var(--muted); margin-bottom: 0.75rem;">
                        💡 Adding an update will close this prayer request and notify your group circle.
                    </p>
                    <textarea id="updateTextarea" placeholder="How has this prayer been answered?" required style="
                        width: 100%; height: 100px; padding: 0.75rem; border-radius: 12px;
                        background: rgba(244, 241, 234, 0.05); border: 1px solid rgba(244, 241, 234, 0.14);
                        color: var(--offwhite); font-family: inherit; font-size: 0.95rem; resize: none; outline: none;
                        margin-bottom: 0.5rem; transition: border-color 0.2s ease;
                    "></textarea>
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.2rem;">
                        <span id="update-char-counter" style="font-size: 0.75rem; color: var(--muted);">0 / 1000</span>
                        <span id="update-validation-feedback" style="font-size: 0.75rem; color: var(--orb-magenta); display: none;">Min 10 characters</span>
                    </div>
                    <div class="circle-actions">
                        <button type="button" class="btn-ghost" id="btn-cancel-detail">Keep Open</button>
                        <button type="submit" class="btn-primary" id="btn-submit-update" disabled>Send Update & Close</button>
                    </div>
                </form>
            `;
        }

        detailContentArea.innerHTML = innerHTML;

        // Add form handlers if open
        if (!isClosed) {
            const form = document.getElementById('update-prayer-form');
            const textarea = document.getElementById('updateTextarea');
            const charCounter = document.getElementById('update-char-counter');
            const valFeedback = document.getElementById('update-validation-feedback');
            const submitBtn = document.getElementById('btn-submit-update');
            const cancelBtn = document.getElementById('btn-cancel-detail');

            cancelBtn.addEventListener('click', closeDetailModal);

            textarea.addEventListener('input', () => {
                const len = textarea.value.trim().length;
                charCounter.textContent = `${len} / 1000`;
                if (len >= 10) {
                    valFeedback.style.display = 'none';
                    submitBtn.disabled = false;
                } else {
                    submitBtn.disabled = true;
                    if (len > 0) {
                        valFeedback.style.display = 'inline';
                    }
                }
            });

            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                const updateText = textarea.value.trim();
                if (updateText.length < 10) return;

                if (!confirm('Are you sure you want to close this prayer? This will notify your intercessors and cannot be undone.')) {
                    return;
                }

                submitBtn.disabled = true;
                submitBtn.textContent = 'Sending...';

                try {
                    const updateRes = await fetch(`/api/prayers/${prayerId}/updates`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-Device-ID': currentDeviceId,
                        },
                        body: JSON.stringify({ updateText }),
                    });

                    if (updateRes.ok) {
                        showToast('Prayer closed & intercessors notified.', 'success');
                        closeDetailModal();
                        await fetchUserPrayers();
                    } else {
                        /* v8 ignore start */
                        const errData = await updateRes.json();
                        showToast(errData.error || 'Failed to submit update.', 'error');
                        submitBtn.disabled = false;
                        submitBtn.textContent = 'Send Update & Close';
                        /* v8 ignore stop */
                    }
                } catch (err) {
                    /* v8 ignore start */
                    console.error(err);
                    showToast('Network error occurred.', 'error');
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Send Update & Close';
                    /* v8 ignore stop */
                }
            });
        } else {
            document.getElementById('btn-close-detail').addEventListener('click', closeDetailModal);
        }
    } catch (e) {
        /* v8 ignore start */
        detailContentArea.innerHTML = `
            <div style="text-align: center; padding: 2rem; color: var(--orb-magenta);">
                <p>⚠️ Failed to load prayer details.</p>
                <button type="button" class="btn-ghost mt-4" style="margin-top: 1rem;" onclick="closeDetailModal()">Close</button>
            </div>
        `;
        /* v8 ignore stop */
    }
}

function closeDetailModal() {
    detailModal.classList.remove('open');
}

// === 8. Event Listeners & Binding ===
function initEventListeners() {
    nav.querySelectorAll(".nav-btn").forEach((b) =>
        b.addEventListener("click", () => showView(b.dataset.view))
    );

    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape") {
            closeCircleModal();
            closeDetailModal();
        }
    });

    promptInput.addEventListener("focus", () => pill.classList.add("expanded"));
    promptInput.addEventListener("blur", () => {
        if (circleModal.classList.contains("open")) return;
        if (!promptInput.value.trim() && !circleSelect.classList.contains("specific")) {
            pill.classList.remove("expanded");
        }
    });

    circleSelect.addEventListener("mousedown", (e) => e.preventDefault());
    circleSelect.addEventListener("click", openCircleModal);

    confirmCircleBtn.addEventListener("click", handlePasscodeConfirm);
    anyCircleBtn.addEventListener("click", () => {
        resetToAllCircles();
        closeCircleModal();
    });

    passcodeInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            handlePasscodeConfirm();
        }
    });

    circleModal.addEventListener("click", (e) => {
        if (e.target === circleModal) closeCircleModal();
    });

    detailModal.addEventListener("click", (e) => {
        if (e.target === detailModal) closeDetailModal();
    });
    detailCloseBtnX.addEventListener("click", closeDetailModal);

    // Prompt Form Submit
    promptForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const text = promptInput.value.trim();
        if (!text) return;

        if (text.length < 10) {
            showToast("Prayer must be at least 10 characters.", "error");
            return;
        }

        const sendBtn = promptForm.querySelector('.send-btn');
        sendBtn.disabled = true;

        // 1. Slow glow phase on the full bar
        pill.classList.add("glowing-bar");

        // Start API request in background
        const apiPromise = fetch('/api/prayers', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Device-ID': currentDeviceId,
            },
            body: JSON.stringify({
                deviceId: currentDeviceId,
                prayerText: text,
                groupId: selectedGroupId,
            }),
        });

        try {
            // Wait 800ms for the slow glow phase to run
            await sleep(800);

            // 2. Collapse glowing bar into circular orb
            pill.classList.remove("glowing-bar");
            pill.classList.add("submitting");
            const collapseStart = Date.now();

            const res = await apiPromise;

            if (res.ok) {
                const prayer = await res.json();
                
                // Set flag to render it hidden (.just-added)
                prayer._isNewSubmission = true;
                prayersList.unshift(prayer);

                // Ensure the collapse transition completes (minimum 450ms)
                const elapsedCollapse = Date.now() - collapseStart;
                if (elapsedCollapse < 450) {
                    await sleep(450 - elapsedCollapse);
                }

                // 2. Switch view to "My Prayers" which renders cards
                showView("mine");

                // Get the newly rendered card at the top
                const firstCard = cardsEl.querySelector('.pcard.just-added');
                if (firstCard) {
                    // 3. Measure pill and card positions to calculate vector
                    const cardRect = firstCard.getBoundingClientRect();
                    const pillRect = pill.getBoundingClientRect();
                    const dx = (cardRect.left + cardRect.width / 2) - (pillRect.left + pillRect.width / 2);
                    const dy = (cardRect.top + cardRect.height / 2) - (pillRect.top + pillRect.height / 2);

                    // 4. Animate the orb exactly to the card center (slower flight: 1.4s)
                    // Keep opacity at 1 inline to override body.view-mine opacity: 0
                    pill.style.opacity = '1';
                    pill.style.transition = 'transform 1.4s cubic-bezier(0.16, 1, 0.3, 1)';
                    pill.style.transform = `translate(calc(-50% + ${dx}px), ${dy}px) scale(0.15)`;

                    // Wait for flight animation to get near the end (1350ms)
                    /* v8 ignore start */
                    await sleep(1350);

                    // Fade out the flight orb quickly as it lands
                    pill.style.transition = 'opacity 0.25s ease, filter 0.25s ease';
                    pill.style.opacity = '0';
                    pill.style.filter = 'blur(6px)';

                    // 5. Trigger glow-reveal morph inheritance on card
                    firstCard.classList.remove('just-added');
                    firstCard.classList.add('glow-reveal');
                    
                    // Wait for the card morph animation to finish
                    await sleep(850);
                }

                // Reset states and values
                promptInput.value = "";
                resetToAllCircles();

                // Clear inline style overrides
                pill.style.transition = '';
                pill.style.transform = '';
                pill.style.opacity = '';
                pill.style.filter = '';
                pill.classList.remove("submitting");
                pill.classList.remove("expanded");
            } else {
                showToast("Failed to share prayer request.", "error");
                pill.classList.remove("submitting");
                pill.classList.remove("glowing-bar");
            }
        } catch (err) {
            console.error(err);
            showToast("Network error submitting prayer.", "error");
            pill.classList.remove("submitting");
            pill.classList.remove("glowing-bar");
        } finally {
            sendBtn.disabled = false;
        }
        /* v8 ignore stop */
    });

    // Modal Tab logic
    tabPasscode.addEventListener('click', async () => {
        tabPasscode.classList.add('active');
        tabQr.classList.remove('active');
        panePasscode.style.display = 'block';
        paneQr.style.display = 'none';
        await stopScanner();
    });

    tabQr.addEventListener('click', () => {
        tabQr.classList.add('active');
        tabPasscode.classList.remove('active');
        paneQr.style.display = 'block';
        panePasscode.style.display = 'none';
    });

    // QR scanner logic
    btnStartQr.addEventListener('click', () => {
        qrFeedback.style.display = 'none';
        btnStartQr.disabled = true;
        btnStartQr.textContent = 'Initializing Camera...';

        html5QrCode = new Html5Qrcode('qr-reader');
        html5QrCode.start(
            { facingMode: 'environment' },
            {
                fps: 10,
                qrbox: { width: 220, height: 220 },
            },
            async (qrText) => {
                const match = qrText.match(/(?:group\/|groupId=)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i);
                const extractedId = match ? match[1] : qrText.trim();

                qrFeedback.textContent = 'Verifying scanned group...';
                qrFeedback.className = 'feedback-msg';
                qrFeedback.style.display = 'block';

                try {
                    const grpRes = await fetch(`/api/groups/${extractedId}`);
                    if (grpRes.ok) {
                        const group = await grpRes.json();
                        selectedGroupId = group.groupId;
                        selectedGroupName = group.name;

                        qrFeedback.textContent = `Scanned and verified: ${group.name}`;
                        qrFeedback.className = 'feedback-msg success';

                        await stopScanner();

                        /* v8 ignore start */
                        setTimeout(() => {
                            circleLabel.textContent = group.name;
                            circleSelect.classList.add("specific");
                            circleSelect.setAttribute("aria-label", "Select circle - currently " + group.name);
                            closeCircleModal();
                        }, 1000);
                    } else {
                        throw new Error('Invalid QR code');
                    }
                } catch (e) {
                    qrFeedback.textContent = 'Could not resolve group from scanned QR code.';
                    qrFeedback.className = 'feedback-msg error';
                }
                /* v8 ignore stop */
            },
            () => {} // Skip frame processing errors
        ).catch((err) => {
            console.error(err);
            btnStartQr.disabled = false;
            btnStartQr.textContent = 'Retry Scanner';
            qrFeedback.textContent = 'Camera permission denied or camera not found.';
            qrFeedback.className = 'feedback-msg error';
            qrFeedback.style.display = 'block';
        });
    });

    // Pagination
    btnLoadMore.addEventListener('click', () => {
        visibleCount += 20;
        renderCards();
    });
}

// === 9. Utility Helpers ===
function getRelativeTime(date) {
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.round(diffMs / 60000);
    const diffHours = Math.round(diffMs / 3600000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;

    const days = Math.round(diffHours / 24);
    return `${days}d ago`;
}

function escapeHtml(unsafe) {
    return unsafe
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function showToast(message, type = 'info') {
    const existing = document.querySelector('.toast');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    document.body.appendChild(toast);

    /* v8 ignore start */
    setTimeout(() => {
        toast.style.animation = 'toast-out 0.2s forwards';
        toast.addEventListener('animationend', () => {
            toast.remove();
        });
    }, 4000);
    /* v8 ignore stop */
}
