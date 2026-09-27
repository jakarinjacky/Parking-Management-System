/* Decorative scene is isolated from authentication and business state. */
(function(){
  const intro=document.querySelector('#loginScreen .intro, #loginPanel > div');
  if(intro){
    intro.classList.add('studio-intro');
    intro.insertAdjacentHTML('afterbegin','<div class="studio-kicker">GREENPARK / INTERACTIVE STUDIO</div>');
    intro.insertAdjacentHTML('beforeend','<div class="studio-scene" aria-hidden="true"><div class="studio-island"><div class="studio-road"></div><i class="studio-bay">P</i><i class="studio-bay">EV</i><i class="studio-bay">P</i><i class="studio-bay">P</i><div class="studio-car"></div><div class="studio-sign">GREENPARK</div></div><span class="studio-orbit">YOUR SPACE. YOUR DESIGN.</span></div><p class="studio-caption">ออกแบบพื้นที่ · จำลองรถเข้าออก · เชื่อมการทำงานในระบบเดียว<br>เข้าสู่ระบบ แล้วเลือก “ลานพรีเซนต์” เพื่อทดลองด้วยข้อมูลสมมุติ</p>');
  }
  const password=document.querySelector('#loginForm [name=password]');
  if(password){
    const toggle=document.createElement('button');toggle.type='button';toggle.className='password-toggle';toggle.textContent='แสดงรหัสผ่าน';toggle.setAttribute('aria-pressed','false');
    toggle.onclick=()=>{const show=password.type==='password';password.type=show?'text':'password';toggle.textContent=show?'ซ่อนรหัสผ่าน':'แสดงรหัสผ่าน';toggle.setAttribute('aria-pressed',String(show));};
    password.closest('label').after(toggle);
  }
})();
