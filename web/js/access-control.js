/* UI and demo permissions. Java independently authorizes every protected API.
 * GitHub Pages is a client-side demonstration, not a security boundary.
 */
(() => {
    'use strict';
    const tabs = {
        owner: ['lot-view', 'entry-gate', 'exit-cashier', 'tickets-history', 'vehicle-history', 'reservations', 'memberships', 'dashboard', 'oop-docs', 'ai-ops'],
        admin: ['lot-view', 'entry-gate', 'exit-cashier', 'tickets-history', 'vehicle-history', 'reservations', 'memberships', 'oop-docs'],
        staff: ['lot-view', 'entry-gate', 'exit-cashier']
    };
    let role = null;
    window.canAccessTab = id => (tabs[role] || []).includes(id);
    window.applyRolePermissions = user => {
        role = Object.hasOwn(tabs, user?.role) ? user.role : null;
        document.querySelectorAll('.tab-btn').forEach(button => {
            const id = button.getAttribute('onclick')?.match(/switchTab\('([^']+)'\)/)?.[1];
            if (id) button.hidden = !window.canAccessTab(id);
        });
        document.querySelectorAll('.tab-content').forEach(section => {
            section.hidden = !window.canAccessTab(section.id.replace('tab-', ''));
        });
        document.querySelectorAll('#clockController, .metric-pill.revenue, .floating-ai-launcher, .floating-copilot-window').forEach(el => {
            el.hidden = role !== 'owner';
        });
        document.querySelectorAll('button[onclick]').forEach(button => {
            const name = button.getAttribute('onclick')?.match(/^\s*([\w$]+)\(/)?.[1];
            if (access[name]) button.hidden = !window.canAccessTab(access[name]);
        });
        if (role && !window.canAccessTab(document.querySelector('.tab-content.active')?.id.replace('tab-', ''))) switchTab('lot-view');
    };
    const access = {
        loadDailyDashboard: 'dashboard', fastForward: 'dashboard', resetSimTime: 'dashboard',
        loadFeatureLists: 'reservations', createReservation: 'reservations', createMembership: 'memberships',
        loadParkingHistory: 'vehicle-history', exportParkingHistory: 'vehicle-history',
        loadTicketsAndPayments: 'tickets-history', submitFeatureForm: 'reservations',
        loadAiPredictiveData: 'ai-ops', toggleAiHeatmap: 'ai-ops', resetDemoData: 'dashboard',
        sendQuickCopilotPrompt: 'ai-ops', submitCopilotChat: 'ai-ops',
        toggleFloatingCopilot: 'ai-ops', sendFloatingPrompt: 'ai-ops', submitFloatingCopilotChat: 'ai-ops'
    };
    for (const [name, tab] of Object.entries(access)) {
        const original = window[name];
        if (typeof original !== 'function') continue;
        window[name] = function (...args) {
            if (!window.canAccessTab(tab)) { args[0]?.preventDefault?.(); return; }
            return original.apply(this, args);
        };
    }
    window.applyRolePermissions(null);
})();
