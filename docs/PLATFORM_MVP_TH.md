# GreenPark Platform — เวอร์ชัน MVP

โมดูลแพลตฟอร์มใหม่อยู่ในโปรเจกต์ Java SE เดิม หน้า `web/platform.html`
สถานะ: **MVP สำหรับทดลองและพัฒนาต่อ ไม่ใช่ Product พร้อมขาย**

## เปิดใช้งาน

Windows: ติดตั้ง JDK 17 ขึ้นไป แล้วเปิด `run-platform-demo.bat`

macOS/Linux: `bash run-platform-demo.sh`

เมื่อ Java Server เริ่มแล้ว เปิด `http://localhost:8080/platform.html`
ต้องไม่มี Server อีกตัวใช้พอร์ต 8080 อยู่

บัญชีทดลองทุกบัญชีใช้รหัส `DemoPass123!`:

| บัญชี | สิทธิ์ | ขอบเขต |
|---|---|---|
| superadmin | เจ้าของแพลตฟอร์ม | บริษัททั้งหมด สร้างบริษัทพร้อมเจ้าของ |
| owner | เจ้าของบริษัท A | ตัวอย่างคอนโด ห้าง โรงแรม และลานที่สร้างเพิ่ม |
| owner2 | เจ้าของบริษัท B | ลานของบริษัท B เท่านั้น |
| admin | ผู้ดูแล | คอนโดบริษัท A เท่านั้น |
| staff | พนักงาน | รถเข้า–ออกคอนโดบริษัท A ไม่เห็นประวัติการเงินย้อนหลัง |

ข้อมูลทดลองสร้างครั้งแรกเท่านั้น อยู่ใน `data/platform-demo/platform.json`
ไม่ควรใช้ชื่อ/ทะเบียนรถจริงในโหมดนี้ และห้ามเปิดพอร์ตทดลองสู่อินเทอร์เน็ต
การแก้ไฟล์ข้อมูลด้วยมือหรือเปิดหลาย Server ให้ใช้ไฟล์เดียวกันไม่รองรับ

## วิธีทดลองครบวงจร

1. เข้าเป็น owner เลือกคอนโด หรือกดสร้างลานจากแม่แบบ
2. เปิดออกแบบผัง เลือกชั้น 1–8 และเครื่องมือถนน ช่องจอด ทางเข้า ทางออก
3. คลิกวางหรือลากชิ้นส่วน เลือกชิ้นส่วนเพื่อเปลี่ยนชื่อ หมุนลูกศร หรือกำหนดทางเดียว
4. ใช้หลายช่องถนนต่อกันเป็นทางตรง ทางเลี้ยว และทางแยก
5. กดตรวจผัง บันทึกแบบร่าง และเผยแพร่ผัง
6. หน้า รถเข้า–ออก รับรถเข้าช่องที่เผยแพร่แล้ว เลือกรถให้ตรงประเภทช่อง
7. คลิกช่องบนผังเพื่อแสดงเส้นทางจากทางเข้า
8. รับเงินสดและยืนยันรถออก ระบบคำนวณยอดที่ Server
9. เปิดประวัติ เลือกช่วงวันที่/ทะเบียน กดค้นหา แล้วส่งออก `.xlsx`
10. ลอง owner2 จะไม่เห็นข้อมูลของ owner; เปลี่ยน siteId ใน API ก็ถูกปฏิเสธ

## สิ่งที่ทำงานแล้ว

