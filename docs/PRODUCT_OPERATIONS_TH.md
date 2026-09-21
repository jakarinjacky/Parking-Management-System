# ความสามารถและขั้นตอนเปิดใช้งาน Product

## เพิ่มในรอบนี้

- PostgreSQL transaction ครอบการโหลด ตรวจสิทธิ์ แก้ไข และบันทึก โดยใช้ transaction advisory lock
  ป้องกันสอง process เขียน aggregate ทับกัน และอ่านการเปลี่ยนรหัส/ปิดบัญชีล่าสุดทุกคำขอ
  ยังคง schema JSONB เดิม ไม่ลบหรือย้ายข้อมูลเดิมแบบเสี่ยง
- Session ยังอยู่ในหน่วยความจำแต่ละ instance: หลาย instance ต้องใช้ sticky routing
  รีสตาร์ตต้อง login ใหม่ ยังไม่ใช่ระบบ HA หรือฐานข้อมูลแยกตารางเต็มรูปแบบ
- ส่วนลดสมาชิกและช่วงสิทธิ์/วันเข้าพัก–ออก ตรวจตาม Asia/Bangkok; โควตารถพร้อมกันต่อห้อง
- ราคาแยกประเภทรถ เสาร์–อาทิตย์ วันหยุด และเพดานทุก 24 ชั่วโมงที่คิดเงิน
  ลำดับราคา: วันหยุด > เสาร์อาทิตย์ > ประเภทรถ > ราคาพื้นฐาน เก็บ snapshot ตอนรับรถ
  ส่วนลดสมาชิกกับคูปองใช้ค่าที่มากกว่า ไม่บวกซ้อน ปัดขึ้นเป็นบาท ไม่มีทศนิยมสตางค์
- ขอ/อนุมัติผู้มาติดต่อ มีเวลาหมดอายุและใช้สิทธิ์ได้ครั้งเดียว ก่อนรับรถเมื่อเปิดกฎนี้
- คูปองห้างตรวจหลักฐานโดยเจ้าหน้าที่ บันทึกเลขอ้างอิงห้ามใช้ซ้ำ (ไม่เชื่อม POS)
- Valet โรงแรม: รับฝาก → จอดแล้ว → ส่งคืน ระบุบัญชีผู้ทำรายการ; ห้าม checkout ก่อนส่งคืน
- ถนนโค้งแบบร่าง quadratic Bézier กำหนดพิกัด/ความกว้างเป็นเมตร ชั้นละพื้นที่ 100×100 เมตร
  **แยกจาก Grid ใช้งานจริง** ไม่ใช่แบบก่อสร้าง ไม่มีรัศมีเลี้ยว ทางลาด หรือเส้นทางที่วิ่งตามเส้นโค้ง
- Retention ทำงานทุกชั่วโมงเมื่อ server เปิด แม้ไม่มีผู้ใช้; health endpoint ตรวจการเข้าถึง store
- Backup timer รายวัน และ restore drill ใน container ชั่วคราวแยกจาก production

## HTTPS บน AWS

ต้องมีโดเมนที่คุณควบคุม ชี้ DNS A ไปยัง EC2 และเปิด Security Group TCP 80/443
เพิ่ม GitHub Repository variable `PLATFORM_DOMAIN` เป็นชื่อโฮสต์ เช่น `parking.example.com`
จากนั้น Run workflow Deploy Platform to AWS EC2 จาก main

Workflow จะรัน Caddy, ตั้ง Secure cookie และ bind พอร์ต 8080 เฉพาะ 127.0.0.1
ใบรับรองต้องออกสำเร็จก่อนเปิดใช้งานจริง อย่าปิดการตรวจ certificate เพื่อหลบ error
ถ้าไม่มีโดเมนยังใช้ HTTP พอร์ตเดิมสำหรับข้อมูลสมมุติ และ Device Gateway ปิดอยู่
เอกสารอ้างอิง: https://caddyserver.com/docs/automatic-https

อย่าลบ PLATFORM_DOMAIN หลังเปิดใช้งาน HTTPS แล้วโดยไม่วางแผนเปลี่ยน URL
เพราะ workflow ที่ไม่มีโดเมนใช้โหมดทดลอง HTTP ไม่ใช่ production mode

## ESP32 / กล้อง Gateway

หลัง HTTPS พร้อม เพิ่ม Repository variable `PLATFORM_DEVICE_GATEWAY=true` และ Deploy
เจ้าของเปิดหน้าอุปกรณ์ → ลงทะเบียน → สร้าง/หมุนคีย์ เก็บคีย์ที่แสดงครั้งเดียวในอุปกรณ์
คีย์ในฐานข้อมูลเก็บ SHA-256 digest, ไม่แสดงใน state/audit และเปลี่ยนคีย์แล้วคีย์เก่าใช้ไม่ได้

