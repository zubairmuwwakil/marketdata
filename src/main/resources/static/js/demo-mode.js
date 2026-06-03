window.marketLensDemo = (() => {
  const STORAGE_KEY = 'marketlens_api_key';
  let configPromise = null;

  function getStoredApiKey() {
    return localStorage.getItem(STORAGE_KEY) || '';
  }

  function saveApiKey(value) {
    const next = (value || '').trim();
    if (next) {
      localStorage.setItem(STORAGE_KEY, next);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
    return next;
  }

  async function load() {
    if (!configPromise) {
      configPromise = fetch('/api/v1/demo/config', {
        headers: { 'Content-Type': 'application/json' }
      })
        .then(async (res) => (res.ok ? res.json() : null))
        .catch(() => null)
        .then((config) => {
          if (config && !getStoredApiKey() && config.defaultApiKey) {
            saveApiKey(config.defaultApiKey);
          }
          return config;
        });
    }
    return configPromise;
  }

  function suggestedApiKey(config, currentValue) {
    return (currentValue || '').trim() || getStoredApiKey() || (config && config.defaultApiKey) || 'change-me-user';
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
    getStoredApiKey,
    saveApiKey,
    load,
    suggestedApiKey,
    insertBanner,
    setValueIfEmpty
  };
})();
