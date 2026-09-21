# ติดตั้ง Parking Platform บน AWS Free Tier

สถาปัตยกรรมเริ่มต้นใช้ EC2 เครื่องเดียว รัน Java และ PostgreSQL ด้วย Docker Compose เหมาะกับการทดลองและลูกค้ากลุ่มแรก ข้อมูลแพลตฟอร์มถูกบันทึกใน PostgreSQL JSONB ส่วน volume ของฐานข้อมูลอยู่บน EBS

## สิ่งที่ระบบเตรียมไว้แล้ว

- `Dockerfile` สร้าง Java 17 application image พร้อม PostgreSQL JDBC driver
- `compose.aws.yml` รันแอปและ PostgreSQL โดยไม่เปิดพอร์ตฐานข้อมูลออกสู่อินเทอร์เน็ต
- `.github/workflows/deploy-aws.yml` ทดสอบก่อนส่งไฟล์และ restart container บน EC2
- production ปิดบัญชี demo และสร้าง `superadmin` จาก secret ตอนเริ่มฐานข้อมูลครั้งแรก
- health check อยู่ที่ `/api/platform/health`
- สคริปต์สำรอง PostgreSQL เก็บไฟล์ย้อนหลัง 7 วัน

## 1. สร้าง EC2

1. เลือก Amazon Linux 2023 แบบ x86_64 และ instance ที่เข้าเงื่อนไข Free Tier ของบัญชี
2. Storage เริ่มที่ 10–20 GiB แบบ `gp3`
3. ใส่เนื้อหา `scripts/bootstrap-ec2.sh` ใน User data
4. Security Group เปิด SSH 22 เฉพาะ IP ของผู้ดูแล และเปิด TCP 8080 เฉพาะช่วงทดลอง
5. ผูก Elastic IP เฉพาะเมื่อจำเป็น เพราะ public IPv4 อาจมีค่าบริการหรือใช้เครดิต

## 2. ตั้งค่า GitHub

สร้าง GitHub Environment ชื่อ `production` และเพิ่ม Environment secrets:

| Secret | ค่า |
|---|---|
| `AWS_HOST` | Public IP หรือ DNS ของ EC2 |
| `AWS_USER` | `ec2-user` |
| `AWS_SSH_PRIVATE_KEY` | private key ที่ใช้ deploy เท่านั้น |
| `AWS_KNOWN_HOSTS` | ผลจาก `ssh-keyscan -H ชื่อโฮสต์` ที่ตรวจ fingerprint แล้ว |
| `DB_PASSWORD` | รหัสสุ่มยาวของ PostgreSQL |
| `PLATFORM_ADMIN_PASSWORD` | รหัสเริ่มต้น superadmin อย่างน้อย 12 ตัวอักษร |

เพิ่ม Repository variable `AWS_DEPLOY_ENABLED=true` เมื่อทดสอบ deploy แบบ manual สำเร็จแล้ว หลังจากนั้นทุก push เข้า `main` จะ deploy อัตโนมัติ

## 3. Deploy ครั้งแรก

เปิด Actions → **Deploy Platform to AWS EC2** → **Run workflow** จากนั้นตรวจ:

```bash
curl http://EC2_HOST:8080/api/platform/health
```

หน้าใช้งานคือ `http://EC2_HOST:8080/platform.html` และผู้ใช้เริ่มต้นคือ `superadmin` กับรหัสใน `PLATFORM_ADMIN_PASSWORD` รหัสนี้ถูกใช้เฉพาะการสร้างฐานข้อมูลครั้งแรก การเปลี่ยน secret ภายหลังจะไม่เปลี่ยนรหัสในข้อมูลเดิม

## 4. ก่อนเปิดให้ลูกค้าจริง

พอร์ต 8080 แบบ HTTP ใช้สำหรับทดลองเท่านั้น ก่อนรับข้อมูลจริงต้องมีโดเมนและ HTTPS reverse proxy แล้วตั้ง `PLATFORM_SECURE_COOKIE=true` จำกัด SSH ให้ผ่าน SSM หรือ IP ผู้ดูแล และปิดการเข้าถึง 8080 จากอินเทอร์เน็ต ควรย้าย backup ออกนอกเครื่อง เช่น S3 และตั้ง AWS Budget แจ้งเตือนที่ 25%, 50%, 80% และ 100% ของเครดิต

ฐานข้อมูลปัจจุบันเก็บ aggregate เป็น JSONB เพื่อย้ายจากไฟล์เดิมได้เร็ว เมื่อเริ่มขายและต้องทำรายงานหลายลานพร้อมกัน ควรแยกเป็นตาราง `tenants`, `sites`, `users`, `tickets`, `layout_versions`, `devices` และ `audit_logs` พร้อม migration tool
