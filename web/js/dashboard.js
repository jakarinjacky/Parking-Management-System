/* Live operations use the same authenticated platform API and PostgreSQL state as the layout editor. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const date = value => value ? new Date(value).toLocaleString('th-TH') : '—';
  const baht = value => Number(value || 0).toLocaleString('th-TH',{style:'currency',currency:'THB'});
  const isPages = location.hostname.endsWith('github.io');
  const workspaceTenant=new URLSearchParams(location.search).get('tenant')||'';
  let state, siteId = '', floor = 1, tab = 'map', saving = false;
  const site = () => state?.sites.find(item => item.id === siteId);
  function notice(message, error = false) {
    $('notice').textContent = message; $('notice').hidden = false;
    $('notice').classList.toggle('error', error);
  }
  async function api(path, data) {
    const response = await fetch(`/api/platform/${path}`, {method:data === undefined ? 'GET' : 'POST',credentials:'same-origin',headers:{...(data===undefined?{}:{'Content-Type':'application/json'}),...(workspaceTenant?{'X-Workspace-Tenant':workspaceTenant}:{})},body:data === undefined ? undefined : JSON.stringify(data)});
    const result = await response.json();
    if (!response.ok) {
      if (response.status === 401) showLogin();
      throw new Error(result.error || `API ${response.status}`);
    }
    return result;
  }
  function showLogin() { state = null; $('loginPanel').hidden = false; $('workspace').hidden = true; $('identity').hidden = true; }
  function active() { return (site()?.tickets || []).filter(ticket => ticket.status === 'ACTIVE'); }
  function available(type) {
    const taken = new Set(active().map(ticket => ticket.slotId));
    return (site()?.published || []).filter(cell => cell.type === 'SLOT' && !taken.has(cell.id) && (!type || window.PlatformCore.compatible(type, cell.slotType)));
  }
  async function refresh(preferred = siteId) {
    const next = await api('state');
    if(next.user.role==='super_admin'&&!workspaceTenant){location.replace('platform.html');return;}
    state = next;
    document.querySelectorAll('a[href^="platform.html"]').forEach(a=>a.href=next.user.role==='super_admin'&&workspaceTenant?`platform.html?tenant=${encodeURIComponent(workspaceTenant)}`:'platform.html');
    siteId = next.sites.some(item => item.id === preferred) ? preferred : (next.sites[0]?.id || '');
    $('loginPanel').hidden = true; $('workspace').hidden = false; $('identity').hidden = false;
    $('account').textContent = `${next.tenants[0]?.name || ''} · ${next.user.username} · ${{super_admin:'เจ้าของแพลตฟอร์ม',owner:'เจ้าของ',admin:'ผู้ดูแล',staff:'พนักงาน'}[next.user.role] || next.user.role}`;
    render();
  }
  function render() {
    const picker = $('sitePicker'); picker.innerHTML = state.sites.map(item => `<option value="${esc(item.id)}">${esc(item.name)}</option>`).join(''); picker.value = siteId;
    const s = site(), cells = (s?.published || []).filter(cell => cell.type === 'SLOT'), tickets = active(), taken = new Map(tickets.map(ticket => [ticket.slotId, ticket]));
    $('total').textContent = cells.length; $('available').textContent = available().length; $('occupied').textContent = tickets.length;
    $('departed').textContent = (s?.tickets || []).filter(ticket => ticket.status === 'EXITED').length;
    const floors = [...new Set(cells.map(cell => Number(cell.floor)))].sort((a,b) => a-b);
    if (!floors.includes(floor)) floor = floors[0] || 1;
    $('floorPicker').innerHTML = floors.map(value => `<option value="${value}">ชั้น ${value}</option>`).join(''); $('floorPicker').value = String(floor);
    $('slotMap').innerHTML = cells.filter(cell => Number(cell.floor) === floor).length ? cells.filter(cell => Number(cell.floor) === floor).map(cell => {
      const ticket = taken.get(cell.id);
      return `<article class="slot ${ticket ? 'busy' : 'free'}"><span>${esc(cell.slotType)}</span><strong>${esc(cell.label)}</strong><small>${ticket ? esc(ticket.licensePlate) : 'ว่าง'}</small></article>`;
    }).join('') : '<p class="empty">ยังไม่มีผังเผยแพร่ ไปที่แพลตฟอร์มเพื่อออกแบบและเผยแพร่ช่องจอด</p>';
    updateSlotOptions(); renderActive(); renderHistory();
    document.querySelectorAll('[data-tab]').forEach(button => button.classList.toggle('selected', button.dataset.tab === tab));
    document.querySelectorAll('.view').forEach(view => view.hidden = view.id !== `view${tab[0].toUpperCase()}${tab.slice(1)}`);
  }
  function updateSlotOptions() {
    const select = $('entryForm').elements.slotId, previous = select.value, type = $('entryForm').elements.vehicleType.value;
    select.innerHTML = available(type).map(cell => `<option value="${esc(cell.id)}">${esc(cell.label)} · ชั้น ${Number(cell.floor)}</option>`).join('');
    if (available(type).some(cell => cell.id === previous)) select.value = previous;
  }
  function renderActive() {
    const rows = active();
    $('activeTickets').innerHTML = rows.length ? `<table><thead><tr><th>ทะเบียน</th><th>ช่อง</th><th>เวลาเข้า</th><th>ดำเนินการ</th></tr></thead><tbody>${rows.map(ticket => `<tr><td>${esc(ticket.licensePlate)}</td><td>${esc(ticket.slotNumber)}</td><td>${date(ticket.entryTime)}</td><td><button data-exit="${esc(ticket.ticketId)}">ยืนยันรับเงินสด / รถออก</button></td></tr>`).join('')}</tbody></table>` : '<p class="empty">ยังไม่มีรถจอดในลานนี้</p>';
  }
  function historyRows() {
    const from = $('fromDate').value, to = $('toDate').value, plate = $('plateFilter').value.trim().toLowerCase();
    return (site()?.tickets || []).filter(ticket => {
      const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Bangkok',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(ticket.entryTime)).map(part => [part.type,part.value]));
      const day = `${parts.year}-${parts.month}-${parts.day}`;
      return (!from || day >= from) && (!to || day <= to) && ticket.licensePlate.toLowerCase().includes(plate);
    }).slice().sort((a,b) => b.entryTime.localeCompare(a.entryTime));
  }
  function renderHistory() {
    const rows = historyRows(), staff = state?.user.role === 'staff';
    $('exportButton').hidden = staff; $('historyNote').textContent = staff ? 'พนักงานเห็นรายการที่กำลังจอดอยู่ตามสิทธิ์บัญชี' : `เก็บย้อนหลัง 3 เดือน · ${rows.length} รายการตามตัวกรอง`;
    $('historyTable').innerHTML = rows.length ? `<table><thead><tr><th>ทะเบียน</th><th>ช่อง</th><th>เข้า</th><th>ออก</th><th>สถานะ</th><th>ค่าจอด</th></tr></thead><tbody>${rows.map(ticket => `<tr><td>${esc(ticket.licensePlate)}</td><td>${esc(ticket.slotNumber)}</td><td>${date(ticket.entryTime)}</td><td>${date(ticket.exitTime)}</td><td>${ticket.status === 'ACTIVE' ? 'กำลังจอด' : 'ออกแล้ว'}</td><td>${baht(ticket.fee)}</td></tr>`).join('')}</tbody></table>` : '<p class="empty">ไม่พบรายการตามเงื่อนไข</p>';
  }
  async function guarded(fn) { if (saving) return; saving = true; try { await fn(); } catch(error) { notice(error.message, true); } finally { saving = false; } }
  async function command(action, values) {
    try {
      await api('command',{action,siteId,revision:state.revision,...values});
      await refresh();
    } catch(error) {
      if (error.message.includes('รีเฟรช')) await refresh();
      throw error;
    }
    render();
  }
  $('loginForm').onsubmit = event => { event.preventDefault(); guarded(async () => { await api('login',Object.fromEntries(new FormData(event.target))); event.target.reset(); await refresh(); }); };
  $('logout').onclick = () => guarded(async () => { await api('logout',{}); showLogin(); });
  $('refresh').onclick = () => guarded(async () => { await refresh(); notice('อัปเดตข้อมูลแล้ว'); });
  $('sitePicker').onchange = event => { siteId = event.target.value; floor = 1; render(); };
  $('floorPicker').onchange = event => { floor = Number(event.target.value); render(); };
  document.querySelector('.tabs').onclick = event => { const button = event.target.closest('[data-tab]'); if (button) { tab = button.dataset.tab; render(); } };
  $('entryForm').elements.vehicleType.onchange = updateSlotOptions;
  $('entryForm').onsubmit = event => { event.preventDefault(); guarded(async () => {
    const data = Object.fromEntries(new FormData(event.target));
    await command('checkin',data); event.target.reset(); notice('บันทึกรถเข้าแล้ว');
  }); };
  $('activeTickets').onclick = event => { const button = event.target.closest('[data-exit]'); if (!button) return;
    const ticket = active().find(item => item.ticketId === button.dataset.exit);
    if (!ticket || !confirm(`ยืนยันรับเงินสด / ไม่มีค่าบริการ และนำรถ ${ticket.licensePlate} ออก? ระบบคำนวณค่าจอดอีกครั้งเมื่อบันทึก`)) return;
    guarded(async () => { await command('checkout',{ticketId:ticket.ticketId}); notice('บันทึกรถออกแล้ว'); });
  };
  ['fromDate','toDate','plateFilter'].forEach(id => $(id).oninput = renderHistory);
  $('exportButton').onclick = () => guarded(async () => {
    if (state.user.role === 'staff') throw new Error('บัญชีพนักงานไม่มีสิทธิ์ส่งออกประวัติ');
    if ($('fromDate').value && $('toDate').value && $('fromDate').value > $('toDate').value) throw new Error('วันที่เริ่มต้องไม่เกินวันที่สิ้นสุด');
    await refresh();
    [['historyFrom','fromDate'],['historyTo','toDate'],['historyPlate','plateFilter']].forEach(([target,source]) => $(target).value = $(source).value);
    window.setHistoryExportRecords(historyRows()); window.exportParkingHistory();
    notice($('historyExportMessage').textContent || 'ส่งออกแล้ว');
  });
  if (isPages) { notice('เว็บตัวอย่างบน GitHub Pages ไม่มี Java Backend กรุณาเปิดเว็บที่รันบนเซิร์ฟเวอร์', true); $('loginForm').querySelector('button').disabled = true; }
  else api('state').then(next => { state = next; return refresh(); }).catch(error => { showLogin(); if (!error.message.includes('กรุณาเข้าสู่')) notice(error.message, true); });
})();
