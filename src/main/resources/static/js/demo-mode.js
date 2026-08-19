window.marketLensDemo = (() => {
  const STORAGE_KEY = 'marketlens_api_key';
  const DEFAULT_KEY = 'change-me-user';
  let configPromise = null;
  let cachedConfig = null;

  function getRawStoredApiKey() {
    return localStorage.getItem(STORAGE_KEY) || '';
  }

  function getStoredApiKey() {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw && raw.trim()) {
      return raw.trim();
    }
    if (cachedConfig && cachedConfig.defaultApiKey) {
      return cachedConfig.defaultApiKey;
    }
    return DEFAULT_KEY;
  }

  function saveApiKey(value) {
    const next = (value || '').trim();
    if (next) {
      localStorage.setItem(STORAGE_KEY, next);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
    return next || DEFAULT_KEY;
  }

  async function load() {
    if (!configPromise) {
      configPromise = fetch('/api/v1/demo/config', {
        headers: { 'Content-Type': 'application/json' }
      })
        .then(async (res) => (res.ok ? res.json() : null))
        .catch(() => null)
        .then((config) => {
          cachedConfig = config;
          if (config && !getRawStoredApiKey() && config.defaultApiKey) {
            saveApiKey(config.defaultApiKey);
          }
          return config;
        });
    }
    return configPromise;
  }

  function suggestedApiKey(config, currentValue) {
    return (currentValue || '').trim() || (config && config.defaultApiKey) || getStoredApiKey() || DEFAULT_KEY;
  }

  function ensureDemoKey(config, currentValue) {
    return (currentValue || '').trim() || getStoredApiKey() || (config && config.defaultApiKey) || DEFAULT_KEY;
  }

  function ensureModalStyles() {
    if (document.getElementById('marketlens-modal-styles')) return;
    const style = document.createElement('style');
    style.id = 'marketlens-modal-styles';
    style.textContent = `
      .ml-modal-overlay {
        position: fixed;
        inset: 0;
        background: rgba(11, 18, 32, 0.65);
        backdrop-filter: blur(6px);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        padding: 16px;
        animation: mlFadeIn 0.15s ease-out;
      }
      .ml-modal-card {
        background: #ffffff;
        color: #0b1220;
        border-radius: 18px;
        box-shadow: 0 24px 60px rgba(15, 23, 42, 0.25);
        max-width: 480px;
        width: 100%;
        padding: 24px;
        border: 1px solid #e2e8f0;
        box-sizing: border-box;
      }
      .ml-modal-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 12px;
      }
      .ml-modal-title {
        font-family: "Manrope", sans-serif;
        font-size: 1.15rem;
        font-weight: 800;
        margin: 0;
        color: #0b1220;
      }
      .ml-modal-close {
        background: none;
        border: none;
        font-size: 20px;
        cursor: pointer;
        color: #5f6b85;
        line-height: 1;
        padding: 4px 8px;
        border-radius: 6px;
      }
      .ml-modal-close:hover {
        background: #f1f5f9;
        color: #0b1220;
      }
      .ml-modal-desc {
        font-size: 0.88rem;
        color: #5f6b85;
        margin: 0 0 16px 0;
        line-height: 1.45;
      }
      .ml-modal-input-group {
        display: flex;
        flex-direction: column;
        gap: 6px;
        margin-bottom: 16px;
      }
      .ml-modal-input-group label {
        font-size: 0.78rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #5f6b85;
      }
      .ml-modal-input {
        width: 100%;
        padding: 10px 14px;
        border-radius: 10px;
        border: 1px solid #cbd5e1;
        font-family: "IBM Plex Mono", monospace;
        font-size: 0.95rem;
        box-sizing: border-box;
        outline: none;
      }
      .ml-modal-input:focus {
        border-color: #2563eb;
        box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
      }
      .ml-modal-actions {
        display: flex;
        gap: 8px;
        justify-content: flex-end;
        align-items: center;
        flex-wrap: wrap;
      }
      .ml-modal-btn {
        padding: 8px 14px;
        border-radius: 10px;
        font-size: 0.88rem;
        font-weight: 600;
        cursor: pointer;
        border: 1px solid transparent;
        transition: all 0.15s ease;
      }
      .ml-modal-btn-primary {
        background: #2563eb;
        color: #ffffff;
      }
      .ml-modal-btn-primary:hover {
        background: #1d4ed8;
      }
      .ml-modal-btn-secondary {
        background: #f8fafc;
        border-color: #e2e8f0;
        color: #0b1220;
      }
      .ml-modal-btn-secondary:hover {
        background: #f1f5f9;
      }
      .ml-modal-btn-link {
        background: transparent;
        color: #2563eb;
        margin-right: auto;
        padding-left: 0;
      }
      .ml-modal-btn-link:hover {
        text-decoration: underline;
      }
      @keyframes mlFadeIn {
        from { opacity: 0; transform: scale(0.98); }
        to { opacity: 1; transform: scale(1); }
      }
    `;
    document.head.appendChild(style);
  }

  function openKeyModal(options = {}) {
    ensureModalStyles();
    const existing = document.querySelector('.ml-modal-overlay');
    if (existing) existing.remove();

    const currentKey = options.currentValue !== undefined ? options.currentValue : getStoredApiKey();
    const defaultKey = (cachedConfig && cachedConfig.defaultApiKey) || DEFAULT_KEY;

    const overlay = document.createElement('div');
    overlay.className = 'ml-modal-overlay';
    overlay.innerHTML = `
      <div class="ml-modal-card" role="dialog" aria-modal="true" aria-labelledby="ml-modal-title">
        <div class="ml-modal-header">
          <h3 class="ml-modal-title" id="ml-modal-title">MarketLens Access Key</h3>
          <button type="button" class="ml-modal-close" aria-label="Close modal">&times;</button>
        </div>
        <p class="ml-modal-desc">
          Sets the gateway authentication key sent in the <code>X-API-Key</code> header to track quotas and access endpoints.
        </p>
        <div class="ml-modal-input-group">
          <label for="ml-modal-key-input">API Key</label>
          <input type="text" id="ml-modal-key-input" class="ml-modal-input" placeholder="e.g. change-me-user" value="${currentKey}" autocomplete="off" spellcheck="false" />
        </div>
        <div class="ml-modal-actions">
          <button type="button" class="ml-modal-btn ml-modal-btn-link" id="ml-modal-default-btn">Use Demo Default (${defaultKey})</button>
          <button type="button" class="ml-modal-btn ml-modal-btn-secondary" id="ml-modal-cancel-btn">Cancel</button>
          <button type="button" class="ml-modal-btn ml-modal-btn-primary" id="ml-modal-save-btn">Save Key</button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);

    const input = overlay.querySelector('#ml-modal-key-input');
    const saveBtn = overlay.querySelector('#ml-modal-save-btn');
    const cancelBtn = overlay.querySelector('#ml-modal-cancel-btn');
    const closeBtn = overlay.querySelector('.ml-modal-close');
    const defaultBtn = overlay.querySelector('#ml-modal-default-btn');

    input.focus();
    input.select();

    const close = () => overlay.remove();

    const handleSave = () => {
      const val = input.value.trim() || defaultKey;
      saveApiKey(val);
      close();
      if (typeof options.onSave === 'function') {
        options.onSave(val);
      }
    };

    saveBtn.addEventListener('click', handleSave);
    defaultBtn.addEventListener('click', () => {
      input.value = defaultKey;
      handleSave();
    });
    cancelBtn.addEventListener('click', () => {
      close();
      if (typeof options.onCancel === 'function') options.onCancel();
    });
    closeBtn.addEventListener('click', () => {
      close();
      if (typeof options.onCancel === 'function') options.onCancel();
    });

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        close();
        if (typeof options.onCancel === 'function') options.onCancel();
      }
    });

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        handleSave();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        close();
        if (typeof options.onCancel === 'function') options.onCancel();
      }
    });
  }

  function attachKeyButton(buttonEl, onKeyChange) {
    if (!buttonEl) return;
    buttonEl.addEventListener('click', (e) => {
      e.preventDefault();
      openKeyModal({
        currentValue: getStoredApiKey(),
        onSave: (newKey) => {
          if (typeof onKeyChange === 'function') {
            onKeyChange(newKey);
          }
        }
      });
    });
  }

  function insertBanner(container, config, text) {
    if (!container || !config || container.querySelector('[data-demo-banner]')) {
      return;
    }
    const banner = document.createElement('div');
    banner.setAttribute('data-demo-banner', 'true');
    banner.style.marginBottom = '16px';
    banner.style.padding = '14px 16px';
    banner.style.borderRadius = '16px';
    banner.style.border = '1px solid rgba(37, 99, 235, 0.18)';
    banner.style.background = 'linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(20, 184, 166, 0.06))';
    banner.style.color = '#0b1220';
    banner.innerHTML = `
      <div style="display:flex; gap:10px; flex-wrap:wrap; align-items:center;">
        <strong style="font-family:'Manrope',sans-serif;">Demo mode</strong>
        <span style="font-family:'IBM Plex Mono',monospace; font-size:12px; color:#1d4ed8;">${config.defaultFrom} → ${config.defaultTo}</span>
        <span style="font-family:'IBM Plex Mono',monospace; font-size:12px; color:#0f766e;">Default key: ${config.defaultApiKey || 'configured in app'}</span>
      </div>
      <div style="margin-top:6px; color:#5f6b85; font-size:13px;">${text}</div>
    `;
    container.prepend(banner);
  }

  function setValueIfEmpty(element, value) {
    if (element && value && !element.value) {
      element.value = value;
    }
  }

  return {
    DEFAULT_KEY,
    getStoredApiKey,
    getRawStoredApiKey,
    saveApiKey,
    load,
    loadDemoConfig: load,
    ensureDemoKey,
    suggestedApiKey,
    openKeyModal,
    attachKeyButton,
    insertBanner,
    setValueIfEmpty
  };
})();