- หลายบริษัทและหลายลาน: Server ตรวจ tenant และลานที่ได้รับมอบหมายทุกคำสั่ง
- บัญชีสี่ระดับ; สร้างบริษัทพร้อม owner; owner สร้าง admin/staff ที่ผูกกับลาน
- รหัสผ่านใหม่ใช้ PBKDF2-HMAC-SHA256 + salt; session หมดอายุ 8 ชั่วโมง
- แม่แบบ 9 ประเภท: คอนโด ห้าง โรงแรม สำนักงาน โรงพยาบาล สถานศึกษา ลานสาธารณะ งานอีเวนต์ กำหนดเอง
- แม่แบบใช้ผังตัวอย่างพื้นฐานร่วมกัน พร้อมคำอธิบายธุรกิจ; แบบกำหนดเองเริ่มว่าง
- Grid 24×16 ต่อชั้น สูงสุด 8 ชั้น; ช่องรถยนต์ EV มอเตอร์ไซค์ VIP ผู้พิการ รถใหญ่
- ถนนสองทาง/ทางเดียว ทางเข้า ทางออก ทางม้าลาย อาคาร และสัญลักษณ์อุปกรณ์
- วาง ลากย้าย ลบ เปลี่ยนชื่อ ทิศทาง Undo/Redo; รองรับคลิกวางบนมือถือ
- Server ตรวจพิกัดซ้ำ ชื่อช่องซ้ำ และการเชื่อมทางเข้า–ช่องจอด–ทางออกตามทิศทาง
- แบบร่างแยกจากผังใช้งาน เก็บเวอร์ชันเผยแพร่ล่าสุด 20 เวอร์ชัน ย้อนกลับเป็นแบบร่าง
- ห้ามเผยแพร่การลบ/ย้าย/เปลี่ยนช่องที่มีรถหรือการจองที่ยังไม่หมดอายุ
- ป้องกันเขียนทับกันด้วย revision; คำสั่งผิดจะ rollback; บันทึกไฟล์แบบ atomic replace
- รถเข้า–ออกแยกลาน ตรวจช่อง/ทะเบียนซ้ำ ประเภทรถ และช่องที่จองไว้
- ค่าจอด: ราคาเดียวต่อชั่วโมงต่อลาน หักช่วงฟรีแล้วปัดขึ้นชั่วโมง; snapshot ราคาไว้ตอนรถเข้า
- รับเงินสดแบบพนักงานยืนยันเท่านั้น ไม่ใช่ธุรกรรมจากธนาคาร
- เก็บสมาชิกพร้อมทะเบียน ห้อง/หน่วยงาน และวันหมดอายุ (ยังไม่ให้ส่วนลดอัตโนมัติ)
- จองช่วงเวลา ตรวจการจองซ้อน ยกเลิกได้; ช่องที่มีการจองในอนาคตสงวนไว้เพื่อกันการจอดทับ
- ประวัติรถออกย้อนหลัง 3 เดือนปฏิทิน; ล้างเมื่ออ่าน/เขียนข้อมูล เก็บรถที่ยังอยู่เสมอ
- ตัวอย่างประวัติ 30 รายการ/ลานย้อนหลัง 1–88 วัน แสดง SAMPLE ชัดเจน
- ส่งออก Excel OOXML จริง โดยใช้ตัวส่งออกเดิม; ข้อความไม่ถูกแปลงเป็นสูตร
- ทะเบียนอุปกรณ์จำลอง แสดง NOT_CONNECTED ไม่แสดงสถานะ Online ปลอม
- Audit Log 5,000 เหตุการณ์ล่าสุด ไม่บันทึกรหัสผ่าน
- UI เขียวเทา Responsive; หน้า GitHub Pages ดูตัวอย่างอ่านอย่างเดียวได้

## การเชื่อมกับระบบเดิมและ OOP

`ParkingServer` เพิ่ม handler `/api/platform` และ `index.html` เพิ่มลิงก์แพลตฟอร์ม
ใช้ Java Server และตัวส่งออก Excel เดียวกัน แต่ **ยังไม่ย้ายฐานข้อมูล/บัญชี/ตั๋วของลานเดิม**
ระบบเดิมยังเปิดได้ที่ `/index.html` โดยใช้บัญชีเดิม

แพลตฟอร์มแยก workflow รถเข้า–ออกเป็นโมดูล MVP ของตัวเองในระยะนี้ ไม่ได้อ้างว่า
`ParkingService` เดิมรองรับ multi-tenant แล้ว การรวม business logic และย้ายข้อมูลเดิม
ต้องทำผ่าน migration ที่สำรองข้อมูลและทดสอบก่อน ไม่คัดลอกทะเบียนหรือรายได้โดยอัตโนมัติ

| คลาส | หน้าที่และแนวคิด OOP |
|---|---|
| `PlatformHandler implements HttpHandler` | Adapter ระหว่าง HTTP กับ Service; Session และขนาดคำขอ |
| `PlatformService` | Encapsulation ของ state; methods สำหรับ authorization และ transactional commands |
| `ParkingLayout` | Domain object ตรวจ invariants ผังและเส้นทาง directed graph |
| `Json` | Strict nested parser; ไม่ใช้ parser แบบ flat ของระบบเดิมกับข้อมูลซ้อน |

