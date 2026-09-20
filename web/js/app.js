// --- Auth Guard (เช็กการล็อกอินก่อนเข้าหน้า index.html) ---
// --- Auth Guard System ---
(function checkAuth() {
    const isLoggedIn = localStorage.getItem('isLoggedIn');
    if (isLoggedIn !== 'true') {
        const currentPath = window.location.pathname;
        const loginTarget = currentPath.substring(0, currentPath.lastIndexOf('/') + 1) + 'login.html';
        window.location.href = loginTarget;
    }
})();
/**
 * Smart Parking Management System - Frontend Controller
 * เชื่อมโยงกับ Pure Java SE REST API Server (พอร์ต 8080)
 */

const API_BASE = 'http://localhost:8080/api';

// Application State
let appState = {
    selectedFloor: 1,
    floorsData: [],
    statusData: null,
    selectedPayMethod: 'PROMPTPAY',
    currentFeePreview: null,
    activeTickets: [],
    isServerOnline: false,
    currentUser: null
};

async function initializeSession() {
    try {
        const res = await fetch(`${API_BASE}/session`, { credentials: 'include' });
        if (!res.ok) {
            throw new Error('Session missing');
        }

        const data = await res.json();
        appState.currentUser = data;
        updateCurrentUserBadge(data);
        return true;
    } catch (err) {
        window.location.href = 'login.html';
        return false;
    }
}

function updateCurrentUserBadge(user) {
    window.applyRolePermissions?.(user);
    const badge = document.getElementById('currentUserBadge');
    if (!badge || !user) return;

    const label = { owner: 'เจ้าของ', admin: 'ผู้ดูแล', staff: 'พนักงาน' }[user.role] || 'ไม่มีสิทธิ์';
    badge.innerText = `${user.displayName} (${label})`;
    badge.title = `Username: ${user.username}`;
}

async function logout() {
    try {
        await fetch(`${API_BASE}/logout`, { method: 'POST', credentials: 'include' });
    } catch (err) {
        console.warn('Logout request failed:', err);
    } finally {
        window.location.href = 'login.html';
    }
}

// --- Initialization ---
document.addEventListener('DOMContentLoaded', async () => {
    const sessionReady = await initializeSession();
    if (!sessionReady) return;

    loadSystemStatus();
    loadParkingLotData();
    loadTicketsAndPayments();

    // Auto refresh dashboard every 5 seconds
    setInterval(() => {
        loadSystemStatus();
    }, 5000);
});

// --- Tab Navigation ---
function switchTab(tabId) {
    if (window.canAccessTab && !window.canAccessTab(tabId)) return;
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    const activeBtn = Array.from(document.querySelectorAll('.tab-btn'))
        .find(btn => btn.getAttribute('onclick')?.includes(tabId));
    if (activeBtn) activeBtn.classList.add('active');

    const activeTab = document.getElementById('tab-' + tabId);
    if (activeTab) activeTab.classList.add('active');

    if (tabId === 'lot-view') {
        loadParkingLotData();
    } else if (tabId === 'tickets-history') {
        loadTicketsAndPayments();
    } else if (tabId === 'vehicle-history') {
        loadParkingHistory();
    } else if (tabId === 'exit-cashier') {
        refreshActivePlateChips();
    } else if (tabId === 'dashboard') {
        loadDailyDashboard();
    } else if (tabId === 'reservations' || tabId === 'memberships') {
        loadFeatureLists();
    }
}

function historyDateValue(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function escapeHistoryHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

let historyRequestVersion = 0;
async function loadParkingHistory() {
    const requestVersion = ++historyRequestVersion;
    window.setHistoryExportRecords?.([]);
    const fromInput = document.getElementById('historyFrom');
    const toInput = document.getElementById('historyTo');
    const plateInput = document.getElementById('historyPlate');
    if (!fromInput || !toInput) return;

    const now = new Date();
    if (!toInput.value) toInput.value = historyDateValue(now);
    if (!fromInput.value) {
        const threeMonthsAgo = new Date(now);
        threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);
        fromInput.value = historyDateValue(threeMonthsAgo);
    }

    const params = new URLSearchParams({ from: fromInput.value, to: toInput.value });
    if (plateInput?.value.trim()) params.set('plate', plateInput.value.trim());
    const stillCurrent = () => requestVersion === historyRequestVersion
        && fromInput.value === params.get('from') && toInput.value === params.get('to')
        && (plateInput?.value.trim() || '') === (params.get('plate') || '');

    try {
        const res = await fetch(`${API_BASE}/history?${params}`, { credentials: 'include' });
        const data = await res.json();
        if (!stillCurrent()) return;
        if (!res.ok) throw new Error(data.error || 'ไม่สามารถโหลดประวัติได้');

        document.getElementById('historyEnteredCount').innerText = data.enteredCount || 0;
        document.getElementById('historyExitedCount').innerText = data.exitedCount || 0;
        document.getElementById('historyParkedCount').innerText = data.currentlyParkedCount || 0;
        document.getElementById('historyRevenue').innerText = `฿${Number(data.totalRevenue || 0).toFixed(2)}`;
        document.getElementById('historyRetentionMessage').innerText =
            `แสดง ${data.from} ถึง ${data.to} | ระบบเก็บข้อมูลย้อนหลัง ${data.retentionMonths} เดือน`;
        renderParkingHistory(data.records || []);
    } catch (err) {
        if (!stillCurrent()) return;
        document.getElementById('historyRetentionMessage').innerText = err.message;
        renderParkingHistory([]);
    }
}

function renderParkingHistory(records) {
    window.setHistoryExportRecords?.(records);
    const tbody = document.getElementById('vehicleHistoryTableBody');
    if (!tbody) return;
    if (!records.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-muted" style="text-align:center;">ไม่พบประวัติในช่วงวันที่เลือก</td></tr>';
        return;
    }
    tbody.innerHTML = records.map(item => `<tr>
        <td class="mono text-highlight">${escapeHistoryHtml(item.ticketId)}</td>
        <td><strong>${escapeHistoryHtml(item.licensePlate)}</strong></td>
        <td>${escapeHistoryHtml(item.vehicleTypeDisplay || item.vehicleType)}</td>
        <td>ชั้น ${Number(item.floorNumber || 0)} / ${escapeHistoryHtml(item.slotNumber)}</td>
        <td class="mono">${escapeHistoryHtml(item.entryTime)}</td>
        <td class="mono">${escapeHistoryHtml(item.exitTime || '-')}</td>
        <td><span class="status-tag ${item.status === 'EXITED' ? 'exited' : 'active'}">${item.status === 'EXITED' ? 'ออกแล้ว' : 'ยังอยู่ในลาน'}</span></td>
        <td class="mono text-success">฿${Number(item.fee || 0).toFixed(2)}</td>
    </tr>`).join('');
}

