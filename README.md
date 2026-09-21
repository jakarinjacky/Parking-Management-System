# 🅿️ Smart Parking Management System (OOP-Driven Full Stack Web Application)

ระบบบริหารจัดการลานจอดรถอัจฉริยะ (Smart Parking Management System) ที่ออกแบบและพัฒนาตามหลักการ **Object-Oriented Programming (OOP)**, **SOLID Principles**, และ **Design Patterns** อย่างเข้มข้น ครอบคลุมทั้งฝั่ง Domain Logic, Service Layer, Embedded REST API Server และ Modern Interactive Web Interface

## AWS + PostgreSQL deployment

แพลตฟอร์มสามารถรันด้วย PostgreSQL และ Docker บน AWS EC2 ได้ ดูขั้นตอนภาษาไทยที่ [docs/AWS_FREE_TIER_DEPLOY_TH.md](docs/AWS_FREE_TIER_DEPLOY_TH.md) โดย workflow `.github/workflows/deploy-aws.yml` รองรับการ deploy ครั้งแรกแบบ manual และ deploy อัตโนมัติจาก `main` หลังตั้งค่า `AWS_DEPLOY_ENABLED=true`

---

## 🌟 จุดเด่นและสถาปัตยกรรม OOP (Key Highlights)

### 1. เสาหลัก OOP ทั้ง 4 ประการ (The 4 OOP Pillars)
- **Abstraction (นามธรรม)**: 
  - `Vehicle`: คลาสแม่นามธรรม ซ่อนรายละเอียดและบังคับสัญญาผ่านเมธอด `canParkIn(Slot slot)` และ `getPricingStrategy()`
  - `Payment`: คลาสแม่นามธรรมสำหรับการชำระเงิน พร้อมเมธอด `processPayment()`
  - `PricingStrategy` & `SlotAllocationStrategy`: อินเตอร์เฟสกฎการคิดราคาและการจัดสรรช่องจอด
- **Encapsulation (การห่อหุ้มและการซ่อนข้อมูล)**:
  - `Slot`: ปกป้อง Invariants และความถูกต้องของสถานะช่องจอด เช่น ห้ามรับรถใหม่เมื่อสถานะเป็น `OCCUPIED` และจัดการจอง/ปลดปล่อยผ่าน `assignVehicle()` และ `release()` แบบ Thread-Safe
  - `Ticket`: ควบคุม State Transition (ACTIVE -> PAID -> EXITED หรือ LOST) โดยไม่ให้แก้ไขค่าธรรมเนียมหรือสถานะจากภายนอกโดยตรง
- **Inheritance (การสืบทอดคุณสมบัติ)**:
  - `Vehicle` ➔ `Car`, `Motorcycle`, `ElectricVehicle`, `Truck`
  - `Payment` ➔ `PromptPayPayment`, `CreditCardPayment`, `CashPayment`
- **Polymorphism & Double Dispatch**:
  - เมธอด `slot.canFitVehicle(vehicle)` ส่งต่อความรับผิดชอบให้ `vehicle.canParkIn(this)` เพื่อให้รถแต่ละชนิดตัดสินตามขีดความสามารถของตนเอง
  - การคำนวณค่าจอดเรียกผ่าน `PricingStrategy.calculateFee()` โดยผลลัพธ์จะแปรผันตามประเภทรถและระยะเวลาจริง

---

### 2. Design Patterns ที่นำมาประยุกต์ใช้
1. **Strategy Pattern**: 
   - แยกตรรกะการคิดเงินออกจากคลาสหลัก รองรับการปรับเปลี่ยนราคาหรือเพิ่มโปรโมชันตาม Open-Closed Principle
   - `StandardPricingStrategy`: ฟรี 15 นาทีแรก, ชม. แรก 20 บาท, ชม. ถัดไป 30 บาท/ชม.
   - `MotorcyclePricingStrategy`: ฟรี 30 นาทีแรก, 10 บาท/ชม.
   - `EVPricingStrategy`: 40 บาท/ชม. (รวมค่าใช้บริการจุดชาร์จ EV)
   - `TruckPricingStrategy`: 50 บาท/ชม. สำหรับรถขนาดใหญ่
