// pray.js - Entry point for the intercessor action page with interactive animations
import './shared/apiClient.js';
import './styles/tokens.css';
import './styles/reset.css';
import './styles/base.css';
import './styles/components.css';
import './styles/animations.css';
import './styles/layout.css';

console.log('Prayer Link intercessor action page initialized.');

document.addEventListener('DOMContentLoaded', () => {
  const loadingText = document.getElementById('loading-text');
  const card = document.getElementById('prayer-display-card');
  const prayContainer = document.getElementById('pray-container');
  const statusMessageContainer = document.getElementById('status-message-container');
  const groupHeaderTitle = document.getElementById('group-header-title');

  // Extract parameters from URL path: /pray/prayerId/token
  const pathParts = window.location.pathname.split('/');
  let prayerId = null;
  let token = null;

  // Since pathParts starts with "", we look for "/pray/" prefix
  const prayIndex = pathParts.indexOf('pray');
  if (prayIndex !== -1 && prayIndex + 2 < pathParts.length) {
    prayerId = pathParts[prayIndex + 1];
    token = pathParts[prayIndex + 2];
  }

  // Fallback to URL Query Parameters if path format is not used
  const urlParams = new URLSearchParams(window.location.search);
  const prayerIdParam = urlParams.get('prayerId');
  const tokenParam = urlParams.get('token');

  const finalPrayerId = prayerId || prayerIdParam;
  const finalToken = token || tokenParam;

  if (!finalPrayerId || !finalToken) {
    showError('This link is invalid. Missing prayer ID or intercessor token.');
    return;
  }

  // Pre-flight check: validate token format and expiry timestamp
  const decodedToken = decodeURIComponent(finalToken);
  const parts = decodedToken.split('|');
  if (parts.length !== 2 && parts.length !== 3) {
    showError('This link is invalid. Malformed intercessor token.');
    return;
  }
  
  let expiryTimestamp;
  let tokenGroupId = null;
  if (parts.length === 3) {
    tokenGroupId = parts[1];
    expiryTimestamp = parseInt(parts[2], 10);
  } else {
    expiryTimestamp = parseInt(parts[1], 10);
  }

  if (
    isNaN(expiryTimestamp) ||
    expiryTimestamp < Math.floor(Date.now() / 1000)
  ) {
    showError(
      'This link has expired. Intercessor links are valid for 30 days.'
    );
    return;
  }

  // Fetch the prayer details
  fetch(`/api/prayers/${finalPrayerId}`)
    .then((res) => {
      if (res.status === 404) {
        throw new Error('This prayer request could not be found.');
      }
      if (!res.ok) {
        throw new Error(
          'Failed to load prayer details. Please check your connection.'
        );
      }
      return res.json();
    })
    .then((prayer) => {
      // Toggle visibility
      if (statusMessageContainer) statusMessageContainer.style.display = 'none';
      if (prayContainer) prayContainer.style.display = 'block';

      const activeGroupId = tokenGroupId || prayer.assignedGroupId;
      renderPrayer(prayer, decodedToken);
      
      if (activeGroupId) {
        // Fetch group details to display the group name
        fetch(`/api/groups/${activeGroupId}`)
          .then(res => {
            if (res.ok) return res.json();
            return null;
          })
          .then(group => {
            if (group && group.name && groupHeaderTitle) {
              groupHeaderTitle.textContent = `${group.name}`;
            }
          })
          .catch(err => console.error('Error fetching group info', err));

        fetchOtherPrayers(activeGroupId, decodedToken, finalPrayerId);
      }
    })
    .catch((err) => {
      showError(err.message);
    });

  function showError(msg) {
    if (statusMessageContainer) {
      statusMessageContainer.style.display = 'block';
      const loadingEl = document.getElementById('loading-text');
      if (loadingEl) {
        loadingEl.parentElement.innerHTML = `
          <div class="text-center" style="padding: var(--space-4);">
            <span style="font-size: var(--font-size-3xl);">⚠️</span>
            <h3 class="font-display mt-4 mb-4" style="color: var(--color-error); font-size: var(--font-size-lg);">Error</h3>
            <p class="color-text-secondary" style="font-weight: 300; line-height: 1.6;">${msg}</p>
          </div>
        `;
      }
    }
    if (prayContainer) prayContainer.style.display = 'none';
  }

  function fetchOtherPrayers(groupId, tokenVal, primaryPrayerId) {
    const otherPrayersContainer = document.getElementById('other-prayers-container');
    const otherPrayersList = document.getElementById('other-prayers-list');
    
    fetch(`/api/prayers/group/${groupId}?token=${encodeURIComponent(tokenVal)}`)
      .then((res) => {
        if (!res.ok) {
          throw new Error('Failed to load other prayers.');
        }
        return res.json();
      })
      .then((prayers) => {
        const filteredPrayers = prayers.filter(p => p.prayerId !== primaryPrayerId);
        
        if (filteredPrayers.length === 0) {
          if (otherPrayersContainer) otherPrayersContainer.style.display = 'none';
          return;
        }
        
        if (otherPrayersContainer) otherPrayersContainer.style.display = 'block';
        if (otherPrayersList) {
          otherPrayersList.innerHTML = '';
          
          filteredPrayers.forEach(prayer => {
            const item = document.createElement('div');
            item.className = 'card';
            item.style.padding = 'var(--space-4)';
            item.style.borderRadius = 'var(--radius-sm)';
            item.style.display = 'flex';
            item.style.flexDirection = 'column';
            item.style.gap = 'var(--space-3)';
            
            item.innerHTML = `
              <p style="font-weight: 300; line-height: 1.5; color: var(--color-text-primary); white-space: pre-wrap; font-size: var(--font-size-sm); margin: 0;">
                "${prayer.prayerText}"
              </p>
              <div class="flex justify-between items-center" style="border-top: 1px solid rgba(18, 44, 38, 0.03); padding-top: var(--space-2); margin-top: auto;">
                <button class="btn-primary inline-pray-btn" data-id="${prayer.prayerId}" style="min-height: 36px; padding: 0.4rem 1.2rem; font-size: var(--font-size-xs);">
                  Pray
                </button>
                <div class="badge-prayer">
                  <span>🙏</span> <span class="count-val">${prayer.prayedForCount || 0}</span>
                </div>
              </div>
            `;
            
            otherPrayersList.appendChild(item);
            
            const btn = item.querySelector('.inline-pray-btn');
            const countVal = item.querySelector('.count-val');
            
            btn.addEventListener('click', (e) => {
              btn.disabled = true;
              btn.textContent = '...';
              
              fetch(`/api/prayers/${prayer.prayerId}/prayed`, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json'
                },
                body: JSON.stringify({ intercessorToken: tokenVal })
              })
                .then(res => {
                  if (res.status === 409) throw new Error('ALREADY_PRAYED');
                  if (!res.ok) throw new Error('Failed to record prayer');
                  return res.json();
                })
                .then(data => {
                  countVal.textContent = data.prayedForCount;
                  btn.textContent = '✓';
                  btn.style.backgroundColor = 'var(--color-success)';
                  btn.style.borderColor = 'transparent';
                  btn.style.color = 'var(--color-text-inverse)';
                  triggerEmojiBurst(e, btn);
                  showToast('Thank you! Your prayer has been recorded.', 'success');
                })
                .catch(err => {
                  btn.disabled = false;
                  btn.textContent = 'Pray';
                  if (err.message === 'ALREADY_PRAYED') {
                    btn.disabled = true;
                    btn.textContent = '✓';
                    btn.style.backgroundColor = 'var(--color-secondary)';
                    btn.style.color = 'var(--color-text-primary)';
                    showToast("You've already recorded your prayer for this request.", 'info');
                  } else {
                    showToast(err.message, 'error');
                  }
                });
            });
          });
        }
      })
      .catch(err => {
        console.error('Error fetching other group prayers:', err);
      });
  }

  function renderPrayer(prayer, tokenVal) {
    if (loadingText) loadingText.style.display = 'none';

    // Check if the prayer status is CLOSED
    if (prayer.status === 'CLOSED') {
      let updateHtml = '';
      if (prayer.updates && prayer.updates.length > 0) {
        const latestUpdate = prayer.updates[0];
        updateHtml = `
          <div class="mt-6 mb-6" style="background: rgba(74, 222, 128, 0.08); border-left: 4px solid var(--color-success); padding: var(--space-4); border-radius: 0 var(--radius-sm) var(--radius-sm) 0; text-align: left;">
            <p style="font-weight: 600; font-size: var(--font-size-sm); margin-bottom: var(--space-2); color: var(--color-text-primary);">The requester shared this update:</p>
            <p style="font-style: italic; line-height: 1.6; color: var(--color-text-secondary); white-space: pre-wrap;">"${latestUpdate.updateText}"</p>
          </div>
        `;
      }

      card.innerHTML = `
        <div style="text-align: center;">
          <blockquote class="mb-6 font-display" style="font-size: var(--font-size-xl); font-style: italic; line-height: 1.6; color: var(--color-text-primary); border: none; padding: 0;">
            "${prayer.prayerText}"
          </blockquote>
          ${updateHtml}
          <div style="border-top: 1px solid rgba(18, 44, 38, 0.05); padding-top: var(--space-4); color: var(--color-text-secondary); font-weight: 300;">
            <p>This prayer request has been closed.</p>
          </div>
        </div>
      `;
      return;
    }

    // Render OPEN prayer state
    card.innerHTML = `
      <div>
        <blockquote class="mb-8 font-display" id="prayer-text" style="font-size: var(--font-size-xl); font-style: italic; line-height: 1.6; color: var(--color-text-primary); text-align: center; border: none; padding: 0;">
          "${prayer.prayerText}"
        </blockquote>
        
        <div id="thank-you-message" style="display: none; text-align: center; margin-bottom: var(--space-6); background: rgba(74, 222, 128, 0.08); border: 1px solid var(--color-success); border-radius: var(--radius-sm); padding: var(--space-4);">
          <p style="color: var(--color-text-primary); font-weight: 500;">Thank you for praying! 🙏</p>
          <p style="color: var(--color-text-secondary); font-size: var(--font-size-sm); margin-top: var(--space-1);">Your prayer has been recorded. You can close this page.</p>
        </div>

        <div class="flex justify-between items-center mt-8" style="border-top: 1px solid rgba(18, 44, 38, 0.05); padding-top: var(--space-4);">
          <button class="btn-primary" id="btn-mark-prayed" style="min-height: 48px; min-width: 180px;">
            I have prayed for this
          </button>
          <div class="badge-prayer" id="prayer-badge">
            <span>🙏</span> <span id="prayer-count">${prayer.prayedForCount || 0}</span>
          </div>
        </div>
      </div>
    `;

    const btnMarkPrayed = document.getElementById('btn-mark-prayed');
    const countEl = document.getElementById('prayer-count');
    const thankYouMsg = document.getElementById('thank-you-message');

    if (btnMarkPrayed) {
      btnMarkPrayed.addEventListener('click', (e) => {
        btnMarkPrayed.disabled = true;
        btnMarkPrayed.textContent = 'Recording...';

        fetch(`/api/prayers/${prayer.prayerId}/prayed`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ intercessorToken: tokenVal }),
        })
          .then((res) => {
            if (res.status === 409) {
              throw new Error('ALREADY_PRAYED');
            }
            if (res.status === 401) {
              throw new Error('This link is invalid or has expired.');
            }
            if (!res.ok) {
              throw new Error(
                'Failed to record prayer action. Please try again.'
              );
            }
            return res.json();
          })
          .then((data) => {
            if (countEl) countEl.textContent = data.prayedForCount;

            btnMarkPrayed.textContent = 'Prayer Offered ✓';
            btnMarkPrayed.style.backgroundColor = 'var(--color-success)';
            btnMarkPrayed.style.borderColor = 'transparent';
            btnMarkPrayed.style.color = 'var(--color-text-inverse)';

            if (thankYouMsg) thankYouMsg.style.display = 'block';

            btnMarkPrayed.style.transform = 'scale(1.05)';
            setTimeout(() => {
              btnMarkPrayed.style.transform = 'scale(1)';
            }, 150);

            triggerEmojiBurst(e, btnMarkPrayed);
            showToast('Thank you! Your prayer has been recorded.', 'success');
          })
          .catch((err) => {
            btnMarkPrayed.disabled = false;
            btnMarkPrayed.textContent = 'I have prayed for this';

            if (err.message === 'ALREADY_PRAYED') {
              btnMarkPrayed.disabled = true;
              btnMarkPrayed.textContent = 'Already Offered 🙏';
              btnMarkPrayed.style.backgroundColor = 'var(--color-secondary)';
              btnMarkPrayed.style.color = 'var(--color-text-primary)';
              showToast(
                "You've already recorded your prayer for this request. Thank you! 🙏",
                'info'
              );
            } else {
              showToast(err.message, 'error');
            }
          });
      });
    }
  }

  // Cursor movement parallax animation
  document.addEventListener('mousemove', (e) => {
    const x = window.innerWidth / 2 - e.clientX;
    const y = window.innerHeight / 2 - e.clientY;

    const wrappers = document.querySelectorAll('.blob-wrapper');
    wrappers.forEach((wrapper) => {
      const styleAttr = wrapper.getAttribute('style') || '';
      const match = styleAttr.match(/--factor:\s*(-?\d+(\.\d+)?)/);
      const factor = match ? parseFloat(match[1]) : 0.1;
      const tx = x * factor * 0.08;
      const ty = y * factor * 0.08;
      wrapper.style.transform = `translate3d(${tx}px, ${ty}px, 0)`;
    });
  });

  // Scroll movement parallax animation
  window.addEventListener('scroll', () => {
    const scrolled = window.pageYOffset;
    const header = document.querySelector('header');
    if (header) {
      header.style.transform = `translate3d(0, ${scrolled * 0.15}px, 0)`;
      header.style.opacity = `${Math.max(0, 1 - scrolled / 400)}`;
    }
  });
});