async function loadDailyDashboard() {
    const dateInput = document.getElementById('dashboardDate');
    if (!dateInput) return;
    if (!dateInput.value) dateInput.value = new Date().toISOString().slice(0, 10);
    try {
        const res = await fetch(`${API_BASE}/dashboard/daily?date=${dateInput.value}`, { credentials: 'include' });
        if (!res.ok) throw new Error('Dashboard request failed');
        const data = await res.json();
        document.getElementById('dailyRevenue').innerText = `฿${Number(data.revenue || 0).toFixed(2)}`;
        document.getElementById('dailyOccupancy').innerText = `${data.occupancyRate || 0}%`;
        document.getElementById('dailyReservations').innerText = data.reservationCount || 0;
        document.getElementById('dailyMembers').innerText = data.activeMembers || 0;
        document.getElementById('dashboardMessage').innerText = `${data.occupiedSlots}/${data.totalCapacity} ช่องกำลังใช้งาน | ชำระแล้ว ${data.paidTickets} รายการ`;
        loadFeatureLists();
    } catch (err) {
        document.getElementById('dashboardMessage').innerText = 'ไม่สามารถโหลด Dashboard ได้';
    }
}

async function createReservation(event) {
    event.preventDefault();
    const payload = {
        licensePlate: document.getElementById('reservationPlate').value,
        vehicleType: document.getElementById('reservationType').value,
        requiresCharging: document.getElementById('reservationCharging').checked,
        startTime: document.getElementById('reservationStart').value,
        endTime: document.getElementById('reservationEnd').value
    };
    await submitFeatureForm('/reservations', payload, 'สร้างการจองสำเร็จ');
}

async function loadFeatureLists() {
    try {
        const [reservationResponse, membershipResponse] = await Promise.all([
            fetch(`${API_BASE}/reservations`, { credentials: 'include' }),
            fetch(`${API_BASE}/memberships`, { credentials: 'include' })
        ]);
        if (!reservationResponse.ok || !membershipResponse.ok) throw new Error('Feature list request failed');
        const reservations = await reservationResponse.json();
        const memberships = await membershipResponse.json();
        document.getElementById('reservationsList').innerHTML = reservations.length
            ? reservations.slice(-5).reverse().map(item => `<div class="feature-list-item"><strong>${item.licensePlate}</strong><span>${item.vehicleType} | ${item.startTime.replace('T', ' ')}</span><em>${item.checkedIn ? 'เข้าจอดแล้ว' : item.cancelled ? 'ยกเลิก' : 'รอเข้าจอด'}</em></div>`).join('')
            : '<span class="text-muted">ยังไม่มีข้อมูล</span>';
        document.getElementById('membershipsList').innerHTML = memberships.length
            ? memberships.slice(-5).reverse().map(item => `<div class="feature-list-item"><strong>${item.licensePlate}</strong><span>${item.memberName} | ${item.membershipTypeDisplay || item.membershipType || 'STANDARD_MEMBER'}</span><em>ถึง ${item.validUntil}</em></div>`).join('')
            : '<span class="text-muted">ยังไม่มีข้อมูล</span>';
    } catch (err) {
        document.getElementById('reservationsList').innerHTML = '<span class="text-muted">เข้าสู่ระบบเพื่อดูรายการ</span>';
        document.getElementById('membershipsList').innerHTML = '<span class="text-muted">เข้าสู่ระบบเพื่อดูรายการ</span>';
    }
}

async function createMembership(event) {
    event.preventDefault();
    const payload = {
        memberId: document.getElementById('memberId').value,
        memberName: document.getElementById('memberName').value,
        licensePlate: document.getElementById('memberPlate').value,
        membershipType: document.getElementById('memberType').value,
        validFrom: document.getElementById('memberFrom').value,
        validUntil: document.getElementById('memberUntil').value
    };
    await submitFeatureForm('/memberships', payload, 'บันทึกสมาชิกสำเร็จ');
}

async function submitFeatureForm(path, payload, successMessage) {
    const message = document.getElementById(path === '/reservations' ? 'reservationMessage' : 'membershipMessage');
    try {
        const res = await fetch(`${API_BASE}${path}`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'บันทึกข้อมูลไม่สำเร็จ');
        message.innerText = successMessage;
        loadFeatureLists();
        loadDailyDashboard();
    } catch (err) {
        message.innerText = err.message;
    }
}

// --- API Calls & Data Fetching ---

async function loadSystemStatus() {
    try {
        const res = await fetch(`${API_BASE}/status`, { credentials: 'include' });
        if (!res.ok) throw new Error('Network error');
        const data = await res.json();
        appState.statusData = data;
        appState.isServerOnline = true;

        updateHeaderMetrics(data);
        updateDisplayBoard(data.displayBoardMessage, data.displayBoardTime);
    } catch (err) {
        appState.isServerOnline = false;
        // Mock fallback if user opens index.html directly before starting java server
        console.warn('Backend server not connected. Operating in standalone demo mode.');
        renderDemoStatus();
    }
}

async function loadParkingLotData() {
    try {
        const res = await fetch(`${API_BASE}/lot`, { credentials: 'include' });
        if (!res.ok) throw new Error('Network error');
        const floors = await res.json();
        appState.floorsData = floors;
        renderFloorTabs(floors);
        renderCurrentFloorSlots();
        refreshActivePlateChips();
    } catch (err) {
        console.warn('Using demo lot data');
        renderDemoLotData();
    }
}