ยังไม่ได้แยก Tenant, Device, UserAccount และ Repository เป็นคลาส typed ทุกตัว
โครงสร้าง JSON แยก tenants/sites/users/audit รองรับการ refactor ในระยะต่อไป

## API

- `POST /api/platform/login` `{username,password}`
- `GET /api/platform/state` — เฉพาะข้อมูลที่ session มีสิทธิ์
- `POST /api/platform/command` — `{action,revision,siteId,...fields}`
- `POST /api/platform/logout` `{}`

Commands: createTenant, createSite, createUser, saveLayout, publish, restore,
configure, checkin, checkout, addMember, reserve, cancelReservation, addDevice

Cookie แยกจากระบบเดิม เป็น HttpOnly + SameSite=Strict และตรวจ Origin สำหรับ POST
ไม่รับ tenant ของ owner/staff จาก client มาใช้แทน tenant ของบัญชี
ค่า tenantId สำหรับ superadmin ใช้เฉพาะการจัดการตามสิทธิ์เจ้าของแพลตฟอร์ม

## สิ่งที่ยังไม่ทำ / ห้ามนำไปโฆษณาว่ารองรับแล้ว

1. มี PostgreSQL JSONB + PlatformStore แล้ว; ยังขาด migration ข้อมูลลานเดิมและ transaction ข้ามหลาย server
2. สมาชิกอัตโนมัติ/โควตาคอนโด, visitor approval, คูปองห้าง, Valet/PMS โรงแรม
3. กฎราคาวันหยุด รายวัน แยกประเภทรถ ส่วนลด และเก็บค่า EV ตามพลังงาน
4. ถนนเวกเตอร์โค้งอิสระ ความกว้างตามเมตร การตรวจรัศมีเลี้ยว ทางลาดเชื่อมชั้น
5. กล้องจริง/ANPR จริง ESP32 จริง ไม้กั้นจริง heartbeat และ fail-safe
6. Payment Gateway, Subscription billing, จำกัดแพ็กเกจ, ใบกำกับภาษี
7. เปลี่ยนโลโก้/ธีมต่อบริษัท และ RBAC แบบสร้าง role เอง
8. มีเปลี่ยนรหัสด้วยรหัสเดิม กู้ superadmin ผ่าน console ปิดบัญชี และ session revoke แล้ว (ดู ACCOUNT_RECOVERY_TH.md); ยังขาด email recovery, MFA และ security review
9. Automated backup/restore, monitoring/alerts และ scheduled retention ตอนระบบไม่มีผู้ใช้
10. เอกสารและขั้นตอนคุ้มครองข้อมูลส่วนบุคคลสำหรับการใช้งานจริง

**ก่อนเปิดสู่อินเทอร์เน็ต:** ห้ามเปิด legacy demo APIs/บัญชีเริ่มต้นให้บุคคลภายนอก
ต้องปรับ authentication ของระบบเดิม, ใช้ HTTPS, ตรวจระบบทั้งหมด และทำ backup/restore test
`PLATFORM_SECURE_COOKIE=true` สำหรับการใช้งาน HTTPS
`PLATFORM_ADMIN_PASSWORD` (อย่างน้อย 12 ตัวอักษร) ใช้สร้าง superadmin ครั้งแรกใน store ใหม่
ห้ามเปิด `PLATFORM_DEMO` ในระบบจริง; environment variables ไม่ใช่การรับรอง production readiness

## ทดสอบ

```bash
node tests/platform-core.test.cjs
python tests/test_platform.py bin
python tests/test_roles.py bin
# ต้องติดตั้ง Playwright + Chromium ก่อน (ไม่เป็น dependency ของเว็บที่ส่งให้ผู้ใช้)
node tests/platform-browser.cjs bin
```

GitHub Pages เป็น static hosting เท่านั้น ไม่รัน Java API
การ deploy หน้า platform ไป Pages จึงให้ได้เฉพาะ preview แบบอ่านอย่างเดียว
การใช้งานข้อมูลร่วมกันต้องมี Java Server ที่เข้าถึงได้จริง
