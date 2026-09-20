/* Presentation-only interactions; parking data and authentication are unchanged. */
(() => {
    'use strict';
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    document.querySelector('.password-toggle')?.addEventListener('click', event => {
        const field = document.getElementById('password');
        const visible = field.type === 'password';
        field.type = visible ? 'text' : 'password';
        event.currentTarget.textContent = visible ? 'ซ่อน' : 'แสดง';
        event.currentTarget.setAttribute('aria-pressed', String(visible));
    });
    document.querySelector('.scene-drive')?.addEventListener('click', () => {
        const scene = document.querySelector('.mini-parking');
        if (scene.classList.contains('driving')) return;
        scene.classList.add('driving');
        window.setTimeout(() => scene.classList.remove('driving'), 1600);
    });
    document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button || button.disabled || reducedMotion.matches) return;
        button.animate([
            { filter: 'brightness(1)' },
            { filter: 'brightness(1.22)' },
            { filter: 'brightness(1)' }
        ], { duration: 260 });
    });
})();
