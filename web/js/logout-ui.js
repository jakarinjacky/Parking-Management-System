/* Visible session controls used by both local mode and GitHub Pages demo mode. */
(function () {
    function buildSessionControls() {
        if (document.getElementById('sessionStatusPanel')) return;

        const header = document.querySelector('.top-nav');
        if (!header) return;

        const panel = document.createElement('div');
        panel.id = 'sessionStatusPanel';
        panel.className = 'session-status-panel';

        const badge = document.createElement('span');
        badge.id = 'currentUserBadge';
        badge.className = 'user-badge';
        const username = localStorage.getItem('username') || appState?.currentUser?.username || 'ผู้ใช้งาน';
        const user = appState?.currentUser;
        const role = { owner: 'เจ้าของ', admin: 'ผู้ดูแล', staff: 'พนักงาน' }[user?.role] || 'กำลังตรวจสอบสิทธิ์';
        badge.textContent = `${user?.displayName || username} (${role})`;

        const logoutButton = document.createElement('button');
        logoutButton.type = 'button';
        logoutButton.className = 'logout-btn';
        logoutButton.innerHTML = 'ออกจากระบบ';
        logoutButton.title = 'Logout';
        logoutButton.addEventListener('click', async () => {
            if (!confirm('ต้องการออกจากระบบใช่หรือไม่?')) return;
            logoutButton.disabled = true;
            try {
                if (typeof window.logout === 'function') {
                    await window.logout();
                    return;
                }
            } catch (error) {
                console.warn('Logout API unavailable; clearing local session instead.', error);
            }
            localStorage.removeItem('isLoggedIn');
            localStorage.removeItem('username');
            window.location.href = 'login.html';
        });

        panel.appendChild(badge);
        panel.appendChild(logoutButton);
        header.appendChild(panel);
    }

    document.addEventListener('DOMContentLoaded', buildSessionControls);
})();
