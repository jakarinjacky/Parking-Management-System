/* Full presentation UI backed exclusively by the tenant-authorized platform API. */
const appState={selectedFloor:1,selectedPayMethod:'CASH',currentFeePreview:null,floorsData:[]};
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
(() => {
'use strict';
const $=id=>document.getElementById(id), esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt=v=>v?new Date(v).toLocaleString('th-TH',{timeZone:'Asia/Bangkok'}):'—';
const money=v=>Number(v||0).toLocaleString('th-TH',{style:'currency',currency:'THB'});
const day=v=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Bangkok',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(v));
const tenant=new URLSearchParams(location.search).get('tenant')||'';
const types={CAR:'รถยนต์ทั่วไป',ELECTRIC_VEHICLE:'รถยนต์ไฟฟ้า (EV)',MOTORCYCLE:'มอเตอร์ไซค์',TRUCK:'รถขนาดใหญ่'};
let state,siteId='',tab='lot-view',busy=false,preferred='',heat=false,quote=null,receipt=null,loadedAt=Date.now(),noticeTimer;
const site=()=>state?.sites.find(s=>s.id===siteId), active=()=>site()?.tickets.filter(t=>t.status==='ACTIVE')||[];
const cells=()=>site()?.published.filter(c=>c.type==='SLOT')||[];
const clock=()=>site()?.clockTime?new Date(site().clockTime).getTime()+Date.now()-loadedAt:Date.now();
const can=id=>!!state&&(state.user.role!=='staff'||['lot-view','entry-gate','exit-cashier'].includes(id));
function notice(text,error=false){$('notice').textContent=text;$('notice').hidden=false;$('notice').classList.toggle('error',error);clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>$('notice').hidden=true,8000);}
async function api(path,data){const r=await fetch(`/api/platform/${path}`,{method:data===undefined?'GET':'POST',credentials:'same-origin',headers:{...(data===undefined?{}:{'Content-Type':'application/json'}),...(tenant?{'X-Workspace-Tenant':tenant}:{})},body:data===undefined?undefined:JSON.stringify(data)});let v;try{v=await r.json();}catch{throw Error('เชื่อมต่อเซิร์ฟเวอร์ไม่สำเร็จ');}if(!r.ok){if(r.status===401)showLogin();throw Error(v.error||'ทำรายการไม่สำเร็จ');}return v;}
function showLogin(){state=null;quote=null;receipt=null;appState.currentFeePreview=null;$('workspace').hidden=true;$('loginPanel').hidden=false;document.querySelectorAll('.modal-overlay').forEach(m=>m.classList.remove('active'));$('floatingCopilotWindow').classList.remove('active');$('floatingAiLauncher').hidden=true;}
async function guarded(fn){if(busy)return;busy=true;try{await fn();}catch(e){notice(e.message,true);}finally{busy=false;}}
async function refresh(){const next=await api('state');if(next.user.role==='super_admin'&&!tenant){location.replace('platform.html');return;}state=next;loadedAt=Date.now();if(!state.sites.some(s=>s.id===siteId))siteId=state.sites[0]?.id||'';$('workspace').hidden=false;$('loginPanel').hidden=true;render();}
async function command(action,data={}){if(!state)throw Error('กรุณาเข้าสู่ระบบ');let r;try{r=await api('command',{action,siteId,revision:state.revision,...data});}catch(e){await refresh().catch(()=>{});throw e;}await refresh();return r.operationResult;}
function free(type,charging=false,plate='') {const taken=new Set(active().map(t=>t.slotId));return cells().filter(c=>!taken.has(c.id)&&(!type||PlatformCore.compatible(type,c.slotType))&&(!charging||type!=='ELECTRIC_VEHICLE'||c.slotType==='EV_CHARGING')&&!(site()?.reservations||[]).some(r=>r.status==='BOOKED'&&r.slotId===c.id&&Date.parse(r.to)>clock()&&r.plate!==plate));}
function rate(type){const s=site(),p=s?.policy||{},key={CAR:'carRate',ELECTRIC_VEHICLE:'evRate',MOTORCYCLE:'motorcycleRate',TRUCK:'truckRate'}[type];return Number(p[key]??-1)>=0?p[key]:s?.rate||0;}
function rateText(t){return `ฟรี ${t.freeMinutes??site()?.freeMinutes??0} นาทีแรก · ${money(t.rate??rate(t.vehicleType))}/ชั่วโมง · ปัดขึ้นเต็มชั่วโมง${Number(t.dailyCap)>0?' · เพดาน '+money(t.dailyCap)+'/24 ชั่วโมง':''}`;}
function elapsed(t){return Math.max(0,Math.floor((clock()-Date.parse(t.entryTime))/60000));}
function feeView(t){const minutes=Math.max(0,Math.floor((Date.parse(t.quotedAt)-Date.parse(t.entryTime))/60000));return {...t,vehicleTypeDisplay:types[t.vehicleType],entryTime:fmt(t.entryTime),currentTime:fmt(t.quotedAt),durationDisplay:formatDurationMinutes(minutes),fee:Number(t.fee),strategyName:'PricingPolicy · อัตราตามลาน',rateDescription:rateText(t),isLostTicket:!!t.lostTicketPenalty,parkingFee:Number(t.fee)-Number(t.lostTicketPenalty||0)};}
function render(){if(!state)return;const s=site(),all=cells(),taken=new Map(active().map(t=>[t.slotId,t]));
$('account').textContent=`${state.tenants[0]?.name||''} · ${state.user.username} (${{super_admin:'เจ้าของแพลตฟอร์ม',owner:'เจ้าของบริษัท',admin:'ผู้ดูแล',staff:'พนักงาน'}[state.user.role]})`;
$('companyContext').textContent=state.tenants[0]?.name||'';$('sitePicker').innerHTML=state.sites.length?state.sites.map(s=>`<option value="${esc(s.id)}">${esc(s.name)}</option>`).join(''):'<option>ยังไม่มีลานจอด</option>';$('sitePicker').value=siteId;
$('liveMode').textContent=s?.sample?'พื้นที่ตัวอย่าง · ข้อมูลสมมุติ':'ข้อมูลบริษัทบนเซิร์ฟเวอร์';
document.querySelectorAll('a[href^="platform.html"]').forEach(a=>a.href=tenant?`platform.html?tenant=${encodeURIComponent(tenant)}`:'platform.html');
$('headerAvailCount').textContent=all.length-taken.size;$('headerOccCount').textContent=taken.size;
$('headerRevenueText').textContent=money((s?.tickets||[]).filter(t=>t.paidAt||t.status==='EXITED').reduce((n,t)=>n+Number(t.fee||0),0));
$('displayBoardMessage').textContent=s?`${s.sample?'ตัวอย่าง':'ข้อมูลจริง'} · ${s.name} · ว่าง ${all.length-taken.size}/${all.length} ช่อง · อัปเดต ${new Date().toLocaleTimeString('th-TH')}`:'สร้างลานและเผยแพร่ผังในแพลตฟอร์มก่อนเริ่มใช้งาน';
$('clockController').hidden=!s?.sample||!['owner','super_admin'].includes(state.user.role);
const floors=[...new Set(all.map(c=>Number(c.floor)))].sort((a,b)=>a-b);if(!floors.includes(appState.selectedFloor))appState.selectedFloor=floors[0]||1;
appState.floorsData=floors.map(f=>({floorNumber:f,availableSlots:all.filter(c=>Number(c.floor)===f&&!taken.has(c.id)).length,totalSlots:all.filter(c=>Number(c.floor)===f).length}));
$('floorPillGroup').innerHTML=appState.floorsData.map(f=>`<button data-floor="${f.floorNumber}" class="floor-btn ${f.floorNumber===appState.selectedFloor?'active':''}">ชั้น ${f.floorNumber} (${f.availableSlots}/${f.totalSlots} ว่าง)</button>`).join('');renderSlots();renderTickets();renderHistory();renderFeatures();renderDaily();updateVehicleSelection();renderPermissions();
$('quickActivePlates').innerHTML=active().length?active().map(t=>`<button class="plate-chip" data-ticket="${esc(t.ticketId)}">🚗 ${esc(t.licensePlate)} (${esc(t.slotNumber)})${t.paidAt?' · ชำระแล้ว':''}</button>`).join(''):'<span class="text-muted">ยังไม่มีรถจอดอยู่ในลาน</span>';
$('btnIssueTicket').disabled=!s?.active||!free().length;
if(!can(tab))tab='lot-view';showTab(tab);}
function renderPermissions(){document.querySelectorAll('.tab-btn').forEach(b=>{const id=b.getAttribute('onclick')?.match(/switchTab\('([^']+)'\)/)?.[1];b.hidden=id?!can(id):false;});document.querySelectorAll('.tab-content').forEach(p=>p.hidden=!can(p.id.replace('tab-','')));$('floatingAiLauncher').hidden=!can('ai-ops');if(!can('ai-ops'))$('floatingCopilotWindow').classList.remove('active');document.querySelector('.metric-pill.revenue').hidden=!can('dashboard');$('btnToggleAiHeatmap').hidden=!can('ai-ops');}
function renderSlots(){const onFloor=cells().filter(c=>Number(c.floor)===appState.selectedFloor),taken=new Map(active().map(t=>[t.slotId,t]));
$('currentFloorTitle').textContent=`ชั้น ${appState.selectedFloor}: ${[...new Set(onFloor.map(c=>PlatformCore.slotTypes[c.slotType]))].join(', ')||'ยังไม่มีช่องจอด'}`;
$('currentFloorStats').textContent=`ว่าง ${onFloor.filter(c=>!taken.has(c.id)).length} ช่อง | จอดอยู่ ${onFloor.filter(c=>taken.has(c.id)).length} ช่อง`;
$('slotsGrid').innerHTML=onFloor.length?onFloor.map(c=>{const t=taken.get(c.id),status=t?'occupied':'available';return `<button class="slot-card ${status} ${c.slotType==='EV_CHARGING'?'ev-charger':''} ${heat&&t?'live-heat':''}" data-slot="${esc(c.id)}"><div class="slot-card-header"><div><div class="slot-number">${esc(c.label)}</div><div class="slot-type-tag">${esc(PlatformCore.slotTypes[c.slotType])}</div></div><span class="slot-status-badge ${status}">${t?'จอดอยู่':'ว่าง'}</span></div><div class="slot-car-preview" ${t?'':'style="opacity:.35"'}><div class="slot-car-icon">${t?getVehicleIcon(t.vehicleType):'🅿️'}</div><div>${t?`<div class="slot-plate-text">${esc(t.licensePlate)}</div><div class="slot-duration-text">${t.paidAt?'ชำระแล้ว · รอออก':'จอดแล้ว '+formatDurationMinutes(elapsed(t))}</div>`:'<div class="slot-duration-text">พร้อมรองรับรถเข้าจอด</div>'}</div></div></button>`;}).join(''):'<p class="text-muted">ยังไม่มีช่องจอดที่เผยแพร่ กรุณาเปิดแพลตฟอร์มเพื่อออกแบบและเผยแพร่ผัง</p>';}
function showTab(id){tab=id;document.querySelectorAll('.tab-content').forEach(p=>p.classList.toggle('active',p.id==='tab-'+id));document.querySelectorAll('.tab-btn').forEach(b=>b.classList.toggle('active',b.getAttribute('onclick')?.includes("'"+id+"'")));}
function switchTab(id){if(!can(id))return;showTab(id);if(id==='ai-ops')loadAiPredictiveData();}
function renderTickets(){const rows=can('tickets-history')?site()?.tickets||[]:[];$('ticketsTableBody').innerHTML=rows.slice().reverse().map(t=>`<tr><td class="mono">${esc(t.ticketId)}</td><td>${esc(t.licensePlate)}</td><td>${esc(types[t.vehicleType])}</td><td>${esc(t.slotNumber)} / ชั้น ${Number(t.floorNumber)}</td><td>${fmt(t.entryTime)}</td><td>${fmt(t.exitTime)}</td><td>${t.status==='EXITED'?'ออกแล้ว':t.paidAt?'ชำระแล้ว รอออก':'กำลังจอด'}</td><td>${t.paidAt||t.status==='EXITED'?money(t.fee):'รอชำระ'}</td><td>${t.status==='ACTIVE'?`<button class="btn-secondary" data-ticket="${esc(t.ticketId)}">${t.paidAt?'นำรถออก':'ชำระเงิน'}</button>`:'—'}</td></tr>`).join('')||'<tr><td colspan="9">ยังไม่มีรายการ</td></tr>';}
function historyRows(){const from=$('historyFrom').value,to=$('historyTo').value,plate=$('historyPlate').value.trim().toLowerCase();return (site()?.tickets||[]).filter(t=>(!from||day(t.entryTime)>=from)&&(!to||day(t.entryTime)<=to)&&t.licensePlate.toLowerCase().includes(plate)).slice().sort((a,b)=>b.entryTime.localeCompare(a.entryTime));}
function renderHistory(){const rows=can('vehicle-history')?historyRows():[];window.setHistoryExportRecords?.(rows);$('historyExportButton').disabled=!rows.length||!can('vehicle-history');$('historyEnteredCount').textContent=rows.length;$('historyExitedCount').textContent=rows.filter(t=>t.status==='EXITED').length;$('historyParkedCount').textContent=rows.filter(t=>t.status==='ACTIVE').length;$('historyRevenue').textContent=money(rows.filter(t=>t.paidAt||t.status==='EXITED').reduce((n,t)=>n+Number(t.fee||0),0));$('historyRetentionMessage').textContent='เก็บประวัติย้อนหลัง 3 เดือน · ข้อมูลเฉพาะลานที่เลือก';$('vehicleHistoryTableBody').innerHTML=rows.map(t=>`<tr><td>${esc(t.ticketId)}</td><td>${esc(t.licensePlate)}</td><td>${esc(types[t.vehicleType])}</td><td>ชั้น ${Number(t.floorNumber)} / ${esc(t.slotNumber)}</td><td>${fmt(t.entryTime)}</td><td>${fmt(t.exitTime)}</td><td>${t.status==='EXITED'?'ออกแล้ว':'กำลังจอด'}</td><td>${money(t.fee)}</td></tr>`).join('')||'<tr><td colspan="8">ไม่พบประวัติตามเงื่อนไข</td></tr>';}
function renderFeatures(){const s=site();$('reservationsList').innerHTML=can('reservations')?(s?.reservations||[]).slice().reverse().map(r=>`<div class="feature-list-item"><strong>${esc(r.plate)}</strong><span>${esc(cells().find(c=>c.id===r.slotId)?.label||r.slotId)} · ${fmt(r.from)} – ${fmt(r.to)}</span><em>${esc(r.status)}</em>${r.status==='BOOKED'?`<button class="btn-secondary live-cancel" data-cancel="${esc(r.id)}">ยกเลิกการจอง</button>`:''}</div>`).join('')||'ยังไม่มีรายการ':'';
$('membershipsList').innerHTML=can('memberships')?(s?.memberships||[]).map(m=>`<div class="feature-list-item"><strong>${esc(m.plate)}</strong><span>${esc(m.name)} · ห้อง ${esc(m.room)} · ${esc(m.membershipType||'STANDARD_MEMBER')}</span><em>${esc(m.starts||'—')} ถึง ${esc(m.expires)}</em></div>`).join('')||'ยังไม่มีรายการ':'';
const select=$('reservationSlot'),old=select.value;select.innerHTML='<option value="">จัดช่องให้อัตโนมัติ</option>'+free().map(c=>`<option value="${esc(c.id)}">${esc(c.label)} / ชั้น ${Number(c.floor)}</option>`).join('');if(free().some(c=>c.id===old))select.value=old;}
function renderDaily(){if(!can('dashboard'))return;const s=site(),d=$('dashboardDate').value||day(clock()),records=(s?.tickets||[]).filter(t=>(t.paidAt||t.status==='EXITED')&&day(t.paidAt||t.exitTime)===d);$('dashboardDate').value=d;$('dailyRevenue').textContent=money(records.reduce((n,t)=>n+Number(t.fee||0),0));$('dailyOccupancy').textContent=(cells().length?Math.round(active().length/cells().length*100):0)+'%';$('dailyReservations').textContent=(s?.reservations||[]).filter(r=>r.status!=='CANCELLED'&&day(r.from)===d).length;$('dailyMembers').textContent=(s?.memberships||[]).filter(m=>(m.starts||'1970-01-01')<=d&&m.expires>=d).length;$('dashboardMessage').textContent=`${active().length}/${cells().length} ช่องกำลังใช้งาน | รับชำระ ${records.length} รายการ · วันที่ไทย`;}
function updateVehicleSelection(){const type=document.querySelector('[name=vType]:checked')?.value||'CAR';document.querySelectorAll('.type-card').forEach(c=>c.classList.toggle('active',!!c.querySelector('input:checked')));$('rateStrategyBadge').textContent='PricingPolicy';$('rateStrategyDesc').textContent=rateText({vehicleType:type})+' · อัตราจริงบันทึกตอนรถเข้า รวมกฎวันหยุดและส่วนลดสมาชิก';requestAiRecommendation();}
function recommendation(type,charging,plate){const candidates=free(type,charging,plate),reserved=(site()?.reservations||[]).find(r=>r.status==='BOOKED'&&r.plate===plate&&Date.parse(r.to)>clock());return candidates.find(c=>c.id===reserved?.slotId)||candidates.find(c=>c.id===preferred)||candidates.slice().sort((a,b)=>{const vip=(site()?.memberships||[]).some(m=>m.plate.toLowerCase()===plate.toLowerCase()&&m.membershipType==='VIP_MEMBER'&&(m.starts||'1970-01-01')<=day(clock())&&m.expires>=day(clock()));return (vip?Number(b.slotType==='VIP')-Number(a.slotType==='VIP'):0)||Number(a.floor)-Number(b.floor)||PlatformCore.route(site().published,a.id).length-PlatformCore.route(site().published,b.id).length;})[0];}
function requestAiRecommendation(){const type=document.querySelector('[name=vType]:checked')?.value||'CAR',c=recommendation(type,$('requiresCharging').checked,$('licensePlate').value.trim());$('aiRecSlotBadge').textContent=c?'ช่องแนะนำ: '+c.label:'ไม่มีช่องที่เหมาะสม';$('aiMatchScoreBadge').textContent='Rule-based';$('aiRecReasonText').textContent=c?'เลือกช่องว่างที่รองรับรถ ตรวจการจองและช่องที่เลือกก่อน แล้วเรียงชั้นและระยะทางในผัง':'ตรวจประเภทช่องและการจอง หรือเผยแพร่ผังเพิ่ม';$('aiEnergyText').textContent='ยังไม่มีข้อมูลวัดการประหยัดพลังงาน';$('aiCongestionText').textContent='คำแนะนำจากผังและสถานะปัจจุบัน ไม่ใช่โมเดลเรียนรู้';}
function ticketDisplay(t){return {...t,vehicleTypeDisplay:types[t.vehicleType],entryTime:fmt(t.entryTime),pricingStrategy:'PricingPolicy',rateDescription:rateText(t),memberVerified:!!t.memberRoom,memberName:t.memberRoom?'ห้อง '+t.memberRoom:''};}
async function handleCheckIn(e){e.preventDefault();await guarded(async()=>{const plate=$('licensePlate').value.trim(),type=document.querySelector('[name=vType]:checked').value,c=recommendation(type,$('requiresCharging').checked,plate);if(!c)throw Error('ไม่มีช่องว่างที่รองรับรถ');await command('checkin',{plate,vehicleType:type,slotId:c.id,requiresCharging:type==='ELECTRIC_VEHICLE'&&$('requiresCharging').checked});const t=active().find(t=>t.licensePlate===plate);preferred='';showTicketModal(ticketDisplay(t));animateGate('entry');$('licensePlate').value='';});}
async function searchTicketForExit(){await guarded(async()=>{quote=await api('quote',{siteId,query:$('exitSearchQuery').value.trim()});appState.currentFeePreview=feeView(quote);renderFeePreview(appState.currentFeePreview);$('paymentConfirmed').checked=false;receipt=null;if(quote.paidAt){receipt=quote;showLiveReceipt(quote);}selectPayMethod(appState.selectedPayMethod);});}
function showLiveReceipt(t){showReceiptModal({paymentId:t.paymentId,transactionRef:t.paymentReference||'รับชำระโดยเจ้าหน้าที่',ticketId:t.ticketId,licensePlate:t.licensePlate,amount:Number(t.fee),methodLabel:t.paymentMethod,paymentTime:fmt(t.paidAt),cashTendered:t.paymentMethod==='MANUAL_CASH'?Number(t.cashTendered??t.fee):null,change:Number(t.change||0)});}
async function submitPayment(){await guarded(async()=>{if(!quote)throw Error('ค้นหาตั๋วก่อน');if(!$('paymentConfirmed').checked)throw Error('กรุณายืนยันว่ารับชำระเงินจริงแล้ว');receipt=await command('recordPayment',{ticketId:quote.ticketId,expectedFee:Number(quote.fee),method:appState.selectedPayMethod,cashTendered:Number($('cashTenderedInput').value),reference:$('paymentReference').value.trim(),confirmed:true});showLiveReceipt(receipt);});}
async function finishPaymentAndOpenExitGate(){await guarded(async()=>{if(!receipt?.paidAt)throw Error('ยังไม่มีรายการรับชำระ');await command('checkout',{ticketId:receipt.ticketId});closeModal('receiptModalOverlay');$('feeResultCard').style.display='none';quote=null;receipt=null;appState.currentFeePreview=null;animateGate('exit');notice('บันทึกรถออกแล้ว');});}
async function handleLostTicket(){if(!confirm('แจ้งตั๋วหายและเพิ่มค่าปรับ 300 บาท?'))return;await guarded(async()=>{await command('reportLostTicket',{query:$('exitSearchQuery').value.trim()});quote=await api('quote',{siteId,query:$('exitSearchQuery').value.trim()});appState.currentFeePreview=feeView(quote);renderFeePreview(appState.currentFeePreview);});}
function animateGate(lane){const arm=$(lane+'BarrierArm'),status=$(lane+'GateStatusText');arm.classList.add('open');$(lane+'LightRed').classList.remove('active');$(lane+'LightGreen').classList.add('active');status.textContent='จำลองไม้กั้นเปิด · ยังไม่ได้สั่งอุปกรณ์จริง';setTimeout(()=>{arm.classList.remove('open');$(lane+'LightRed').classList.add('active');$(lane+'LightGreen').classList.remove('active');status.textContent='ไม้กั้นจำลองปิด';},1800);}
async function showSlotDetails(id){const c=cells().find(c=>c.id===id);if(!c)return;const t=active().find(t=>t.slotId===id);$('slotModalNumber').textContent=`${c.label} (ชั้น ${c.floor})`;$('slotModalStatusBadge').textContent=t?'จอดอยู่':'ว่าง';$('slotModalBody').innerHTML=`<p>${esc(PlatformCore.slotTypes[c.slotType])}</p>${t?`<p>ทะเบียน ${esc(t.licensePlate)}</p><p>เข้า ${fmt(t.entryTime)} · ${formatDurationMinutes(elapsed(t))}</p><button class="btn-primary" data-ticket="${esc(t.ticketId)}">ชำระเงิน / นำรถออก</button>`:`<button class="btn-primary" data-park="${esc(c.id)}">นำรถเข้าจอดในช่องนี้</button>`}`;$('slotDetailModalOverlay').classList.add('active');}
async function createReservation(e){e.preventDefault();await guarded(async()=>{if(!can('reservations'))throw Error('ไม่มีสิทธิ์');const type=$('reservationType').value,plate=$('reservationPlate').value.trim(),c=free(type,$('reservationCharging').checked,plate).find(c=>c.id===$('reservationSlot').value)||(!$('reservationSlot').value?recommendation(type,$('reservationCharging').checked,plate):null);if(!c)throw Error('ช่องที่เลือกไม่รองรับประเภทรถ');await command('reserve',{plate,slotId:c.id,vehicleType:type,from:new Date($('reservationStart').value).toISOString(),to:new Date($('reservationEnd').value).toISOString()});$('reservationMessage').textContent='สร้างการจองสำเร็จ';});}
async function createMembership(e){e.preventDefault();await guarded(async()=>{if(!can('memberships'))throw Error('ไม่มีสิทธิ์');await command('addMember',{plate:$('memberPlate').value.trim(),name:$('memberName').value.trim(),room:$('memberRoom').value.trim(),membershipCode:$('memberId').value.trim(),membershipType:$('memberType').value,starts:$('memberFrom').value,expires:$('memberUntil').value});$('membershipMessage').textContent='บันทึกสมาชิกสำเร็จ';});}
async function triggerAiAnprScan(){await guarded(async()=>{await refresh();if(!$('licensePlate').value.trim()){if(!site()?.sample)throw Error('กรอกทะเบียนก่อนทดสอบกล้องจำลอง');randomizePlate();}$('anprTargetText').textContent='จำลอง ANPR · รอพนักงานยืนยัน';$('anprConfidenceText').textContent='ไม่มีค่าความมั่นใจจากกล้องจริง';$('anprExplanationText').textContent='ใช้ทะเบียนที่กรอก: '+$('licensePlate').value.trim()+' · ไม่มีการวิเคราะห์ภาพหรือเปิดไม้กั้นจริง';$('anprResultBox').style.display='block';requestAiRecommendation();});}
function randomizePlate(){if(!site()?.sample){notice('สุ่มทะเบียนได้เฉพาะลานตัวอย่าง',true);return;}$('licensePlate').value='DEMO-'+Math.floor(1000+Math.random()*9000);requestAiRecommendation();}
function toggleAiHeatmap(){if(!can('ai-ops'))return;heat=!heat;$('aiHeatLegend').style.display=heat?'':'none';$('aiHeatmapBtnText').textContent=heat?'ปิด Heatmap สถานะจริง':'เปิด AI Heatmap';renderSlots();}
function loadAiPredictiveData(){if(!can('ai-ops'))return;const rows=site()?.tickets||[],counts=Array(24).fill(0);rows.forEach(t=>counts[Number(new Intl.DateTimeFormat('en-GB',{hour:'2-digit',hourCycle:'h23',timeZone:'Asia/Bangkok'}).format(new Date(t.entryTime)))]++);const max=Math.max(1,...counts),occupancy=cells().length?active().length/cells().length:0;$('aiForecastBarsContainer').innerHTML=counts.map((n,h)=>`<div style="flex:1;min-width:12px;text-align:center" title="${h}:00 · ${n} คัน"><div style="height:${n/max*110}px;min-height:2px;background:#b9e879;border-radius:4px 4px 0 0"></div><small>${h}</small></div>`).join('');$('aiCurrentTrafficStatus').textContent='ใช้งาน '+Math.round(occupancy*100)+'%';$('aiSurgeMultiplierText').textContent='อัตราคิดเงินจริงตามการตั้งค่าลาน';$('aiDynamicPricingAdvice').textContent=occupancy>.8?'พื้นที่ใกล้เต็ม ควรจัดเจ้าหน้าที่ดูแลทางเข้า':'ยังมีพื้นที่รองรับรถ ตรวจช่องที่รองรับประเภทรถก่อนรับเข้า';$('aiPredictorXaiText').textContent='กราฟคือจำนวนรถเข้ารายชั่วโมงจากประวัติที่มี ไม่ใช่การรับรองความแม่นยำของพยากรณ์';$('aiExecutiveSummaryText').textContent=`วิเคราะห์ ${rows.length} รายการในลาน ${site()?.name||'—'} · แสดงเฉพาะข้อมูลที่บัญชีนี้เข้าถึงได้`;$('aiExecRecommendationsList').innerHTML='<p>จัดช่องตามประเภทรถและการจอง ใช้จำนวนช่องว่างต่อชั้นช่วยกระจายรถ ตรวจประวัติจริงก่อนเปลี่ยนราคา</p>';$('aiMetricEfficiency').textContent='ใช้พื้นที่ '+Math.round(occupancy*100)+'%';$('aiMetricCarbon').textContent='ยังไม่มีข้อมูลวัดจริง';}
function answer(q){const s=site();if(/รายได้|เงิน/.test(q))return 'ยอดรับชำระในประวัติลานนี้ '+money((s?.tickets||[]).filter(t=>t.paidAt||t.status==='EXITED').reduce((n,t)=>n+Number(t.fee||0),0));if(/EV|ชาร์จ/i.test(q))return 'ช่อง EV ว่าง '+free('ELECTRIC_VEHICLE',true).length+' ช่อง · เลือกต้องการชาร์จเพื่อแนะนำช่อง EV';if(/ราคา|อัตรา|Strategy/i.test(q))return rateText({vehicleType:'CAR'})+' กฎวันหยุดและส่วนลดคำนวณตอนรถเข้า';if(/OOP|คลาส|โค้ด/i.test(q))return 'PlatformService ตรวจสิทธิ์และธุรกรรม, ParkingLayout ตรวจผัง, PricingPolicy คำนวณราคา และ PlatformStore แยกวิธีจัดเก็บข้อมูล';if(/แนะนำ|ทำไม/.test(q))return 'ตรวจประเภทช่อง การจองและช่องที่เลือก แล้วเรียงชั้นกับระยะทางตามผัง ยังไม่ใช่ AI ที่เรียนรู้จากภาพ';if(/ว่าง|ชั้น|ที่จอด/.test(q))return appState.floorsData.map(f=>`ชั้น ${f.floorNumber} ว่าง ${f.availableSlots}/${f.totalSlots}`).join(' · ')||'ยังไม่มีผังเผยแพร่';return 'ผู้ช่วยนี้ตอบตามกฎจากข้อมูลลานปัจจุบัน ลองถามช่องว่าง รายได้ อัตราค่าจอด EV หรือ OOP';}
function appendChat(id,who,text){const el=document.createElement('div');el.className='chat-bubble '+who;el.textContent=text;$(id).append(el);$(id).scrollTop=$(id).scrollHeight;}
async function chat(id,text){if(!can('ai-ops')||!text.trim())return;appendChat(id,'user',text.slice(0,500));try{await refresh();appendChat(id,'ai',answer(text));}catch(e){appendChat(id,'ai','โหลดข้อมูลล่าสุดไม่ได้: '+e.message);}}
function resetContext(){quote=null;receipt=null;preferred='';heat=false;appState.currentFeePreview=null;appState.selectedFloor=1;$('feeResultCard').style.display='none';document.querySelectorAll('.modal-overlay').forEach(m=>m.classList.remove('active'));for(const id of ['aiCopilotChatLog','floatingChatLog'])$(id).replaceChildren();for(const id of ['historyFrom','historyTo','historyPlate','exitSearchQuery','paymentReference'])$(id).value='';window.setHistoryExportRecords?.([]);}
Object.assign(window,{switchTab,updateVehicleSelection,requestAiRecommendation,triggerAiAnprScan,randomizePlate,handleCheckIn,searchTicketForExit,submitPayment,finishPaymentAndOpenExitGate,handleLostTicket,showSlotDetails,createReservation,createMembership,toggleAiHeatmap,loadAiPredictiveData,
loadSystemStatus:()=>guarded(refresh),loadParkingLotData:()=>guarded(refresh),loadTicketsAndPayments:()=>guarded(refresh),loadFeatureLists:()=>guarded(refresh),loadDailyDashboard:()=>guarded(refresh),
loadParkingHistory:()=>guarded(async()=>{if($('historyFrom').value&&$('historyTo').value&&$('historyFrom').value>$('historyTo').value)throw Error('วันที่เริ่มต้องไม่เกินวันที่สิ้นสุด');await refresh();}),
fastForward:minutes=>guarded(()=>command('sampleTime',{minutes})),resetSimTime:()=>guarded(()=>command('sampleTime',{minutes:0,reset:true})),
toggleFloatingCopilot:()=>{if(can('ai-ops'))$('floatingCopilotWindow').classList.toggle('active');},sendQuickCopilotPrompt:q=>chat('aiCopilotChatLog',q),sendFloatingPrompt:q=>chat('floatingChatLog',q),submitCopilotChat:()=>{const q=$('copilotInputText').value;$('copilotInputText').value='';chat('aiCopilotChatLog',q);},submitFloatingCopilotChat:()=>{const q=$('floatingCopilotInput').value;$('floatingCopilotInput').value='';chat('floatingChatLog',q);},handleCopilotKeyPress:e=>{if(e.key==='Enter'){e.preventDefault();window.submitCopilotChat();}},handleFloatingCopilotKeyPress:e=>{if(e.key==='Enter'){e.preventDefault();window.submitFloatingCopilotChat();}}});
$('loginForm').onsubmit=e=>{e.preventDefault();guarded(async()=>{await api('login',Object.fromEntries(new FormData(e.target)));e.target.reset();resetContext();await refresh();});};
$('logout').onclick=()=>guarded(async()=>{await api('logout',{});resetContext();showLogin();});
$('sitePicker').onchange=e=>{siteId=e.target.value;resetContext();render();};
$('dashboardDate').onchange=renderDaily;
$('licensePlate').oninput=requestAiRecommendation;
['historyFrom','historyTo','historyPlate'].forEach(id=>$(id).oninput=()=>{$('historyExportButton').disabled=true;window.setHistoryExportRecords?.([]);});
document.addEventListener('click',e=>{const b=e.target.closest('[data-floor],[data-slot],[data-ticket],[data-park],[data-cancel]');if(!b)return;if(b.dataset.floor){appState.selectedFloor=Number(b.dataset.floor);render();}if(b.dataset.slot)showSlotDetails(b.dataset.slot);if(b.dataset.ticket){closeModal('slotDetailModalOverlay');switchTab('exit-cashier');$('exitSearchQuery').value=b.dataset.ticket;searchTicketForExit();}if(b.dataset.park){const c=cells().find(c=>c.id===b.dataset.park);preferred=c.id;closeModal('slotDetailModalOverlay');switchTab('entry-gate');const type={MOTORCYCLE:'MOTORCYCLE',LARGE:'TRUCK',EV_CHARGING:'ELECTRIC_VEHICLE'}[c.slotType]||'CAR';document.querySelector(`[name=vType][value=${type}]`).checked=true;updateVehicleSelection();$('licensePlate').focus();}if(b.dataset.cancel&&confirm('ยกเลิกการจองนี้?'))guarded(()=>command('cancelReservation',{reservationId:b.dataset.cancel}));});
selectPayMethod('CASH');showLogin();
const tick=()=>{const now=fmt(clock());$('displayBoardTime').textContent=now;$('simulatedTimeText').textContent=now;};tick();setInterval(tick,1000);
refresh().catch(e=>{showLogin();if(!e.message.includes('เข้าสู่'))notice(e.message,true);});
setInterval(()=>{if(state&&!busy&&!document.hidden&&!document.querySelector('.modal-overlay.active'))guarded(refresh);},15000);
})();