2. **Observer Pattern**:
   - `ParkingLot` ทำหน้าที่เป็น Subject (Notifier)
   - `DisplayBoard` (ป้ายไฟทางเข้า) ทำหน้าที่เป็น Observer คอยรับอัปเดตจำนวนที่ว่างและข้อความเตือนเมื่อมีรถเข้าหรือออกจากช่องจอดแบบเรียลไทม์
3. **Factory Pattern**:
   - `VehicleFactory`: สร้างออบเจกต์รถตามประเภทและเงื่อนไข
   - `SlotFactory`: สร้างช่องจอดรถตามรหัสและประเภท
4. **Adapter / Dependency Inversion (Hardware Ready)**:
   - `LicensePlateReader`: Interface สำหรับกล้อง ANPR/IP Camera; Demo ใช้ `SimulatedAnprCamera`
   - `GateController`: Interface สำหรับชุดควบคุมไม้กั้น; Demo ใช้ `SimulatedGateController`
   - เมื่อต่อ ESP32/Relay หรือกล้องจริง สามารถสร้าง implementation ใหม่แทน Simulator โดยไม่แก้ Business Logic
4. **Aggregate Root / Facade**:
   - `ParkingLot` เป็น Aggregate Root ควบคุม Floor และ Slot
   - `ParkingService` เป็น Facade เชื่อมโยง Domain Entities, Repositories, Observer และ Clock Simulation

---

## 📁 โครงสร้างโปรเจกต์ (Project Structure)

```text
IDe/
├── src/
│   ├── domain/
│   │   ├── enums/
│   │   │   ├── VehicleType.java       # CAR, MOTORCYCLE, ELECTRIC_VEHICLE, TRUCK
│   │   │   ├── SlotType.java          # STANDARD, COMPACT, MOTORCYCLE, LARGE, EV_CHARGING
│   │   │   ├── SlotStatus.java        # AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE
│   │   │   ├── TicketStatus.java      # ACTIVE, PAID, EXITED, LOST
│   │   │   ├── PaymentStatus.java     # PENDING, SUCCESS, FAILED
│   │   │   └── PaymentMethod.java     # PROMPTPAY, CREDIT_CARD, CASH
│   │   ├── model/
│   │   │   ├── Vehicle.java           # Abstract Base Class
│   │   │   ├── Car.java
│   │   │   ├── Motorcycle.java
│   │   │   ├── ElectricVehicle.java
│   │   │   ├── Truck.java
│   │   │   ├── Slot.java              # Slot Entity with Encapsulation
│   │   │   ├── ParkingFloor.java      # Floor Entity managing slots
│   │   │   ├── ParkingLot.java        # Aggregate Root with Observer support
│   │   │   └── Ticket.java            # Parking Ticket Entity
│   │   ├── strategy/
│   │   │   ├── PricingStrategy.java
│   │   │   ├── StandardPricingStrategy.java
│   │   │   ├── MotorcyclePricingStrategy.java
│   │   │   ├── EVPricingStrategy.java
│   │   │   ├── TruckPricingStrategy.java
│   │   │   ├── SlotAllocationStrategy.java
│   │   │   └── NearestFirstAllocationStrategy.java
│   │   ├── observer/
│   │   │   ├── ParkingLotObserver.java
│   │   │   └── DisplayBoard.java
│   │   ├── factory/
│   │   │   ├── VehicleFactory.java
│   │   │   └── SlotFactory.java
│   │   └── payment/
│   │       ├── Payment.java           # Abstract Base Class
│   │       ├── PromptPayPayment.java
│   │       ├── CreditCardPayment.java
│   │       └── CashPayment.java
│   ├── repository/
│   │   ├── TicketRepository.java
│   │   └── PaymentRepository.java
│   ├── service/
│   │   └── ParkingService.java        # Facade / Application Service
│   ├── server/
│   │   └── ParkingServer.java         # Pure Java SE HTTP REST API & Static Server
│   ├── test/
│   │   └── ParkingSystemTest.java     # Automated Unit Test Suite
│   └── util/
│       └── SimpleJson.java            # Pure Java JSON Utility
├── web/
│   ├── index.html                     # Modern Glassmorphic SPA
│   ├── css/style.css                  # Responsive Theme & Animations
│   └── js/app.js                      # REST Client & Interactive UI Controller
├── run.bat                            # สคริปต์คอมไพล์ ทดสอบ และเปิดเว็บ (1-Click)
├── test.bat                           # สคริปต์รันเฉพาะ Unit Tests
└── README.md
```

