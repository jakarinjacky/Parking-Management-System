/*
 * GitHub Pages Demo Runtime
 * ทำให้หน้าเว็บเดโมทำงานได้ครบโดยไม่ต้องพึ่ง Java backend
 * ข้อมูลถูกเก็บใน localStorage ของ browser สำหรับการพรีเซนต์
 */
(function () {
    if (!window.location.hostname.endsWith('github.io')) return;

    const STORAGE_KEY = 'smartParkingDemoStateV4';
    const PREFERRED_SLOT_KEY = 'smartParkingPreferredSlot';
    const LOST_PENALTY = 300;

    const typeMeta = {
        CAR: {
            label: 'รถยนต์ทั่วไป',
            strategy: 'StandardPricingStrategy',
            rate: 'ฟรี 15 นาทีแรก | ชม. แรก 20 บาท | ชม. ถัดไป 30 บาท/ชม.'
        },
        ELECTRIC_VEHICLE: {
            label: 'รถยนต์ไฟฟ้า (EV)',
            strategy: 'EVPricingStrategy',
            rate: 'ฟรี 10 นาทีแรก | คิด 40 บาท/ชม. (รวมค่าจุดชาร์จไฟ EV)'
        },
        MOTORCYCLE: {
            label: 'มอเตอร์ไซค์',
            strategy: 'MotorcyclePricingStrategy',
            rate: 'ฟรี 30 นาทีแรก | คิด 10 บาท/ชม.'
        },
        TRUCK: {
            label: 'รถบรรทุก / รถตู้ใหญ่',
            strategy: 'TruckPricingStrategy',
            rate: 'ฟรี 15 นาทีแรก | คิด 50 บาท/ชม. (ช่องจอดพิเศษขนาดใหญ่)'
        }
    };

    function iso(ms) {
        return new Date(ms).toISOString();
    }

    function fmt(ms) {
        return new Date(ms).toLocaleString('th-TH', { hour12: false });
    }

    function demoHistoricalTickets(now) {
        const plates = [
            '1กก-1023','2ขข-4587','3คค-7712','4งง-2098','5จจ-6631',
            '6ฉฉ-8145','7ชช-3902','8ซซ-5476','9ญญ-1258','1ฎฎ-9364',
            '2ฏฏ-4071','3ฐฐ-6829','4ฑฑ-1537','5ณณ-7480','6ดด-2916',
            '7ตต-8653','8ถถ-3149','9ทท-5706','1นน-4285','2บบ-7931',
            '3ปป-2468','4ผผ-9017','5พพ-6354','6ฟฟ-1729','7มม-5842',
            '8ยย-3206','9รร-7561','1ลล-4893','2วว-2175','3สส-8430'
        ];
        const types = ['CAR', 'ELECTRIC_VEHICLE', 'MOTORCYCLE', 'TRUCK'];
        return plates.map((licensePlate, index) => {
            const daysAgo = 88 - index * 3;
            const entry = new Date(now - daysAgo * 86400000);
            entry.setHours(7 + index % 10, (index * 7) % 60, 0, 0);
            const durationMinutes = 35 + (index % 6) * 25;
            const exit = new Date(entry.getTime() + durationMinutes * 60000);
            const vehicleType = types[index % types.length];
            const hours = Math.max(1, Math.ceil(durationMinutes / 60));
            const fee = vehicleType === 'MOTORCYCLE' ? hours * 10
                : vehicleType === 'ELECTRIC_VEHICLE' ? hours * 40
                : vehicleType === 'TRUCK' ? hours * 50
                : hours === 1 ? 20 : 20 + (hours - 1) * 30;
            const floorNumber = ['MOTORCYCLE', 'TRUCK'].includes(vehicleType) ? 3
                : vehicleType === 'ELECTRIC_VEHICLE' ? 1 : 2;
            const slotNumber = vehicleType === 'ELECTRIC_VEHICLE' ? `F1-0${1 + index % 3}`
                : vehicleType === 'MOTORCYCLE' ? `F3-0${1 + index % 4}`
                : vehicleType === 'TRUCK' ? `F3-0${5 + index % 2}`
                : `F2-0${1 + index % 8}`;
            return {
                ticketId: `HIS-DEMO-${String(index + 1).padStart(3, '0')}`,
                licensePlate, vehicleType, floorNumber, slotNumber,
                entryAt: iso(entry.getTime()), exitAt: iso(exit.getTime()), status: 'EXITED', fee
            };
        });
    }

    function initialState() {
        const now = Date.now();
        return {
            version: 4,
            offsetMinutes: 0,
            sequence: 1004,
            paymentSequence: 1,
            tickets: [
                ...demoHistoricalTickets(now),
                {
                    ticketId: 'TKT-DEMO-1001', licensePlate: '1กก-9999', vehicleType: 'ELECTRIC_VEHICLE',
                    floorNumber: 1, slotNumber: 'F1-01', entryAt: iso(now - 120 * 60000), status: 'ACTIVE', exitAt: null, fee: 80
                },
                {
                    ticketId: 'TKT-DEMO-1002', licensePlate: '4ขข-1234', vehicleType: 'CAR',
                    floorNumber: 1, slotNumber: 'F1-04', entryAt: iso(now - 45 * 60000), status: 'ACTIVE', exitAt: null, fee: 20
                },
                {
                    ticketId: 'TKT-DEMO-1003', licensePlate: '9กข-777', vehicleType: 'MOTORCYCLE',
                    floorNumber: 3, slotNumber: 'F3-01', entryAt: iso(now - 30 * 60000), status: 'ACTIVE', exitAt: null, fee: 0
                }
            ],
            payments: [],
            reservations: [],
            memberships: []
        };
    }

    function loadState() {
        try {
            const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY));
            if (parsed && parsed.version === 4 && Array.isArray(parsed.tickets)) {
                pruneHistory(parsed);
                saveState(parsed);
                return parsed;
            }
        } catch (_) {}
        const fresh = initialState();
        saveState(fresh);
        return fresh;
    }

    function pruneHistory(state) {
        const cutoff = new Date(nowMs(state));
        cutoff.setMonth(cutoff.getMonth() - 3);
        state.tickets = state.tickets.filter(ticket => {
            if (ticket.status !== 'EXITED') return true;
            return new Date(ticket.exitAt || ticket.entryAt).getTime() >= cutoff.getTime();
        });
    }

    function saveState(state) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    }

    function nowMs(state) {
        return Date.now() + Number(state.offsetMinutes || 0) * 60000;
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function durationMinutes(ticket, state) {
        const start = new Date(ticket.entryAt).getTime();
        const end = ticket.exitAt ? new Date(ticket.exitAt).getTime() : nowMs(state);
        return Math.max(0, Math.floor((end - start) / 60000));
    }

    function baseParkingFee(ticket, state) {
        const minutes = durationMinutes(ticket, state);
        if (ticket.vehicleType === 'MOTORCYCLE') {
            if (minutes <= 30) return 0;
            return Math.ceil(minutes / 60) * 10;
        }
        if (ticket.vehicleType === 'ELECTRIC_VEHICLE') {
            if (minutes <= 10) return 0;
            return Math.ceil(minutes / 60) * 40;
        }
        if (ticket.vehicleType === 'TRUCK') {
            if (minutes <= 15) return 0;
            return Math.ceil(minutes / 60) * 50;
        }
        if (minutes <= 15) return 0;
        const hours = Math.ceil(minutes / 60);
        return hours <= 1 ? 20 : 20 + (hours - 1) * 30;
    }

    function totalFee(ticket, state) {
        const base = baseParkingFee(ticket, state);
        return base + (ticket.status === 'LOST' ? LOST_PENALTY : 0);
    }

    function durationText(minutes) {
        const h = Math.floor(minutes / 60);
        const m = minutes % 60;
        return h > 0 ? `${h} ชม. ${m} นาที` : `${m} นาที`;
    }

    function floorsTemplate() {
        return [
            {
                floorNumber: 1,
                floorName: 'ชั้น 1: VIP, EV Charging & รถเก๋ง',
                slots: [
                    ['F1-01', 'EV_CHARGING', 'ช่องจอดพร้อมสถานีชาร์จ EV'],
                    ['F1-02', 'EV_CHARGING', 'ช่องจอดพร้อมสถานีชาร์จ EV'],
                    ['F1-03', 'EV_CHARGING', 'ช่องจอดพร้อมสถานีชาร์จ EV'],
                    ['F1-04', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV ทั่วไป'],
                    ['F1-05', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV ทั่วไป'],
                    ['F1-06', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV ทั่วไป'],
                    ['F1-07', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV ทั่วไป'],
                    ['F1-08', 'COMPACT', 'ช่องจอดรถขนาดกะทัดรัด']
                ]
            },
            {
                floorNumber: 2,
                floorName: 'ชั้น 2: รถเก๋งทั่วไป & รถขนาดกะทัดรัด',
                slots: [
                    ['F2-01', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV'],
                    ['F2-02', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV'],
                    ['F2-03', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV'],
                    ['F2-04', 'STANDARD', 'ช่องจอดรถเก๋ง/SUV'],
                    ['F2-05', 'COMPACT', 'ช่องจอดขนาดกะทัดรัด'],
                    ['F2-06', 'COMPACT', 'ช่องจอดขนาดกะทัดรัด'],
                    ['F2-07', 'COMPACT', 'ช่องจอดขนาดกะทัดรัด'],
                    ['F2-08', 'COMPACT', 'ช่องจอดขนาดกะทัดรัด']
                ]
            },
            {
                floorNumber: 3,
                floorName: 'ชั้น 3: รถจักรยานยนต์ & รถขนาดใหญ่',
                slots: [
                    ['F3-01', 'MOTORCYCLE', 'ช่องจอดรถจักรยานยนต์'],
                    ['F3-02', 'MOTORCYCLE', 'ช่องจอดรถจักรยานยนต์'],
                    ['F3-03', 'MOTORCYCLE', 'ช่องจอดรถจักรยานยนต์'],
                    ['F3-04', 'MOTORCYCLE', 'ช่องจอดรถจักรยานยนต์'],
                    ['F3-05', 'LARGE', 'ช่องจอดขนาดใหญ่'],
                    ['F3-06', 'LARGE', 'ช่องจอดขนาดใหญ่']
                ]
            }
        ];
    }

    function activeForSlot(ticket) {
        return ['ACTIVE', 'LOST', 'PAID'].includes(ticket.status);
    }

    function buildFloors(state) {
        const template = floorsTemplate();
        const active = state.tickets.filter(activeForSlot);
        const floors = template.map(f => {
            const slots = f.slots.map(([slotNumber, slotType, slotTypeDisplay]) => {
                const ticket = active.find(t => t.slotNumber === slotNumber);
                const mins = ticket ? durationMinutes(ticket, state) : 0;
                return {
                    slotNumber,
                    floorNumber: f.floorNumber,
                    slotType,
                    slotTypeDisplay,
                    statusLabel: ticket ? (ticket.status === 'PAID' ? 'ชำระแล้ว รอออก' : ticket.status === 'LOST' ? 'ตั๋วหาย' : 'จอดอยู่') : 'ว่าง',
                    isAvailable: !ticket,
                    vehicle: ticket ? {
                        licensePlate: ticket.licensePlate,
                        vehicleType: ticket.vehicleType,
                        vehicleTypeDisplay: typeMeta[ticket.vehicleType]?.label || ticket.vehicleType,
                        ticketId: ticket.ticketId,
                        entryTime: fmt(new Date(ticket.entryAt).getTime()),
                        parkedDurationMinutes: mins,
                        rateStrategy: typeMeta[ticket.vehicleType]?.strategy || 'StandardPricingStrategy',
                        currentFee: totalFee(ticket, state)
                    } : null
                };
            });
            return {
                floorNumber: f.floorNumber,
                floorName: f.floorName,
                totalSlots: slots.length,
                availableSlots: slots.filter(s => s.isAvailable).length,
                slots
            };
        });
        return floors;
    }

    function compatible(slotType, vehicleType, requiresCharging) {
        if (vehicleType === 'MOTORCYCLE') return slotType === 'MOTORCYCLE';
        if (vehicleType === 'TRUCK') return slotType === 'LARGE';
        if (vehicleType === 'ELECTRIC_VEHICLE' && requiresCharging) return slotType === 'EV_CHARGING';
        if (vehicleType === 'ELECTRIC_VEHICLE') return ['EV_CHARGING', 'STANDARD', 'COMPACT'].includes(slotType);
        if (vehicleType === 'CAR') return ['STANDARD', 'COMPACT'].includes(slotType);
        return false;
    }

    function findSlot(state, vehicleType, requiresCharging) {
        const floors = buildFloors(state);
        const preferred = sessionStorage.getItem(PREFERRED_SLOT_KEY);
        if (preferred) {
            for (const floor of floors) {
                const s = floor.slots.find(x => x.slotNumber === preferred && x.isAvailable && compatible(x.slotType, vehicleType, requiresCharging));
                if (s) {
                    sessionStorage.removeItem(PREFERRED_SLOT_KEY);
                    return s;
                }
            }
            sessionStorage.removeItem(PREFERRED_SLOT_KEY);
        }
        for (const floor of floors) {
            const slot = floor.slots.find(s => s.isAvailable && compatible(s.slotType, vehicleType, requiresCharging));
            if (slot) return slot;
        }
        return null;
    }

    function findTicket(query, state) {
        const q = String(query || '').trim().toLowerCase();
        const candidates = state.tickets.filter(t => ['ACTIVE', 'LOST', 'PAID'].includes(t.status));
        return candidates.slice().reverse().find(t =>
            t.ticketId.toLowerCase() === q || t.licensePlate.toLowerCase() === q
        ) || null;
    }

    function feeData(ticket, state) {
        const mins = durationMinutes(ticket, state);
        const parkingFee = baseParkingFee(ticket, state);
        const total = totalFee(ticket, state);
        return {
            ticketId: ticket.ticketId,
            licensePlate: ticket.licensePlate,
            vehicleType: ticket.vehicleType,
            vehicleTypeDisplay: typeMeta[ticket.vehicleType]?.label || ticket.vehicleType,
            slotNumber: ticket.slotNumber,
            floorNumber: ticket.floorNumber,
            entryTime: fmt(new Date(ticket.entryAt).getTime()),
            currentTime: fmt(nowMs(state)),
            durationMinutes: mins,
            durationHours: Math.ceil(mins / 60),
            durationDisplay: durationText(mins),
            fee: Number(total),
            parkingFee: Number(parkingFee),
            lostTicketPenalty: ticket.status === 'LOST' ? LOST_PENALTY : 0,
            isLostTicket: ticket.status === 'LOST',
            status: ticket.status,
            strategyName: typeMeta[ticket.vehicleType]?.strategy || 'StandardPricingStrategy',
            rateDescription: typeMeta[ticket.vehicleType]?.rate || ''
        };
    }

    function refreshAll() {
        window.loadSystemStatus();
        window.loadParkingLotData();
        window.loadTicketsAndPayments();
        if (document.getElementById('tab-dashboard')?.classList.contains('active')) window.loadDailyDashboard();
    }

    window.initializeSession = async function () {
        const loggedIn = localStorage.getItem('isLoggedIn') === 'true';
        const username = localStorage.getItem('username');
        if (!loggedIn || !username) {
            window.location.href = 'login.html';
            return false;
        }
        const demoUser = {
            username,
            displayName: username === 'admin' ? 'Administrator' : username,
            role: username === 'admin' ? 'admin' : 'staff'
        };
        appState.currentUser = demoUser;
        appState.isServerOnline = false;
        if (typeof updateCurrentUserBadge === 'function') updateCurrentUserBadge(demoUser);
        return true;
    };

    window.logout = async function () {
        localStorage.removeItem('isLoggedIn');
        localStorage.removeItem('username');
        window.location.href = 'login.html';
    };

    window.loadSystemStatus = async function () {
        const state = loadState();
        const floors = buildFloors(state);
        const occupied = floors.reduce((n, f) => n + (f.totalSlots - f.availableSlots), 0);
        const total = floors.reduce((n, f) => n + f.totalSlots, 0);
        const revenue = state.payments.reduce((sum, p) => sum + Number(p.amount || 0), 0);
        appState.isServerOnline = false;
        appState.statusData = {
            simulatedTime: fmt(nowMs(state)),
            totalCapacity: total,
            totalAvailable: total - occupied,
            totalOccupied: occupied,
            totalRevenue: revenue,
            displayBoardMessage: `DEMO พร้อมพรีเซนต์ • ว่าง ${total - occupied}/${total} ช่อง • ข้อมูลบันทึกใน Browser`,
            displayBoardTime: fmt(nowMs(state))
        };
        const data = appState.statusData;
        const sim = document.getElementById('simulatedTimeText');
        const avail = document.getElementById('headerAvailCount');
        const occ = document.getElementById('headerOccCount');
        const rev = document.getElementById('headerRevenueText');
        if (sim) sim.innerText = data.simulatedTime;
        if (avail) avail.innerText = data.totalAvailable;
        if (occ) occ.innerText = data.totalOccupied;
        if (rev) rev.innerText = `฿${Number(data.totalRevenue).toFixed(2)}`;
        if (typeof updateDisplayBoard === 'function') updateDisplayBoard(data.displayBoardMessage, data.displayBoardTime);
    };

    window.loadParkingLotData = async function () {
        const state = loadState();
        const floors = buildFloors(state);
        appState.floorsData = floors;
        if (!floors.some(f => f.floorNumber === appState.selectedFloor)) appState.selectedFloor = 1;
        renderFloorTabs(floors);
        renderCurrentFloorSlots();
        refreshActivePlateChips();
        if (window.__demoHeatmapEnabled) applyHeatmap();
    };

    window.renderTicketsTable = function (tickets) {
        const tbody = document.getElementById('ticketsTableBody');
        if (!tbody) return;
        if (!tickets?.length) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-muted" style="text-align:center;">ยังไม่มีประวัติตั๋วในระบบ</td></tr>';
            return;
        }
        tbody.innerHTML = tickets.map(t => {
            const status = String(t.status || 'ACTIVE');
            const action = ['ACTIVE', 'LOST'].includes(status)
                ? `<button class="btn-secondary" style="padding:2px 8px;font-size:.75rem;" onclick="quickCheckoutSlot('${escapeHtml(t.licensePlate)}')">ชำระเงิน</button>`
                : '<span class="text-muted">-</span>';
            return `<tr>
                <td class="mono text-highlight">${escapeHtml(t.ticketId)}</td>
                <td><strong>${escapeHtml(t.licensePlate)}</strong></td>
                <td>${escapeHtml(t.vehicleTypeDisplay || t.vehicleType)}</td>
                <td>ชั้น ${Number(t.floorNumber || 0)} [${escapeHtml(t.slotNumber)}]</td>
                <td class="mono">${escapeHtml(t.entryTime || '-')}</td>
                <td class="mono">${escapeHtml(t.exitTime || '-')}</td>
                <td><span class="status-tag ${escapeHtml(status.toLowerCase())}">${escapeHtml(status)}</span></td>
                <td class="mono text-success">฿${Number(t.fee || 0).toFixed(2)}</td>
                <td>${action}</td>
            </tr>`;
        }).join('');
    };

    window.loadTicketsAndPayments = async function () {
        const state = loadState();
        const tickets = state.tickets.slice().reverse().map(t => ({
            ...t,
            vehicleTypeDisplay: typeMeta[t.vehicleType]?.label || t.vehicleType,
            entryTime: fmt(new Date(t.entryAt).getTime()),
            exitTime: t.exitAt ? fmt(new Date(t.exitAt).getTime()) : '-',
            fee: t.status === 'EXITED' ? Number(t.fee || 0) : totalFee(t, state)
        }));
        appState.activeTickets = tickets;
        renderTicketsTable(tickets);
    };

    window.loadParkingHistory = async function () {
        const state = loadState();
        const fromInput = document.getElementById('historyFrom');
        const toInput = document.getElementById('historyTo');
        const plateInput = document.getElementById('historyPlate');
        const now = new Date(nowMs(state));
        if (toInput && !toInput.value) toInput.value = iso(now.getTime()).slice(0, 10);
        if (fromInput && !fromInput.value) {
            const from = new Date(now);
            from.setMonth(from.getMonth() - 3);
            fromInput.value = iso(from.getTime()).slice(0, 10);
        }
        const from = fromInput?.value ? new Date(`${fromInput.value}T00:00:00`).getTime() : 0;
        const to = toInput?.value ? new Date(`${toInput.value}T23:59:59`).getTime() : Number.MAX_SAFE_INTEGER;
        const plate = plateInput?.value.trim().toLowerCase() || '';
        const filtered = state.tickets
            .filter(t => {
                const entered = new Date(t.entryAt).getTime();
                return entered >= from && entered <= to && (!plate || t.licensePlate.toLowerCase().includes(plate));
            })
            .sort((a, b) => new Date(b.entryAt) - new Date(a.entryAt));
        const records = filtered.map(t => ({
            ticketId: t.ticketId,
            licensePlate: t.licensePlate,
            vehicleType: t.vehicleType,
            vehicleTypeDisplay: typeMeta[t.vehicleType]?.label || t.vehicleType,
            floorNumber: t.floorNumber,
            slotNumber: t.slotNumber,
            entryTime: fmt(new Date(t.entryAt).getTime()),
            exitTime: t.exitAt ? fmt(new Date(t.exitAt).getTime()) : null,
            status: t.status === 'EXITED' ? 'EXITED' : 'IN_PARKING',
            fee: Number(t.fee || 0)
        }));
        const exited = filtered.filter(t => t.status === 'EXITED');
        document.getElementById('historyEnteredCount').innerText = filtered.length;
        document.getElementById('historyExitedCount').innerText = exited.length;
        document.getElementById('historyParkedCount').innerText = filtered.length - exited.length;
        document.getElementById('historyRevenue').innerText = `฿${exited.reduce((sum, t) => sum + Number(t.fee || 0), 0).toFixed(2)}`;
        document.getElementById('historyRetentionMessage').innerText =
            `แสดง ${fromInput?.value || '-'} ถึง ${toInput?.value || '-'} | Demo เก็บข้อมูลใน Browser ย้อนหลัง 3 เดือน`;
        window.renderParkingHistory(records);
    };

    window.handleCheckIn = async function (event) {
        event?.preventDefault?.();
        const plate = document.getElementById('licensePlate')?.value.trim();
        const vehicleType = document.querySelector('input[name="vType"]:checked')?.value || 'CAR';
        const requiresCharging = !!document.getElementById('requiresCharging')?.checked;
        if (!plate) {
            alert('กรุณากรอกเลขทะเบียนรถ');
            return;
        }
        const state = loadState();
        if (state.tickets.some(t => activeForSlot(t) && t.licensePlate.toLowerCase() === plate.toLowerCase())) {
            alert('รถทะเบียนนี้อยู่ในลานจอดแล้ว');
            return;
        }
        const slot = findSlot(state, vehicleType, requiresCharging);
        if (!slot) {
            alert('ไม่มีช่องจอดที่เหมาะสมสำหรับรถประเภทนี้');
            return;
        }
        const ticket = {
            ticketId: `TKT-DEMO-${String(state.sequence++).padStart(4, '0')}`,
            licensePlate: plate,
            vehicleType,
            floorNumber: slot.floorNumber,
            slotNumber: slot.slotNumber,
            entryAt: iso(nowMs(state)),
            status: 'ACTIVE',
            exitAt: null,
            fee: 0
        };
        state.tickets.push(ticket);
        saveState(state);
        const data = {
            ...ticket,
            vehicleTypeDisplay: typeMeta[vehicleType].label,
            entryTime: fmt(nowMs(state)),
            pricingStrategy: typeMeta[vehicleType].strategy,
            rateDescription: typeMeta[vehicleType].rate,
            memberVerified: state.memberships.some(m => m.licensePlate.toLowerCase() === plate.toLowerCase())
        };
        const btn = document.getElementById('btnIssueTicket');
        if (btn) btn.disabled = true;
        updateEntryMemberStatus(data);
        animateGate('entry', () => {
            showTicketModal(data);
            if (btn) btn.disabled = false;
            const input = document.getElementById('licensePlate');
            if (input) input.value = '';
            refreshAll();
        });
    };

    window.quickParkToSlot = function (slotNumber) {
        closeModal('slotDetailModalOverlay');
        sessionStorage.setItem(PREFERRED_SLOT_KEY, slotNumber);
        switchTab('entry-gate');
        randomizePlate();
        const rec = document.getElementById('aiRecSlotBadge');
        if (rec) rec.innerText = `ช่องที่เลือก: ${slotNumber}`;
    };

    window.searchTicketForExit = async function () {
        const query = document.getElementById('exitSearchQuery')?.value.trim();
        if (!query) {
            alert('กรุณากรอกเลขตั๋วหรือเลขทะเบียนรถ');
            return;
        }
        const state = loadState();
        const ticket = findTicket(query, state);
        if (!ticket) {
            alert('ไม่พบรถหรือตั๋วที่กำลังอยู่ในลาน');
            return;
        }
        const data = feeData(ticket, state);
        appState.currentFeePreview = data;
        window.renderFeePreview(data);
    };

    window.renderFeePreview = function (fee) {
        const set = (id, value) => { const el = document.getElementById(id); if (el) el.innerText = value; };
        set('feeTicketId', fee.ticketId);
        set('feePlate', fee.licensePlate);
        set('feeTypeDisplay', fee.vehicleTypeDisplay || fee.vehicleType);
        set('feeSlotNumber', `ชั้น ${fee.floorNumber} [${fee.slotNumber}]`);
        set('feeEntryTime', fee.entryTime);
        set('feeCurrentTime', fee.currentTime);
        set('feeDuration', fee.durationDisplay);
        set('feeStrategyName', fee.strategyName);
        set('feeRateDesc', fee.rateDescription);
        set('feeAmountHero', `฿${Number(fee.fee || 0).toFixed(2)}`);
        set('qrAmountDisplay', `฿${Number(fee.fee || 0).toFixed(2)}`);
        const lost = fee.isLostTicket === true || fee.status === 'LOST';
        const breakdown = document.getElementById('lostTicketBreakdown');
        if (breakdown) breakdown.style.display = lost ? 'grid' : 'none';
        if (lost) {
            set('parkingFeeAmount', `฿${Number(fee.parkingFee || 0).toFixed(2)}`);
            set('lostTicketPenaltyAmount', `฿${Number(fee.lostTicketPenalty || LOST_PENALTY).toFixed(2)}`);
            set('lostTicketTotalAmount', `฿${Number(fee.fee || 0).toFixed(2)}`);
        }
        const cash = document.getElementById('cashTenderedInput');
        if (cash) cash.value = Math.ceil(Number(fee.fee || 0) / 10) * 10;
        calculateChangePreview();
        const card = document.getElementById('feeResultCard');
        if (card) card.style.display = 'block';
    };

    window.handleLostTicket = async function () {
        const query = document.getElementById('exitSearchQuery')?.value.trim();
        if (!query) {
            alert('กรุณากรอกป้ายทะเบียนรถหรือเลขตั๋วที่สูญหาย');
            return;
        }
        const state = loadState();
        const ticket = findTicket(query, state);
        if (!ticket) {
            alert('ไม่พบรถหรือตั๋วที่กำลังอยู่ในลาน');
            return;
        }
        if (ticket.status === 'PAID') {
            alert('ตั๋วนี้ชำระเงินแล้ว ไม่สามารถแจ้งตั๋วหายได้');
            return;
        }
        if (!confirm(`ยืนยันแจ้งตั๋วสูญหายสำหรับ ${ticket.licensePlate}?\nระบบจะคิดค่าจอดตามเวลาจริง + ค่าปรับ 300 บาท`)) return;
        ticket.status = 'LOST';
        saveState(state);
        const data = feeData(ticket, state);
        appState.currentFeePreview = data;
        document.getElementById('exitSearchQuery').value = ticket.licensePlate;
        window.renderFeePreview(data);
    };

    window.submitPayment = async function () {
        const fee = appState.currentFeePreview;
        if (!fee) {
            alert('กรุณาค้นหาตั๋วก่อนชำระเงิน');
            return;
        }
        const state = loadState();
        const ticket = findTicket(fee.ticketId, state);
        if (!ticket) {
            alert('ไม่พบตั๋วที่ต้องการชำระ');
            return;
        }
        const amount = Number(fee.fee || 0);
        const method = appState.selectedPayMethod || 'PROMPTPAY';
        let cashTendered = null;
        let change = 0;
        if (method === 'CASH') {
            cashTendered = Number(document.getElementById('cashTenderedInput')?.value || 0);
            if (!Number.isFinite(cashTendered) || cashTendered < amount) {
                alert(`จำนวนเงินสดไม่เพียงพอ ต้องชำระอย่างน้อย ฿${amount.toFixed(2)}`);
                return;
            }
            change = cashTendered - amount;
        }
        if (method === 'CREDIT_CARD') {
            const card = String(document.getElementById('ccNumber')?.value || '').replace(/\D/g, '');
            const holder = String(document.getElementById('ccHolder')?.value || '').trim();
            if (card.length !== 16 || !holder) {
                alert('กรุณากรอกหมายเลขบัตร 16 หลักและชื่อผู้ถือบัตรให้ครบ');
                return;
            }
        }
        const receipt = {
            paymentId: `PAY-DEMO-${String(state.paymentSequence++).padStart(4, '0')}`,
            transactionRef: `DEMO-${Date.now()}`,
            ticketId: ticket.ticketId,
            licensePlate: ticket.licensePlate,
            amount,
            method,
            methodLabel: method === 'PROMPTPAY' ? 'PromptPay QR' : method === 'CREDIT_CARD' ? 'บัตรเครดิต' : 'เงินสด',
            paymentTime: fmt(nowMs(state)),
            paidAt: iso(nowMs(state)),
            cashTendered,
            change
        };
        ticket.status = 'PAID';
        ticket.fee = amount;
        state.payments.push(receipt);
        saveState(state);
        window.showReceiptModal(receipt);
        refreshAll();
    };

    window.showReceiptModal = function (receipt) {
        const set = (id, value) => { const el = document.getElementById(id); if (el) el.innerText = value; };
        set('rcpPaymentId', receipt.paymentId);
        set('rcpRef', receipt.transactionRef || '-');
        set('rcpTicketId', receipt.ticketId);
        set('rcpPlate', receipt.licensePlate);
        set('rcpMethod', receipt.methodLabel || receipt.method);
        set('rcpTime', receipt.paymentTime);
        set('rcpAmount', `฿${Number(receipt.amount || 0).toFixed(2)}`);
        const row = document.getElementById('rcpCashDetailsRow');
        if (receipt.cashTendered != null) {
            if (row) row.style.display = 'flex';
            set('rcpCashTendered', `฿${Number(receipt.cashTendered || 0).toFixed(2)}`);
            set('rcpChange', `฿${Number(receipt.change || 0).toFixed(2)}`);
        } else if (row) row.style.display = 'none';
        document.getElementById('receiptModalOverlay')?.classList.add('active');
    };

    window.finishPaymentAndOpenExitGate = async function () {
        const ticketId = appState.currentFeePreview?.ticketId;
        if (!ticketId) return;
        const state = loadState();
        const ticket = state.tickets.find(t => t.ticketId === ticketId);
        if (!ticket || ticket.status !== 'PAID') {
            alert('ต้องชำระเงินสำเร็จก่อนเปิดไม้กั้นทางออก');
            return;
        }
        ticket.status = 'EXITED';
        ticket.exitAt = iso(nowMs(state));
        saveState(state);
        closeModal('receiptModalOverlay');
        animateGate('exit', () => {
            const card = document.getElementById('feeResultCard');
            if (card) card.style.display = 'none';
            const search = document.getElementById('exitSearchQuery');
            if (search) search.value = '';
            appState.currentFeePreview = null;
            refreshAll();
        });
    };

    window.fastForward = async function (minutes) {
        const state = loadState();
        state.offsetMinutes = Number(state.offsetMinutes || 0) + Number(minutes || 0);
        saveState(state);
        refreshAll();
        if (appState.currentFeePreview) window.searchTicketForExit();
    };

    window.resetSimTime = async function () {
        const state = loadState();
        state.offsetMinutes = 0;
        saveState(state);
        refreshAll();
        if (appState.currentFeePreview) window.searchTicketForExit();
    };

    window.createReservation = async function (event) {
        event.preventDefault();
        const state = loadState();
        const start = document.getElementById('reservationStart')?.value;
        const end = document.getElementById('reservationEnd')?.value;
        if (!start || !end || new Date(end) <= new Date(start)) {
            alert('วันเวลาสิ้นสุดต้องมากกว่าวันเวลาเริ่มต้น');
            return;
        }
        state.reservations.push({
            id: `RSV-${Date.now()}`,
            licensePlate: document.getElementById('reservationPlate').value.trim(),
            vehicleType: document.getElementById('reservationType').value,
            requiresCharging: document.getElementById('reservationCharging').checked,
            startTime: start,
            endTime: end,
            checkedIn: false,
            cancelled: false
        });
        saveState(state);
        document.getElementById('dashboardMessage').innerText = 'สร้างการจองสำเร็จ (Demo)';
        event.target.reset();
        window.loadDailyDashboard();
    };

    window.createMembership = async function (event) {
        event.preventDefault();
        const state = loadState();
        const from = document.getElementById('memberFrom').value;
        const until = document.getElementById('memberUntil').value;
        if (new Date(until) < new Date(from)) {
            alert('วันหมดอายุสมาชิกต้องไม่น้อยกว่าวันเริ่มต้น');
            return;
        }
        const plate = document.getElementById('memberPlate').value.trim();
        const existing = state.memberships.find(m => m.licensePlate.toLowerCase() === plate.toLowerCase());
        const member = {
            memberId: document.getElementById('memberId').value.trim(),
            memberName: document.getElementById('memberName').value.trim(),
            licensePlate: plate,
            membershipType: document.getElementById('memberType').value,
            membershipTypeDisplay: document.getElementById('memberType').value,
            validFrom: from,
            validUntil: until
        };
        if (existing) Object.assign(existing, member); else state.memberships.push(member);
        saveState(state);
        document.getElementById('dashboardMessage').innerText = 'บันทึกสมาชิกสำเร็จ (Demo)';
        event.target.reset();
        window.loadDailyDashboard();
    };

    window.loadFeatureLists = async function () {
        const state = loadState();
        const reservations = document.getElementById('reservationsList');
        const memberships = document.getElementById('membershipsList');
        if (reservations) reservations.innerHTML = state.reservations.length
            ? state.reservations.slice(-5).reverse().map(item => `<div class="feature-list-item"><strong>${escapeHtml(item.licensePlate)}</strong><span>${escapeHtml(item.vehicleType)} | ${escapeHtml(item.startTime.replace('T',' '))}</span><em>${item.checkedIn ? 'เข้าจอดแล้ว' : item.cancelled ? 'ยกเลิก' : 'รอเข้าจอด'}</em></div>`).join('')
            : '<span class="text-muted">ยังไม่มีข้อมูล</span>';
        if (memberships) memberships.innerHTML = state.memberships.length
            ? state.memberships.slice(-5).reverse().map(item => `<div class="feature-list-item"><strong>${escapeHtml(item.licensePlate)}</strong><span>${escapeHtml(item.memberName)} | ${escapeHtml(item.membershipType)}</span><em>ถึง ${escapeHtml(item.validUntil)}</em></div>`).join('')
            : '<span class="text-muted">ยังไม่มีข้อมูล</span>';
    };

    window.loadDailyDashboard = async function () {
        const state = loadState();
        const dateInput = document.getElementById('dashboardDate');
        if (dateInput && !dateInput.value) dateInput.value = new Date(nowMs(state)).toISOString().slice(0,10);
        const date = dateInput?.value;
        const revenue = state.payments.filter(p => !date || String(p.paidAt || '').slice(0,10) === date).reduce((s,p) => s + Number(p.amount || 0), 0);
        const floors = buildFloors(state);
        const total = floors.reduce((n,f) => n + f.totalSlots,0);
        const occupied = floors.reduce((n,f) => n + f.totalSlots - f.availableSlots,0);
        const set = (id, value) => { const el = document.getElementById(id); if (el) el.innerText = value; };
        set('dailyRevenue', `฿${revenue.toFixed(2)}`);
        set('dailyOccupancy', `${total ? Math.round(occupied * 100 / total) : 0}%`);
        set('dailyReservations', state.reservations.filter(r => !date || r.startTime.slice(0,10) === date).length);
        set('dailyMembers', state.memberships.length);
        set('dashboardMessage', `${occupied}/${total} ช่องกำลังใช้งาน | ชำระแล้ว ${state.payments.length} รายการ | Demo data บันทึกใน Browser`);
        window.loadFeatureLists();
    };

    window.triggerAiAnprScan = async function () {
        const input = document.getElementById('licensePlate');
        if (!input.value.trim()) randomizePlate();
        const type = document.querySelector('input[name="vType"]:checked')?.value || 'CAR';
        const target = document.getElementById('anprTargetText');
        const result = document.getElementById('anprResultBox');
        const confidence = document.getElementById('anprConfidenceText');
        const explanation = document.getElementById('anprExplanationText');
        if (target) target.innerText = 'สแกนสำเร็จ — รอพนักงานยืนยัน';
        if (confidence) confidence.innerText = `Confidence: ${(98.5 + Math.random() * 1.3).toFixed(1)}%`;
        if (explanation) explanation.innerText = `Demo ANPR ตรวจพบ ${input.value.trim()} และจำแนกเป็น ${typeMeta[type].label}`;
        if (result) result.style.display = 'block';
        window.requestAiRecommendation();
    };

    window.requestAiRecommendation = async function () {
        const state = loadState();
        const type = document.querySelector('input[name="vType"]:checked')?.value || 'CAR';
        const charging = !!document.getElementById('requiresCharging')?.checked;
        const slot = findSlot(state, type, charging);
        const badge = document.getElementById('aiRecSlotBadge');
        const score = document.getElementById('aiMatchScoreBadge');
        const reason = document.getElementById('aiRecReasonText');
        const energy = document.getElementById('aiEnergyText');
        const congestion = document.getElementById('aiCongestionText');
        if (badge) badge.innerText = slot ? `ช่องแนะนำ: ${slot.slotNumber}` : 'ไม่มีช่องที่เหมาะสม';
        if (score) score.innerText = slot ? '98.8% Match' : '0% Match';
        if (reason) reason.innerText = slot ? `เลือก ${slot.slotNumber} เพราะชนิดช่องตรงกับรถและเป็นช่องว่างที่ใกล้ทางเข้าที่สุด` : 'ขณะนี้ไม่มีช่องว่างที่ตรงกับเงื่อนไข';
        if (energy) energy.innerText = 'ประหยัดพลังงาน: ลดการวนหาที่จอดด้วยการจัดสรรล่วงหน้า';
        if (congestion) congestion.innerText = 'การจราจร: กระจายรถตามประเภทช่องเพื่อลดคอขวด';
    };

    window.showSlotDetails = function (slotNumber) {
        const state = loadState();
        const floors = buildFloors(state);
        let slot;
        for (const f of floors) {
            slot = f.slots.find(s => s.slotNumber === slotNumber);
            if (slot) break;
        }
        if (!slot) return;
        const set = (id, value) => { const el = document.getElementById(id); if (el) el.innerText = value; };
        set('slotModalNumber', `${slot.slotNumber} (ชั้น ${slot.floorNumber})`);
        const badge = document.getElementById('slotModalStatusBadge');
        if (badge) {
            badge.className = `status-badge ${slot.isAvailable ? 'available' : 'occupied'}`;
            badge.innerText = slot.statusLabel;
        }
        const body = document.getElementById('slotModalBody');
        if (body) {
            if (slot.isAvailable) {
                body.innerHTML = `<p>ช่องจอดนี้ว่างพร้อมให้บริการ</p><p class="text-muted mt-3">ประเภทช่องจอด: <strong>${escapeHtml(slot.slotTypeDisplay)}</strong></p><div class="mt-4"><button class="btn-primary btn-block" onclick="quickParkToSlot('${escapeHtml(slot.slotNumber)}')">นำรถเข้าจอดในช่องนี้ทันที (Check-In)</button></div>`;
            } else {
                const v = slot.vehicle;
                body.innerHTML = `<div class="ticket-row"><span>ป้ายทะเบียน:</span><strong>${escapeHtml(v.licensePlate)}</strong></div><div class="ticket-row"><span>ประเภทรถ:</span><span>${escapeHtml(v.vehicleTypeDisplay)}</span></div><div class="ticket-row"><span>เวลาเข้าจอด:</span><span class="mono">${escapeHtml(v.entryTime)}</span></div><div class="ticket-row"><span>ระยะเวลา:</span><strong class="text-success">${escapeHtml(durationText(v.parkedDurationMinutes))}</strong></div><div class="ticket-row"><span>Pricing Strategy:</span><span class="mono-badge">${escapeHtml(v.rateStrategy)}</span></div><div class="ticket-row amount-total-row mt-3"><span>ค่าบริการปัจจุบัน:</span><strong class="text-success">฿${Number(v.currentFee || 0).toFixed(2)}</strong></div><div class="mt-4"><button class="btn-primary btn-block" onclick="quickCheckoutSlot('${escapeHtml(v.licensePlate)}')">นำรถคันนี้ไปชำระเงิน & ออกจากระบบ (Check-Out)</button></div>`;
            }
        }
        document.getElementById('slotDetailModalOverlay')?.classList.add('active');
    };

    function applyHeatmap() {
        const cards = Array.from(document.querySelectorAll('.slot-card'));
        cards.forEach((card, index) => {
            card.style.outline = window.__demoHeatmapEnabled ? `2px solid rgba(255,120,0,${0.25 + (index % 5) * 0.12})` : '';
            card.style.boxShadow = window.__demoHeatmapEnabled ? `0 0 ${8 + (index % 4) * 5}px rgba(255,120,0,.22)` : '';
        });
    }

    window.toggleAiHeatmap = function () {
        window.__demoHeatmapEnabled = !window.__demoHeatmapEnabled;
        const legend = document.getElementById('aiHeatLegend');
        const text = document.getElementById('aiHeatmapBtnText');
        if (legend) legend.style.display = window.__demoHeatmapEnabled ? '' : 'none';
        if (text) text.innerText = window.__demoHeatmapEnabled ? 'ปิด AI Heatmap' : 'เปิด AI Heatmap';
        applyHeatmap();
    };

    window.loadAiPredictiveData = function () {
        const state = loadState();
        const currentHour = new Date(nowMs(state)).getHours();
        const container = document.getElementById('aiForecastBarsContainer');
        if (container) {
            const hours = Array.from({length: 24}, (_, i) => i);
            container.innerHTML = hours.map(h => {
                const peak = (h >= 8 && h <= 10) || (h >= 17 && h <= 20);
                const pct = peak ? 75 + (h % 3) * 8 : 28 + (h * 7 % 38);
                return `<div title="${String(h).padStart(2,'0')}:00 = ${pct}%" style="display:inline-block;width:3.6%;margin-right:.4%;vertical-align:bottom;text-align:center"><div style="height:${Math.max(12,pct)}px;background:currentColor;opacity:${h===currentHour?1:.45};border-radius:4px 4px 0 0"></div><small style="font-size:8px">${h}</small></div>`;
            }).join('');
        }
        const traffic = document.getElementById('aiCurrentTrafficStatus');
        const multiplier = document.getElementById('aiSurgeMultiplierText');
        const advice = document.getElementById('aiDynamicPricingAdvice');
        const xai = document.getElementById('aiPredictorXaiText');
        const peak = (currentHour >= 8 && currentHour <= 10) || (currentHour >= 17 && currentHour <= 20);
        if (traffic) traffic.innerText = peak ? 'ช่วงหนาแน่น (Peak)' : 'ปกติ (Steady State)';
        if (multiplier) multiplier.innerText = peak ? 'ตัวคูณแนะนำ: 1.2x (Demo Insight)' : 'ตัวคูณอัตรา: 1.0x (Standard)';
        if (advice) advice.innerText = peak ? 'AI คาดการณ์ความต้องการสูง แนะนำเตรียมช่องชั้น 2 เพิ่ม' : 'อัตรามาตรฐานเหมาะสมกับความหนาแน่นปัจจุบัน';
        if (xai) xai.innerText = peak ? 'เหตุผล: ชั่วโมงเร่งด่วน + สัดส่วนช่องชั้น 1 ที่ถูกใช้เพิ่มขึ้น' : 'เหตุผล: อัตราเข้า-ออกอยู่ในช่วงสมดุล';
        const list = document.getElementById('aiExecRecommendationsList');
        if (list) list.innerHTML = '<div class="feature-list-item"><strong>1. กระจายรถ</strong><span>แนะนำช่องว่างตามประเภทรถเพื่อลดการวน</span></div><div class="feature-list-item"><strong>2. EV</strong><span>สงวนช่อง EV ให้รถที่ต้องการชาร์จ</span></div><div class="feature-list-item"><strong>3. Peak hour</strong><span>เตรียมชั้น 2 รองรับช่วง 17:00–20:00</span></div>';
    };

    function copilotAnswer(question) {
        const q = String(question || '').toLowerCase();
        const state = loadState();
        const floors = buildFloors(state);
        const available = floors.map(f => `ชั้น ${f.floorNumber} ว่าง ${f.availableSlots}/${f.totalSlots}`).join(', ');
        const revenue = state.payments.reduce((s,p) => s + Number(p.amount || 0), 0);
        if (q.includes('ว่าง') || q.includes('ที่จอด')) return `สถานะปัจจุบัน: ${available}`;
        if (q.includes('รายได้')) return `รายได้สะสมใน Demo ตอนนี้ ฿${revenue.toFixed(2)} จาก ${state.payments.length} รายการชำระ`;
        if (q.includes('ev') || q.includes('ชาร์จ')) return 'รถ EV ที่ต้องการชาร์จจะถูกจัดไปช่อง EV_CHARGING ก่อน เพื่อไม่ให้รถทั่วไปแย่งช่องชาร์จ';
        if (q.includes('ราคา') || q.includes('ค่าบริการ') || q.includes('คิดเงิน')) return 'CAR: ฟรี 15 นาทีแรก, ชั่วโมงแรก 20 บาท, ชั่วโมงถัดไป 30 บาท/ชม.; MOTORCYCLE 10 บาท/ชม.; EV 40 บาท/ชม.; TRUCK 50 บาท/ชม.';
        if (q.includes('oop')) return 'ระบบใช้ Encapsulation, Inheritance, Polymorphism และ Abstraction ร่วมกับ Strategy, Factory และ Observer Pattern โดย ParkingService ทำหน้าที่เป็น Facade ของ workflow หลัก';
        return 'Demo Copilot พร้อมตอบเรื่องสถานะช่องจอด รายได้ จุดชาร์จ EV อัตราค่าบริการ และแนวคิด OOP ของระบบ';
    }

    function appendChat(containerId, who, text) {
        const log = document.getElementById(containerId);
        if (!log) return;
        const wrap = document.createElement('div');
        wrap.className = `chat-bubble ${who === 'user' ? 'user' : 'ai'}`;
        wrap.innerHTML = `<div class="bubble-header"><span class="bubble-author">${who === 'user' ? 'คุณ' : '🤖 AI Smart Parking Copilot'}</span><span class="bubble-time">สด</span></div><div class="bubble-content"></div>`;
        wrap.querySelector('.bubble-content').textContent = text;
        log.appendChild(wrap);
        log.scrollTop = log.scrollHeight;
    }

    window.sendQuickCopilotPrompt = function (text) {
        appendChat('aiCopilotChatLog', 'user', text);
        setTimeout(() => appendChat('aiCopilotChatLog', 'ai', copilotAnswer(text)), 180);
    };
    window.submitCopilotChat = function () {
        const input = document.getElementById('copilotInputText');
        const text = input?.value.trim();
        if (!text) return;
        input.value = '';
        window.sendQuickCopilotPrompt(text);
    };
    window.handleCopilotKeyPress = function (event) {
        if (event.key === 'Enter') { event.preventDefault(); window.submitCopilotChat(); }
    };
    window.toggleFloatingCopilot = function () {
        document.getElementById('floatingCopilotWindow')?.classList.toggle('active');
    };
    window.sendFloatingPrompt = function (text) {
        appendChat('floatingChatLog', 'user', text);
        setTimeout(() => appendChat('floatingChatLog', 'ai', copilotAnswer(text)), 180);
    };
    window.submitFloatingCopilotChat = function () {
        const input = document.getElementById('floatingCopilotInput');
        const text = input?.value.trim();
        if (!text) return;
        input.value = '';
        window.sendFloatingPrompt(text);
    };
    window.handleFloatingCopilotKeyPress = function (event) {
        if (event.key === 'Enter') { event.preventDefault(); window.submitFloatingCopilotChat(); }
    };

    window.resetDemoData = function () {
        if (!confirm('รีเซ็ตข้อมูล Demo ทั้งหมดกลับค่าเริ่มต้น?')) return;
        localStorage.removeItem(STORAGE_KEY);
        appState.currentFeePreview = null;
        refreshAll();
        alert('รีเซ็ตข้อมูล Demo เรียบร้อย');
    };

    document.addEventListener('DOMContentLoaded', () => {
        appState.isServerOnline = false;
        window.loadAiPredictiveData();
        setTimeout(() => {
            window.requestAiRecommendation();
            refreshAll();
        }, 0);
    });
})();