function triggerEmojiBurst(event, anchorElement) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return;
  }
  const burstCount = 6;
  const emojis = ['🙏', '✨', '🤍', '🕊️', '🌸'];

  for (let i = 0; i < burstCount; i++) {
    const floating = document.createElement('span');
    floating.className = 'floating-emoji';
    floating.textContent = emojis[Math.floor(Math.random() * emojis.length)];

    const x = event.clientX || anchorElement.getBoundingClientRect().left + 30;
    const y = event.clientY || anchorElement.getBoundingClientRect().top - 15;

    floating.style.position = 'fixed';
    floating.style.left = `${x - 15}px`;
    floating.style.top = `${y - 15}px`;

    const driftX = Math.random() * 50 - 25 + 'px';
    const driftXEnd = Math.random() * 80 - 40 + 'px';
    const rotate = Math.random() * 40 - 20 + 'deg';
    const rotateEnd = Math.random() * 80 - 40 + 'deg';

    floating.style.setProperty('--drift-x', driftX);
    floating.style.setProperty('--drift-x-end', driftXEnd);
    floating.style.setProperty('--rotate', rotate);
    floating.style.setProperty('--rotate-end', rotateEnd);

    document.body.appendChild(floating);
    floating.addEventListener('animationend', () => {
      floating.remove();
    });
  }
}

function showToast(message, type = 'info') {
  const existing = document.querySelector('.toast');
  if (existing) existing.remove();

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;

  document.body.appendChild(toast);

  setTimeout(() => {
    toast.style.animation = 'fade-out var(--transition-slow) forwards';
    toast.addEventListener('animationend', () => {
      toast.remove();
    });
  }, 4000);
}