---

## 🚀 วิธีการติดตั้งและรันระบบ (How to Run)

> [!NOTE]
> ระบบนี้พัฒนาด้วย **Pure Java SE 26** ไม่จำเป็นต้องติดตั้ง Maven, Gradle, Node.js หรือซอฟต์แวร์เสริมใดๆ เพิ่มเติม

### วิธีที่ 1: ดับเบิลคลิกไฟล์ Batch (ง่ายที่สุด)
- ดับเบิลคลิกที่ไฟล์ **`run.bat`**
- สคริปต์จะทำการ:
  1. คอมไพล์ซอร์สโค้ด Java ทั้งหมดไปยังโฟลเดอร์ `bin/`
  2. รัน Automated Unit Tests (ยืนยันผลทดสอบ 100%)
  3. สตาร์ต Server บนพอร์ต 8080
  4. เปิดเว็บเบราว์เซอร์ไปยัง `http://localhost:8080` อัตโนมัติ

### วิธีที่ 2: รันผ่าน Terminal / PowerShell
```powershell
# 1. คอมไพล์โค้ด
if (!(Test-Path bin)) { New-Item -ItemType Directory -Path bin }
javac -encoding UTF-8 -d bin (Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { $_.FullName })

# 2. รัน Unit Tests เพื่อตรวจสอบ Business Logic
java -cp bin test.ParkingSystemTest

# 3. รัน Server
java -cp bin server.ParkingServer
```
จากนั้นเปิดเบราว์เซอร์ไปที่: **`http://localhost:8080`**

---

## 💻 ฟังก์ชันและการใช้งานระบบบนหน้าเว็บ (Web Features)

### ฟีเจอร์เสริมสำหรับการใช้งานจริง
- **ประวัติรถเข้าออกย้อนหลัง 3 เดือน**: บันทึกลง `data/parking-history.db` ตั้งแต่รถเข้า อัปเดตเวลาออกและค่าบริการเมื่อรถออก ค้นตามช่วงวันที่หรือทะเบียนรถ พร้อมสรุปจำนวนรถเข้า รถออก รถที่ยังอยู่ในลาน และรายได้ โดยลบประวัติที่ออกจากลานและเก่ากว่า 3 เดือนอัตโนมัติผ่าน `GET /api/history`
  - ระบบมีข้อมูลตัวอย่าง 30 รายการ กระจายตั้งแต่ 88 วันก่อนจนถึงเมื่อวาน เพื่อใช้สาธิตกราฟและตารางย้อนหลัง โดยใช้รหัสคงที่จึงไม่เพิ่มข้อมูลซ้ำเมื่อเปิด Server ใหม่
- **Reservation**: จองล่วงหน้าผ่าน `POST /api/reservations` พร้อมทะเบียน ประเภทรถ และช่วงเวลา ระบบจะผูก reservation กับ ticket เมื่อรถเข้าจอดจริง
- **Membership Types**: ลงทะเบียนผ่าน `POST /api/memberships` ได้ 3 ประเภท: `STANDARD_MEMBER` จัดช่องทั่วไป, `VIP_MEMBER` ให้โซน VIP ก่อน, `EV_MEMBER` จัดช่อง EV เมื่อจำเป็น โดยไม่มีช่องประจำถาวร
- **Daily Dashboard**: ดูรายได้ จำนวนรายการชำระ อัตราการใช้พื้นที่ การจอง และสมาชิกที่ใช้งานผ่าน `GET /api/dashboard/daily?date=YYYY-MM-DD`
- **ANPR Member Auto-Entry**: `POST /api/ai/anpr-entry` ตรวจทะเบียนจากกล้องกับสมาชิกที่ยัง valid; ถ้าตรงกัน server จะ check-in และสั่งเปิดไม้กั้นอัตโนมัติ ถ้าไม่ตรงจะรอพนักงานยืนยัน
- **Hardware Simulation API**: `GET /api/hardware/status` ดูสถานะกล้อง/ไม้กั้น และ `POST /api/hardware/gate` สั่ง `OPEN`/`CLOSE` สำหรับ `ENTRY` หรือ `EXIT`