`POST /api/platform/device` พร้อม `Authorization: Bearer <device-token>` และ JSON:

```json
{"siteId":"SITE_ID","deviceId":"DEVICE_ID","type":"HEARTBEAT"}
```

Heartbeat ทุก 30 วินาที; UI แสดง ONLINE เฉพาะเมื่อได้รับใน 90 วินาทีล่าสุด
กล้อง/edge gateway ส่ง `type: "PLATE"` และ `plate` ได้ แต่ยังไม่รับภาพหรืออ่านทะเบียนด้วย ANPR
เหตุการณ์ทะเบียนเป็นข้อมูลประกอบ ไม่อนุมัติรับรถหรือเปิดไม้กั้นเอง
คีย์ผูกกับอุปกรณ์และลาน ห้ามนำมาใช้ในหน้าเว็บสาธารณะ

ESP32 ตัวอย่าง: `hardware/esp32_bench/esp32_bench.ino`
ใช้ Arduino ESP32 core และ ArduinoJson 7; คัดลอก secrets.example.h เป็น secrets.h บนเครื่องคุณ
กรอก Wi-Fi, HTTPS URL, root CA, site/device ID และ token; เลือก LED pin ให้ตรงบอร์ด
ตัวอย่างเปิด LED 500 ms เท่านั้น เก็บ command ID ใน NVS ก่อนเปิดเพื่อไม่ทำซ้ำหลัง reboot/retry
รองรับ ACK และหมดอายุคำสั่งบน server; ไม่รับประกัน exactly-once physical actuation
**ยังไม่ได้ compile/flash/test กับบอร์ดจริงของคุณ** ต้อง bench-test ก่อนเชื่อมอุปกรณ์ใด ๆ
ไม่ต่อขานี้กับรีเลย์ มอเตอร์ หรือไม้กั้น โค้ดนี้ไม่มีคำสั่ง OPEN_BARRIER
กล้องและไม้กั้นแต่ละยี่ห้อต้องมี protocol/model และทดสอบเซนเซอร์นิรภัย/เปิดมือก่อน

## Backup และกู้คืน

Deploy ติดตั้ง `parking-backup.timer` ให้ ec2-user สำรองทุกวันเวลา 03:00 ตาม timezone เครื่อง
เก็บ 7 วันใน /opt/parking-platform/backups สิทธิ์ไฟล์จำกัด; ข้อมูลสำรองยังมีข้อมูลส่วนบุคคล
สำเนาใน EC2 ไม่ป้องกันเครื่อง/ดิสก์เสีย ต้องเลือกที่เก็บนอกเครื่องก่อนขายจริง

```bash
systemctl list-timers parking-backup.timer
journalctl -u parking-backup.service
cd /opt/parking-platform
bash scripts/backup-postgres.sh
bash scripts/restore-drill.sh /opt/parking-platform/backups/ชื่อไฟล์.dump
```

restore-drill ใช้ฐานข้อมูลชั่วคราว ไม่ทับ production และลบเฉพาะ container/volume ทดสอบของตนเมื่อจบ
ต้องมี RAM/พื้นที่พอสำหรับสำเนาทดสอบ; EC2 t3.micro อาจไม่พอสำหรับสำเนาขนาดใหญ่
CI มี PostgreSQL จริงสำหรับทดสอบเขียนพร้อมกัน/rollback และ dump/restore ของข้อมูลสมมุติ
ผล CI ไม่ใช่หลักฐานว่ากู้ข้อมูลจริงของลูกค้าได้ จึงยังต้องทดสอบ backup จริงแยกต่างหาก

## ยังเปิดใช้จริงไม่ได้จนกว่าจะมีข้อมูลเพิ่ม

1. Payment Gateway/Subscription: ยังไม่มีบัญชีร้านค้า ผู้ให้บริการ หรือ webhook credentials
   ต้องเลือก provider, ใช้ sandbox, ตรวจลายเซ็น/ยอดเงิน/idempotency และคืนเงิน ก่อนเปิด live
   ไม่รับเลขบัตรในแอป และไม่ถือภาพสลิปว่าเป็นการยืนยันรับเงินจากธนาคาร
2. กล้อง ANPR/ไม้กั้น: ต้องระบุรุ่น โปรโตคอล และสถานที่ bench test ไม่มีการยืนยันว่าเชื่อมแล้ว
3. ถนนก่อสร้าง: ต้องมีการสำรวจพื้นที่จริงและผู้เชี่ยวชาญตรวจ ไม่อนุมานความปลอดภัยจากภาพผัง
4. ข้อมูลส่วนบุคคล: กำหนดผู้รับผิดชอบ วัตถุประสงค์ อายุเก็บ สิทธิ์เข้าถึง และวิธีลบ/ส่งออก
   เอกสารนี้เป็นรายการงานเชิงระบบ ไม่ใช่การรับรองข้อกฎหมายหรือความพร้อมขาย
