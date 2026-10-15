// Shared by the dashboard and self-contained offline reports.
(() => {
  const key = 'jvmlab.language';
  const supported = value => value === 'ko' || value === 'en';
  let stored;
  try { stored = localStorage.getItem(key); } catch (_) { /* Storage may be unavailable for file URLs. */ }
  const requested = new URL(location.href).searchParams.get('lang');
  let language = supported(requested) ? requested : supported(stored) ? stored :
    (navigator.language || 'en').toLowerCase().startsWith('ko') ? 'ko' : 'en';
  function apply() {
    document.documentElement.lang = language;
    document.querySelectorAll('[data-en][data-ko]').forEach(node => {
      node.textContent = node.dataset[language];
    });
    document.querySelectorAll('[data-language]').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.language === language));
    });
    window.dispatchEvent(new Event('lab-language-change'));
  }
  window.labI18n = {
    get language() { return language; },
    text(en, ko) { return language === 'ko' ? ko : en; },
    setLanguage(value) {
      if (!supported(value)) return;
      language = value;
      try { localStorage.setItem(key, value); } catch (_) { /* Translation still works without storage. */ }
      try {
        const url = new URL(location.href);
        url.searchParams.set('lang', value);
        history.replaceState(null, '', url);
      } catch (_) { /* Some file URL environments do not allow history updates. */ }
      apply();
    }
  };
  document.querySelectorAll('[data-language]').forEach(button => {
    button.addEventListener('click', () => window.labI18n.setLanguage(button.dataset.language));
  });
  apply();
})();
