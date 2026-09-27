/* Public plan information: no authentication or payment side effects. */
(() => {
  const plans = [
    ['STARTER', 'Starter', '499', 'เริ่มต้นจัดการลานจอดอย่างเป็นระบบ'],
    ['BUSINESS', 'Business', '990', 'สำหรับธุรกิจที่กำลังขยายการใช้งาน'],
    ['MULTI_SITE', 'Multi-site', '1,990', 'สำหรับธุรกิจที่ต้องการดูแลหลายสาขา']
  ];
  for (const host of document.querySelectorAll('[data-public-pricing]')) {
    host.innerHTML = `<span class="pricing-kicker">GREENPARK / PLANS</span><h2>เลือกแพ็กเกจก่อนเริ่มใช้งาน</h2>
      <p>ค่าเช่าซอฟต์แวร์รายเดือน · แยกจากรายได้ค่าจอดรถของคุณ</p>
      <div class="pricing-grid">${plans.map(([id,name,price,description]) => `<article class="pricing-plan">
        <h3>${name}</h3><p class="pricing-amount">฿${price}<span> / เดือน</span></p>
        <p>${description}</p><a class="pricing-cta" href="contact.html?plan=${id}#start">ติดต่อเรื่อง ${name} →</a>
      </article>`).join('')}</div>
      <p class="pricing-features">ระบบรองรับออกแบบลานจอดและถนน · รถเข้า–ออก · ประวัติและรายงาน · บัญชีพนักงานแยกสิทธิ์ · แจ้งปัญหาถึงผู้ดูแล</p>
      <details><summary>รายละเอียดราคาและการเปิดใช้บริการ</summary><p>รุ่นปัจจุบันยังไม่จำกัดจำนวนลาน ช่องจอด หรือพนักงานตามแพ็กเกจ โปรดตกลงขอบเขตบริการกับเจ้าของแพลตฟอร์มก่อนเปิดบัญชี ราคานี้ไม่รวมฮาร์ดแวร์ งานติดตั้ง และงานพัฒนาเพิ่มเติม ยอดชำระและเงื่อนไขให้ยึดตามบิลที่ออกให้</p><p>ชำระด้วยการโอนและรอผู้ดูแลยืนยัน ไม่มีการตัดเงินอัตโนมัติ การเลือกแพ็กเกจบนหน้านี้ยังไม่สร้างบัญชีหรือเรียกเก็บเงิน</p></details>`;
  }
  for (const button of document.querySelectorAll('[data-demo-contact]')) {
    button.addEventListener('click', () => {
      const preview = document.getElementById('contactPreview');
      preview.hidden = false;
      preview.textContent = 'ตัวอย่างข้อความทาง' + button.dataset.demoContact + ': สวัสดีครับ/ค่ะ สนใจใช้บริการ GreenPark — ' + document.getElementById('selectedPlan').textContent + ' กรุณาติดต่อกลับเพื่อสอบถามรายละเอียด (การจำลองเท่านั้น ยังไม่ได้ส่งข้อความ)';
    });
  }
  const chosen = document.getElementById('selectedPlan');
  if (chosen) {
    const plan = plans.find(p => p[0] === new URLSearchParams(location.search).get('plan'));
    chosen.textContent = plan ? `แพ็กเกจที่สนใจ: ${plan[1]} — ${plan[2]} บาท/เดือน` : 'เลือกแพ็กเกจด้านบนเพื่อดูราคาที่สนใจ';
  }
})();