async function loadTicketsAndPayments() {
    try {
        const res = await fetch(`${API_BASE}/tickets`, { credentials: 'include' });
        if (!res.ok) throw new Error('Network error');
        const tickets = await res.json();
        appState.activeTickets = tickets;
        renderTicketsTable(tickets);
    } catch (err) {
        renderDemoTicketsTable();
    }
}

// --- UI Rendering ---

function updateHeaderMetrics(data) {
    if (!data) return;
    document.getElementById('simulatedTimeText').innerText = data.simulatedTime || new Date().toLocaleTimeString();
    document.getElementById('headerAvailCount').innerText = data.totalAvailable ?? 0;
    document.getElementById('headerOccCount').innerText = data.totalOccupied ?? 0;
    document.getElementById('headerRevenueText').innerText = `฿${(data.totalRevenue ?? 0).toFixed(2)}`;
}

function updateDisplayBoard(message, time) {
    if (message) {
        document.getElementById('displayBoardMessage').innerText = message;
    }
    if (time) {
        document.getElementById('displayBoardTime').innerText = time;
    }
}

function renderFloorTabs(floors) {
    const container = document.getElementById('floorPillGroup');
    if (!container || !floors.length) return;

    container.innerHTML = floors.map(floor => `
        <button class="floor-btn ${floor.floorNumber === appState.selectedFloor ? 'active' : ''}" 
                onclick="selectFloor(${floor.floorNumber})">
            ชั้น ${floor.floorNumber} (${floor.availableSlots}/${floor.totalSlots} ว่าง)
        </button>
    `).join('');
}

function selectFloor(floorNum) {
    appState.selectedFloor = floorNum;
    const btns = document.querySelectorAll('.floor-btn');
    btns.forEach((btn, idx) => {
        btn.classList.toggle('active', (idx + 1) === floorNum);
    });
    renderCurrentFloorSlots();
}

function renderCurrentFloorSlots() {
    const grid = document.getElementById('slotsGrid');
    if (!grid) return;

    const currentFloor = appState.floorsData.find(f => f.floorNumber === appState.selectedFloor);
    if (!currentFloor) {
        grid.innerHTML = '<p class="text-muted">ไม่พบข้อมูลชั้น</p>';
        return;
    }

    document.getElementById('currentFloorTitle').innerText = currentFloor.floorName;
    document.getElementById('currentFloorStats').innerText = 
        `ว่าง ${currentFloor.availableSlots} ช่อง | จอดอยู่ ${currentFloor.totalSlots - currentFloor.availableSlots} ช่อง`;

    grid.innerHTML = currentFloor.slots.map(slot => {
        const isAvail = slot.isAvailable;
        const statusClass = isAvail ? 'available' : 'occupied';
        const isEv = slot.slotType === 'EV_CHARGING';
        const carIcon = getVehicleIcon(slot.vehicle?.vehicleType);

        return `
            <div class="slot-card ${statusClass} ${isEv ? 'ev-charger' : ''}" onclick="showSlotDetails('${slot.slotNumber}')">
                <div class="slot-card-header">
                    <div>
                        <div class="slot-number">${slot.slotNumber}</div>
                        <div class="slot-type-tag">${slot.slotTypeDisplay}</div>
                    </div>
                    <span class="slot-status-badge ${statusClass}">
                        ${isAvail ? 'ว่าง' : 'จอดอยู่'}
                    </span>
                </div>
                
                ${!isAvail && slot.vehicle ? `
                    <div class="slot-car-preview">
                        <div class="slot-car-icon">${carIcon}</div>
                        <div>
                            <div class="slot-plate-text">${slot.vehicle.licensePlate}</div>
                            <div class="slot-duration-text">จอดแล้ว ${formatDurationMinutes(slot.vehicle.parkedDurationMinutes)}</div>
                        </div>
                    </div>
                ` : `
                    <div class="slot-car-preview" style="opacity: 0.35;">
                        <div class="slot-car-icon">🅿️</div>
                        <div class="slot-duration-text">พร้อมรองรับรถเข้าจอด</div>
                    </div>
                `}
            </div>
        `;
    }).join('');
}

function getVehicleIcon(type) {
    switch (type) {
        case 'MOTORCYCLE': return '🏍️';
        case 'ELECTRIC_VEHICLE': return '⚡';
        case 'TRUCK': return '🚚';
        default: return '🚗';
    }
}

function formatDurationMinutes(minutes) {
    if (!minutes || minutes < 0) return 'เพิ่งเข้าจอด';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0) return `${h} ชม. ${m} น.`;
    return `${m} นาที`;
}

// --- Check-In Form Handling ---

async function triggerAiAnprScan() {
    const button = document.getElementById('btnTriggerAnpr');
    const plateInput = document.getElementById('licensePlate');
    const resultBox = document.getElementById('anprResultBox');
    const targetText = document.getElementById('anprTargetText');

    button.disabled = true;
    targetText.innerText = 'กำลังอ่านทะเบียนจากกล้อง...';

    try {
        const response = await fetch(`${API_BASE}/ai/anpr-entry`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ licensePlate: plateInput.value.trim() })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || 'ANPR scan failed');

        plateInput.value = data.detectedPlate;
        selectVehicleTypeFromAnpr(data.detectedType);
        document.getElementById('anprConfidenceText').innerText = `Confidence: ${data.confidence}%`;
        document.getElementById('anprExplanationText').innerText = data.membershipMatched
            ? `ตรวจพบสมาชิก ${data.memberName} (ถึง ${data.membershipValidUntil}) ระบบยืนยันตัวตนและเปิดไม้กั้นอัตโนมัติ`
            : data.message || data.explanation;
        resultBox.style.display = 'block';
        targetText.innerText = data.membershipMatched ? 'สมาชิกยืนยันแล้ว — เปิดไม้กั้นอัตโนมัติ' : 'สแกนสำเร็จ — รอพนักงานยืนยัน';

        if (data.autoEntry && data.ticket) {
            updateEntryMemberStatus(data.ticket);
            animateGate('entry', () => {
                showTicketModal(data.ticket);
                loadParkingLotData();
                loadSystemStatus();
            });
        }
    } catch (error) {
        targetText.innerText = 'สแกนไม่สำเร็จ กรุณาตรวจสอบทะเบียนและลองใหม่';
        alert('ANPR: ' + error.message);
    } finally {
        button.disabled = false;
    }
}