### ขอบเขตและข้อจำกัดของโครงงาน
- ระบบนี้เป็น **academic simulation** สำหรับสาธิต OOP, Design Patterns, REST workflow และ AI rule-based ไม่ใช่ production deployment
- Membership และ Reservation ถูกบันทึกลงไฟล์ในโฟลเดอร์ `data/` เพื่อคงข้อมูลเมื่อ restart server
- Ticket และ Payment ยังเป็น in-memory runtime ledger และจะถูกสร้างใหม่เมื่อ restart เพื่อให้สอดคล้องกับโหมดจำลอง
- `Map<String, Object>` ใช้เป็น JSON transport boundary ของ service/server เพื่อให้หน้าเว็บเรียก API ได้ง่าย; business rules สำคัญยังอยู่ใน typed domain model และ enum
- ยังไม่มีฐานข้อมูลจริง, transaction, payment gateway, hardware ANPR หรือการจัดการ secret สำหรับ production

1. **แผนผังช่องจอดรถ (2D Lot Map)**:
   - แสดงช่องจอดแยกตามชั้น (ชั้น 1: VIP & EV & เก๋ง, ชั้น 2: เก๋ง & รถเล็ก, ชั้น 3: มอเตอร์ไซค์ & รถใหญ่)
   - สีของช่องจอดแสดงสถานะแบบเรียลไทม์ (เขียว = ว่าง, แดง = มีรถจอด, ฟ้า = หัวชาร์จ EV)
   - คลิกที่ช่องจอดเพื่อดูข้อมูลรถ, เวลาเข้า, กลยุทธ์ราคา และค่าบริการสะสม ณ เวลานั้น
2. **จุดตรวจทางเข้า (Entry Gate)**:
   - ป้อนเลขทะเบียน (มีปุ่มสุ่มป้ายทะเบียน)
   - เลือกประเภทยานพาหนะ (รถยนต์, EV, มอเตอร์ไซค์, รถบรรทุก)
   - มีแอนิเมชันไม้กั้นเปิด-ปิด และออกตั๋วพร้อมบาร์โค้ด
3. **ระบบเร่งเวลาจำลอง (Time Travel / Clock Simulation)**:
   - ปุ่ม `+15m`, `+1h`, `+3h`, `+1d` เพื่อทดสอบกฎการคำนวณค่าจอดตามช่วงเวลาได้ทันที
4. **จุดชำระเงินและทางออก (Exit & Cashier)**:
   - ค้นหาตั๋วด้วยเลขตั๋วหรือป้ายทะเบียน (มี Quick Pick รถที่จอดอยู่ให้คลิกเลือกได้ทันที)
   - แสดงคำนวณค่าจอดแยกตาม Pricing Strategy
   - รองรับ 3 ช่องทางชำระ: **PromptPay QR**, **Credit Card**, **เงินสด** (คำนวณเงินทอนอัตโนมัติ)
   - ออกใบเสร็จรับเงินอย่างเป็นทางการ (Official Receipt Modal)
   - รองรับปุ่ม **"ตั๋วหาย"** คิดค่าจอดตามเวลาจริงรวมค่าปรับ 300 บาท
5. **แท็บ OOP Architecture & SOLID Explorer**:
   - หน้าอธิบายหลักการ OOP และ Design Patterns ที่ใช้ในระบบเพื่อการเรียนรู้และการนำเสนอ