let aiRecommendationTimer;

function debounceAiRecommend() {
    clearTimeout(aiRecommendationTimer);
    aiRecommendationTimer = setTimeout(requestAiRecommendation, 250);
}

async function requestAiRecommendation() {
    const plate = document.getElementById('licensePlate')?.value.trim();
    if (!plate || !appState.isServerOnline) return;

    const type = document.querySelector('input[name="vType"]:checked')?.value || 'CAR';
    const requiresCharging = document.getElementById('requiresCharging')?.checked || false;
    try {
        const response = await fetch(`${API_BASE}/ai/recommend`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ vehicleType: type, requiresCharging: requiresCharging.toString() })
        });
        if (!response.ok) return;
        const data = await response.json();
        document.getElementById('aiRecSlotBadge').innerText = `ช่องแนะนำ: ${data.slotNumber}`;
        document.getElementById('aiMatchScoreBadge').innerText = `${Number(data.matchScore || 0).toFixed(1)}% Match`;
        document.getElementById('aiRecReasonText').innerText = data.primaryReason || 'AI กำลังวิเคราะห์ช่องจอดที่เหมาะสม';
        document.getElementById('aiEnergyText').innerText = `ประหยัดพลังงาน: ${data.energyEfficiency || 'ลดการวนรถในอาคาร'}`;
        document.getElementById('aiCongestionText').innerText = `การจราจร: ${data.congestionImpact || 'ลดความหนาแน่นของทางเข้า'}`;
    } catch (error) {
        console.warn('AI recommendation unavailable:', error);
    }
}

function selectVehicleTypeFromAnpr(type) {
    const radio = document.querySelector(`input[name="vType"][value="${type}"]`);
    if (!radio) return;
    radio.checked = true;
    updateVehicleSelection();
}

function updateVehicleSelection() {
    const selectedType = document.querySelector('input[name="vType"]:checked').value;
    const cards = document.querySelectorAll('.type-card');
    cards.forEach(c => c.classList.remove('active'));
    document.querySelector(`input[name="vType"]:checked`).closest('.type-card').classList.add('active');

    const evGroup = document.getElementById('evOptionGroup');
    const badge = document.getElementById('rateStrategyBadge');
    const desc = document.getElementById('rateStrategyDesc');

    if (selectedType === 'ELECTRIC_VEHICLE') {
        evGroup.style.display = 'block';
        badge.innerText = 'EVPricingStrategy';
        desc.innerText = 'ฟรี 10 นาทีแรก | คิด 40 บาท/ชม. (รวมสถานีชาร์จไฟ EV)';
    } else if (selectedType === 'MOTORCYCLE') {
        evGroup.style.display = 'none';
        badge.innerText = 'MotorcyclePricingStrategy';
        desc.innerText = 'ฟรี 30 นาทีแรก | คิด 10 บาท/ชม.';
    } else if (selectedType === 'TRUCK') {
        evGroup.style.display = 'none';
        badge.innerText = 'TruckPricingStrategy';
        desc.innerText = 'ฟรี 15 นาทีแรก | คิด 50 บาท/ชม. (ช่องจอดขนาดใหญ่)';
    } else {
        evGroup.style.display = 'none';
        badge.innerText = 'StandardPricingStrategy';
        desc.innerText = 'ฟรี 15 นาทีแรก | ชม. แรก 20 บาท | ชม. ถัดไป 30 บาท/ชม.';
    }
}

function randomizePlate() {
    const prefixes = ['1กก', '3ขข', '7ศศ', '9ฮฮ', '5นม', '2รพ', '8กด'];
    const numbers = Math.floor(1000 + Math.random() * 9000);
    const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
    document.getElementById('licensePlate').value = `${prefix}-${numbers}`;
}

async function handleCheckIn(e) {
    e.preventDefault();
    const plate = document.getElementById('licensePlate').value.trim();
    const vType = document.querySelector('input[name="vType"]:checked').value;
    const requiresCharging = document.getElementById('requiresCharging').checked;

    const btn = document.getElementById('btnIssueTicket');
    btn.disabled = true;
    btn.innerHTML = 'กำลังออกตั๋วและจัดสรรช่องจอด...';

    try {
        let ticketData;
        if (appState.isServerOnline) {
            const res = await fetch(`${API_BASE}/park`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    licensePlate: plate,
                    vehicleType: vType,
                    requiresCharging: (vType === 'ELECTRIC_VEHICLE' && requiresCharging) ? "true" : "false"
                })
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Check-in failed');
            ticketData = data;
        } else {
            // Standalone mock
            ticketData = {
                ticketId: `TKT-MOCK-${Math.floor(1000 + Math.random() * 9000)}`,
                licensePlate: plate,
                vehicleType: vType,
                vehicleTypeDisplay: vType,
                floorNumber: 1,
                slotNumber: 'F1-05',
                entryTime: new Date().toLocaleString(),
                pricingStrategy: 'StandardPricingStrategy',
                rateDescription: 'ฟรี 15 นาทีแรก | 20 บาท ชม. แรก'
            };
        }

        // Animate Entry Gate
        updateEntryMemberStatus(ticketData);
        animateGate('entry', () => {
            showTicketModal(ticketData);
            loadParkingLotData();
            loadSystemStatus();
            document.getElementById('licensePlate').value = '';
        });

    } catch (err) {
        alert('เกิดข้อผิดพลาด: ' + err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
            กดรับบัตรจอดรถ & เปิดไม้กั้น (Issue Ticket)
        `;
    }
}

function showTicketModal(ticket) {
    document.getElementById('modalTicketId').innerText = ticket.ticketId;
    document.getElementById('modalPlate').innerText = ticket.licensePlate;
    document.getElementById('modalType').innerText = ticket.vehicleTypeDisplay || ticket.vehicleType;
    document.getElementById('modalSlot').innerText = `ชั้น ${ticket.floorNumber} [${ticket.slotNumber}]`;
    document.getElementById('modalEntryTime').innerText = ticket.entryTime;
    document.getElementById('modalRateName').innerText = ticket.pricingStrategy || 'Standard Rate';
    document.getElementById('modalRateDesc').innerText = ticket.rateDescription || '';
    document.getElementById('modalBarcodeText').innerText = ticket.ticketId;

    document.getElementById('ticketModalOverlay').classList.add('active');
}

function updateEntryMemberStatus(ticket) {
    const row = document.getElementById('modalMemberRow');
    const status = document.getElementById('modalMemberStatus');
    if (!row || !status) return;

    row.style.display = 'flex';
    if (ticket.memberVerified === true || ticket.member === true) {
        status.className = 'text-success';
        status.innerText = `ยืนยันสมาชิกแล้ว${ticket.memberName ? `: ${ticket.memberName}` : ''}${ticket.membershipValidUntil ? ` (ถึง ${ticket.membershipValidUntil})` : ''}`;
    } else {
        status.className = 'text-muted';
        status.innerText = 'ตรวจสอบแล้ว: ไม่มีสิทธิ์สมาชิกที่ใช้งานได้';
    }
}

// --- Exit & Cashier Handling ---

function refreshActivePlateChips() {
    const container = document.getElementById('quickActivePlates');
    if (!container) return;

    const activeCars = [];
    appState.floorsData.forEach(floor => {
        floor.slots.forEach(s => {
            if (s.vehicle) {
                activeCars.push({
                    plate: s.vehicle.licensePlate,
                    slot: s.slotNumber,
                    ticketId: s.vehicle.ticketId
                });
            }
        });
    });

    if (activeCars.length === 0) {
        container.innerHTML = '<span class="text-muted">ยังไม่มีรถจอดอยู่ในลาน</span>';
        return;
    }

    container.innerHTML = activeCars.map(c => `
        <div class="plate-chip" onclick="quickSelectPlate('${c.plate}')">
            🚗 ${c.plate} (${c.slot})
        </div>
    `).join('');
}

function quickSelectPlate(plate) {
    document.getElementById('exitSearchQuery').value = plate;
    searchTicketForExit();
}

async function searchTicketForExit() {
    const query = document.getElementById('exitSearchQuery').value.trim();
    if (!query) {
        alert('กรุณากรอกเลขตั๋วหรือเลขทะเบียนรถ');
        return;
    }

    try {
        let feeData;
        if (appState.isServerOnline) {
            const res = await fetch(`${API_BASE}/calculate-fee`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ticketIdOrPlate: query })
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'ไม่พบข้อมูลตั๋ว');
            feeData = data;
        } else {
            // Mock fallback
            feeData = {
                ticketId: query.startsWith('TKT') ? query : 'TKT-20260909-1002',
                licensePlate: query,
                vehicleType: 'CAR',
                vehicleTypeDisplay: 'รถยนต์ทั่วไป',
                slotNumber: 'F1-02',
                floorNumber: 1,
                entryTime: '2026-09-09 16:30:00',
                currentTime: '2026-09-09 18:45:00',
                durationMinutes: 135,
                durationHours: 3,
                durationDisplay: '2 ชม. 15 นาที',
                fee: 80.0,
                strategyName: 'StandardPricingStrategy',
                rateDescription: 'ฟรี 15 นาทีแรก | ชม. แรก 20 บาท | ชม. ถัดไป 30 บาท'
            };
        }

        appState.currentFeePreview = feeData;
        renderFeePreview(feeData);

    } catch (err) {
        alert('เกิดข้อผิดพลาด: ' + err.message);
    }
}

function renderFeePreview(fee) {
    document.getElementById('feeTicketId').innerText = fee.ticketId;
    document.getElementById('feePlate').innerText = fee.licensePlate;
    document.getElementById('feeTypeDisplay').innerText = fee.vehicleTypeDisplay || fee.vehicleType;
    document.getElementById('feeSlotNumber').innerText = `ชั้น ${fee.floorNumber} [${fee.slotNumber}]`;
    document.getElementById('feeEntryTime').innerText = fee.entryTime;
    document.getElementById('feeCurrentTime').innerText = fee.currentTime;
    document.getElementById('feeDuration').innerText = fee.durationDisplay;
    document.getElementById('feeStrategyName').innerText = fee.strategyName;
    document.getElementById('feeRateDesc').innerText = fee.rateDescription;
    document.getElementById('feeAmountHero').innerText = `฿${fee.fee.toFixed(2)}`;
    document.getElementById('qrAmountDisplay').innerText = `฿${fee.fee.toFixed(2)}`;

    const isLostTicket = fee.isLostTicket === true || fee.status === 'LOST';
    const breakdown = document.getElementById('lostTicketBreakdown');
    breakdown.style.display = isLostTicket ? 'grid' : 'none';
    if (isLostTicket) {
        document.getElementById('parkingFeeAmount').innerText = `฿${Number(fee.parkingFee || 0).toFixed(2)}`;
        document.getElementById('lostTicketPenaltyAmount').innerText = `฿${Number(fee.lostTicketPenalty || 300).toFixed(2)}`;
        document.getElementById('lostTicketTotalAmount').innerText = `฿${Number(fee.fee || 0).toFixed(2)}`;
    }

    // Set default cash tendered to exact or rounded
    document.getElementById('cashTenderedInput').value = Math.ceil(fee.fee / 10) * 10;
    calculateChangePreview();

    document.getElementById('feeResultCard').style.display = 'block';
}

function selectPayMethod(method) {
    appState.selectedPayMethod = method;
    document.querySelectorAll('.pay-method-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.pay-detail-box').forEach(b => b.classList.remove('active'));

    if (method === 'PROMPTPAY') {
        document.getElementById('btnMethodPromptPay').classList.add('active');
        document.getElementById('payDetailsPromptPay').classList.add('active');
    } else if (method === 'CREDIT_CARD') {
        document.getElementById('btnMethodCredit').classList.add('active');
        document.getElementById('payDetailsCredit').classList.add('active');
    } else if (method === 'CASH') {
        document.getElementById('btnMethodCash').classList.add('active');
        document.getElementById('payDetailsCash').classList.add('active');
    }
}

function calculateChangePreview() {
    const fee = appState.currentFeePreview?.fee ?? 0;
    const tendered = parseFloat(document.getElementById('cashTenderedInput').value) || 0;
    const change = Math.max(0, tendered - fee);
    document.getElementById('cashChangeDisplay').innerText = `฿${change.toFixed(2)}`;
}

function tenderExactAmount() {
    if (appState.currentFeePreview) {
        document.getElementById('cashTenderedInput').value = appState.currentFeePreview.fee;
        calculateChangePreview();
    }
}

function tenderRound(amount) {
    document.getElementById('cashTenderedInput').value = amount;
    calculateChangePreview();
}

async function submitPayment() {
    if (!appState.currentFeePreview) return;
    const fee = appState.currentFeePreview;
    const method = appState.selectedPayMethod;

    const payload = {
        ticketId: fee.ticketId,
        method: method,
        cashTendered: method === 'CASH' ? document.getElementById('cashTenderedInput').value : null,
        cardNumber: method === 'CREDIT_CARD' ? document.getElementById('ccNumber').value : null,
        cardHolder: method === 'CREDIT_CARD' ? document.getElementById('ccHolder').value : null
    };

    try {
        let receipt;
        if (appState.isServerOnline) {
            const res = await fetch(`${API_BASE}/pay`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Payment failed');
            receipt = data;
        } else {
            // Mock
            receipt = {
                paymentId: 'PAY-MOCK-999',
                transactionRef: 'DEMO-TX-12345',
                ticketId: fee.ticketId,
                licensePlate: fee.licensePlate,
                amount: fee.fee,
                method: method,
                methodLabel: method,
                paymentTime: new Date().toLocaleString(),
                cashTendered: method === 'CASH' ? payload.cashTendered : null,
                change: method === 'CASH' ? Math.max(0, payload.cashTendered - fee.fee) : 0
            };
        }

        showReceiptModal(receipt);

    } catch (err) {
        alert('การชำระเงินไม่สำเร็จ: ' + err.message);
    }
}

function showReceiptModal(receipt) {
    document.getElementById('rcpPaymentId').innerText = receipt.paymentId;
    document.getElementById('rcpRef').innerText = receipt.transactionRef || '-';
    document.getElementById('rcpTicketId').innerText = receipt.ticketId;
    document.getElementById('rcpPlate').innerText = receipt.licensePlate;
    document.getElementById('rcpMethod').innerText = receipt.methodLabel || receipt.method;
    document.getElementById('rcpTime').innerText = receipt.paymentTime;
    document.getElementById('rcpAmount').innerText = `฿${(receipt.amount ?? 0).toFixed(2)}`;

    if (receipt.cashTendered != null) {
        document.getElementById('rcpCashDetailsRow').style.display = 'flex';
        document.getElementById('rcpCashTendered').innerText = `฿${receipt.cashTendered.toFixed(2)}`;
        document.getElementById('rcpChange').innerText = `฿${(receipt.change ?? 0).toFixed(2)}`;
    } else {
        document.getElementById('rcpCashDetailsRow').style.display = 'none';
    }

    document.getElementById('receiptModalOverlay').classList.add('active');
}

async function finishPaymentAndOpenExitGate() {
    closeModal('receiptModalOverlay');
    const ticketId = appState.currentFeePreview?.ticketId;

    if (appState.isServerOnline && ticketId) {
        try {
            await fetch(`${API_BASE}/exit`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ticketId: ticketId })
            });
        } catch (e) {
            console.error('Exit notification failed:', e);
        }
    }

    animateGate('exit', () => {
        document.getElementById('feeResultCard').style.display = 'none';
        document.getElementById('exitSearchQuery').value = '';
        appState.currentFeePreview = null;
        loadParkingLotData();
        loadSystemStatus();
        loadTicketsAndPayments();
    });
}

async function handleLostTicket() {
    const query = document.getElementById('exitSearchQuery').value.trim();
    if (!query) {
        alert('กรุณากรอกป้ายทะเบียนรถที่ทำตั๋วสูญหาย');
        return;
    }
    if (!confirm(`ยืนยันการแจ้งตั๋วสูญหายสำหรับ ${query}?\nระบบจะคิดค่าจอดตามเวลาจริงรวมค่าปรับตั๋วหาย 300 บาท`)) {
        return;
    }

    try {
        if (appState.isServerOnline) {
            const res = await fetch(`${API_BASE}/lost-ticket`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ticketIdOrPlate: query })
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Lost ticket report failed');
            appState.currentFeePreview = data;
            renderFeePreview(data);
        } else {
            alert('แจ้งตั๋วสูญหายสำเร็จ ระบบรวมค่าจอดตามเวลาจริงและค่าปรับ 300 บาทแล้ว');
        }
    } catch (err) {
        alert('เกิดข้อผิดพลาด: ' + err.message);
    }
}

// --- Simulation Time Travel Controls ---

async function fastForward(minutes) {
    try {
        if (appState.isServerOnline) {
            await fetch(`${API_BASE}/time-travel`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ minutes: minutes.toString() })
            });
        }
        await loadSystemStatus();
        await loadParkingLotData();
        if (appState.currentFeePreview) {
            searchTicketForExit();
        }
    } catch (e) {
        console.error(e);
    }
}

async function resetSimTime() {
    try {
        if (appState.isServerOnline) {
            await fetch(`${API_BASE}/time-travel`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'reset' })
            });
        }
        await loadSystemStatus();
        await loadParkingLotData();
    } catch (e) {
        console.error(e);
    }
}

// --- Barrier Gate Animations ---

function animateGate(type, callback) {
    const prefix = type; // 'entry' or 'exit'
    const redLight = document.getElementById(`${prefix}LightRed`);
    const greenLight = document.getElementById(`${prefix}LightGreen`);
    const arm = document.getElementById(`${prefix}BarrierArm`);
    const car = document.getElementById(`${prefix}AnimatedCar`);
    const statusText = document.getElementById(`${prefix}GateStatusText`);

    // 1. Turn green, open arm
    redLight.classList.remove('active');
    greenLight.classList.add('active');
    arm.classList.add('open');
    statusText.innerText = '🟢 ไม้กั้นเปิด: รถกำลังผ่านเข้าสู่ลานจอด';

    // 2. Drive car through
    setTimeout(() => {
        car.classList.add('passed');
    }, 400);

    // 3. Close arm, reset car position
    setTimeout(() => {
        arm.classList.remove('open');
        greenLight.classList.remove('active');
        redLight.classList.add('active');
        statusText.innerText = '🔴 ไม้กั้นปิดเรียบร้อย';

        // แจ้ง backend ให้สถานะ Relay/ESP32 Simulator ปิดตรงกับภาพบนหน้าเว็บ
        if (appState.isServerOnline) {
            fetch(`${API_BASE}/hardware/gate`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    lane: type.toUpperCase(),
                    action: 'CLOSE',
                    reason: 'รถผ่านจุดตรวจแล้ว'
                })
            }).catch(() => {});
        }

        setTimeout(() => {
            car.classList.remove('passed');
            if (callback) callback();
        }, 600);
    }, 1800);
}

// --- Slot Details Modal ---

function showSlotDetails(slotNumber) {
    let targetSlot = null;
    let targetFloor = null;

    for (const f of appState.floorsData) {
        const found = f.slots.find(s => s.slotNumber === slotNumber);
        if (found) {
            targetSlot = found;
            targetFloor = f;
            break;
        }
    }

    if (!targetSlot) return;

    document.getElementById('slotModalNumber').innerText = `${targetSlot.slotNumber} (ชั้น ${targetSlot.floorNumber})`;
    const badge = document.getElementById('slotModalStatusBadge');
    badge.className = `status-badge ${targetSlot.isAvailable ? 'available' : 'occupied'}`;
    badge.innerText = targetSlot.statusLabel || (targetSlot.isAvailable ? 'ว่าง' : 'จอดอยู่');

    const body = document.getElementById('slotModalBody');
    if (targetSlot.isAvailable) {
        body.innerHTML = `
            <p>ช่องจอดนี้ว่างพร้อมให้บริการ</p>
            <p class="text-muted mt-3">ประเภทช่องจอด: <strong>${targetSlot.slotTypeDisplay}</strong></p>
            <div class="mt-4">
                <button class="btn-primary btn-block" onclick="quickParkToSlot('${targetSlot.slotNumber}')">
                    นำรถเข้าจอดในช่องนี้ทันที (Check-In)
                </button>
            </div>
        `;
    } else {
        const v = targetSlot.vehicle;
        body.innerHTML = `
            <div class="ticket-row"><span>ป้ายทะเบียน:</span> <strong>${v.licensePlate}</strong></div>
            <div class="ticket-row"><span>ประเภทยานพาหนะ:</span> <span>${v.vehicleTypeDisplay}</span></div>
            <div class="ticket-row"><span>เวลาเข้าจอด:</span> <span class="mono">${v.entryTime}</span></div>
            <div class="ticket-row"><span>ระยะเวลา:</span> <strong class="text-success">${formatDurationMinutes(v.parkedDurationMinutes)}</strong></div>
            <div class="ticket-row"><span>Pricing Strategy:</span> <span class="mono-badge">${v.rateStrategy}</span></div>
            <div class="ticket-row amount-total-row mt-3">
                <span>ค่าบริการปัจจุบัน:</span>
                <strong class="text-success">฿${(v.currentFee ?? 0).toFixed(2)}</strong>
            </div>
            <div class="mt-4">
                <button class="btn-primary btn-block" onclick="quickCheckoutSlot('${v.licensePlate}')">
                    นำรถคันนี้ไปชำระเงิน & ออกจากระบบ (Check-Out)
                </button>
            </div>
        `;
    }

    document.getElementById('slotDetailModalOverlay').classList.add('active');
}

function quickParkToSlot(slotNumber) {
    closeModal('slotDetailModalOverlay');
    switchTab('entry-gate');
    randomizePlate();
}

function quickCheckoutSlot(licensePlate) {
    closeModal('slotDetailModalOverlay');
    switchTab('exit-cashier');
    document.getElementById('exitSearchQuery').value = licensePlate;
    searchTicketForExit();
}

// --- Tickets History Table ---

function renderTicketsTable(tickets) {
    const tbody = document.getElementById('ticketsTableBody');
    if (!tbody) return;

    if (!tickets || tickets.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-muted" style="text-align: center;">ยังไม่มีประวัติตั๋วในระบบ</td></tr>';
        return;
    }

    tbody.innerHTML = tickets.map(t => {
        const statusClass = t.status.toLowerCase();
        return `
            <tr>
                <td class="mono text-highlight">${t.ticketId}</td>
                <td><strong>${t.licensePlate}</strong></td>
                <td>${t.vehicleTypeDisplay || t.vehicleType}</td>
                <td>ชั้น ${t.floorNumber} [${t.slotNumber}]</td>
                <td class="mono">${t.entryTime}</td>
                <td class="mono">${t.exitTime}</td>
                <td><span class="status-tag ${statusClass}">${t.status}</span></td>
                <td class="mono text-success">฿${(t.fee ?? 0).toFixed(2)}</td>
                <td>
                    ${t.status === 'ACTIVE' ? `
                        <button class="btn-secondary" style="padding: 2px 8px; font-size: 0.75rem;" onclick="quickCheckoutSlot('${t.licensePlate}')">ชำระเงิน</button>
                    ` : `
                        <span class="text-muted">-</span>
                    `}
                </td>
            </tr>
        `;
    }).join('');
}

// --- Modals Helper ---

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('active');
}

// Close modals when clicking outside
window.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.remove('active');
    }
});

// --- Demo Mock Fallbacks ---

function renderDemoStatus() {
    updateHeaderMetrics({
        simulatedTime: new Date().toLocaleTimeString(),
        totalCapacity: 22,
        totalAvailable: 19,
        totalOccupied: 3,
        totalRevenue: 240.0,
        displayBoardMessage: "ระบบทำงานใน Standalone Mode (รอเปิด Java Server ที่ port 8080)",
        displayBoardTime: new Date().toLocaleTimeString()
    });
}

function renderDemoLotData() {
    const demoFloors = [
        {
            floorNumber: 1,
            floorName: "ชั้น 1: VIP, EV Charging & รถเก๋ง",
            totalSlots: 8,
            availableSlots: 6,
            slots: [
                { slotNumber: "F1-01", floorNumber: 1, slotType: "EV_CHARGING", slotTypeDisplay: "ช่องจอดพร้อมสถานีชาร์จ EV", isAvailable: false, vehicle: { licensePlate: "1กก-9999", vehicleType: "ELECTRIC_VEHICLE", vehicleTypeDisplay: "รถยนต์ไฟฟ้า (EV)", entryTime: "2026-09-09 16:00:00", parkedDurationMinutes: 120, rateStrategy: "EVPricingStrategy", currentFee: 80 } },
                { slotNumber: "F1-02", floorNumber: 1, slotType: "EV_CHARGING", slotTypeDisplay: "ช่องจอดพร้อมสถานีชาร์จ EV", isAvailable: true },
                { slotNumber: "F1-03", floorNumber: 1, slotType: "EV_CHARGING", slotTypeDisplay: "ช่องจอดพร้อมสถานีชาร์จ EV", isAvailable: true },
                { slotNumber: "F1-04", floorNumber: 1, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV ทั่วไป", isAvailable: false, vehicle: { licensePlate: "4ขข-1234", vehicleType: "CAR", vehicleTypeDisplay: "รถยนต์ทั่วไป", entryTime: "2026-09-09 17:15:00", parkedDurationMinutes: 45, rateStrategy: "StandardPricingStrategy", currentFee: 20 } },
                { slotNumber: "F1-05", floorNumber: 1, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV ทั่วไป", isAvailable: true },
                { slotNumber: "F1-06", floorNumber: 1, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV ทั่วไป", isAvailable: true },
                { slotNumber: "F1-07", floorNumber: 1, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV ทั่วไป", isAvailable: true },
                { slotNumber: "F1-08", floorNumber: 1, slotType: "COMPACT", slotTypeDisplay: "ช่องจอดรถขนาดกะทัดรัด", isAvailable: true }
            ]
        },
        {
            floorNumber: 2,
            floorName: "ชั้น 2: รถเก๋งทั่วไป & รถขนาดกะทัดรัด",
            totalSlots: 8,
            availableSlots: 8,
            slots: [
                { slotNumber: "F2-01", floorNumber: 2, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV", isAvailable: true },
                { slotNumber: "F2-02", floorNumber: 2, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV", isAvailable: true },
                { slotNumber: "F2-03", floorNumber: 2, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV", isAvailable: true },
                { slotNumber: "F2-04", floorNumber: 2, slotType: "STANDARD", slotTypeDisplay: "ช่องจอดรถเก๋ง/SUV", isAvailable: true },
                { slotNumber: "F2-05", floorNumber: 2, slotType: "COMPACT", slotTypeDisplay: "ช่องจอดขนาดกะทัดรัด", isAvailable: true },
                { slotNumber: "F2-06", floorNumber: 2, slotType: "COMPACT", slotTypeDisplay: "ช่องจอดขนาดกะทัดรัด", isAvailable: true },
                { slotNumber: "F2-07", floorNumber: 2, slotType: "COMPACT", slotTypeDisplay: "ช่องจอดขนาดกะทัดรัด", isAvailable: true },
                { slotNumber: "F2-08", floorNumber: 2, slotType: "COMPACT", slotTypeDisplay: "ช่องจอดขนาดกะทัดรัด", isAvailable: true }
            ]
        },
        {
            floorNumber: 3,
            floorName: "ชั้น 3: รถจักรยานยนต์ & รถขนาดใหญ่",
            totalSlots: 6,
            availableSlots: 5,
            slots: [
                { slotNumber: "F3-01", floorNumber: 3, slotType: "MOTORCYCLE", slotTypeDisplay: "ช่องจอดรถจักรยานยนต์", isAvailable: false, vehicle: { licensePlate: "9กข-777", vehicleType: "MOTORCYCLE", vehicleTypeDisplay: "มอเตอร์ไซค์", entryTime: "2026-09-09 17:30:00", parkedDurationMinutes: 30, rateStrategy: "MotorcyclePricingStrategy", currentFee: 0 } },
                { slotNumber: "F3-02", floorNumber: 3, slotType: "MOTORCYCLE", slotTypeDisplay: "ช่องจอดรถจักรยานยนต์", isAvailable: true },
                { slotNumber: "F3-03", floorNumber: 3, slotType: "MOTORCYCLE", slotTypeDisplay: "ช่องจอดรถจักรยานยนต์", isAvailable: true },
                { slotNumber: "F3-04", floorNumber: 3, slotType: "MOTORCYCLE", slotTypeDisplay: "ช่องจอดรถจักรยานยนต์", isAvailable: true },
                { slotNumber: "F3-05", floorNumber: 3, slotType: "LARGE", slotTypeDisplay: "ช่องจอดขนาดใหญ่", isAvailable: true },
                { slotNumber: "F3-06", floorNumber: 3, slotType: "LARGE", slotTypeDisplay: "ช่องจอดขนาดใหญ่", isAvailable: true }
            ]
        }
    ];

    appState.floorsData = demoFloors;
    renderFloorTabs(demoFloors);
    renderCurrentFloorSlots();
    refreshActivePlateChips();
}

function renderDemoTicketsTable() {
    const demoTickets = [
        { ticketId: "TKT-20260909-1001", licensePlate: "1กก-9999", vehicleType: "ELECTRIC_VEHICLE", vehicleTypeDisplay: "รถยนต์ไฟฟ้า (EV)", floorNumber: 1, slotNumber: "F1-01", entryTime: "2026-09-09 16:00:00", exitTime: "-", status: "ACTIVE", fee: 80.0 },
        { ticketId: "TKT-20260909-1002", licensePlate: "4ขข-1234", vehicleType: "CAR", vehicleTypeDisplay: "รถยนต์ทั่วไป", floorNumber: 1, slotNumber: "F1-04", entryTime: "2026-09-09 17:15:00", exitTime: "-", status: "ACTIVE", fee: 20.0 },
        { ticketId: "TKT-20260909-1003", licensePlate: "9กข-777", vehicleType: "MOTORCYCLE", vehicleTypeDisplay: "มอเตอร์ไซค์", floorNumber: 3, slotNumber: "F3-01", entryTime: "2026-09-09 17:30:00", exitTime: "-", status: "ACTIVE", fee: 0.0 }
    ];
    renderTicketsTable(demoTickets);
}
