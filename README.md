# Money-Transfer-Service

Money Transfer Service API สำหรับจัดการบัญชีและโอนเงิน ตาม spec `CODING_RULES.md` — รองรับ idempotency, ledger entry, Redis lock/cache, JMS outbox และ RFC 7807 Problem Details.

## Tech Stack

- Java 21 + Spring Boot 3.3.5
- SQL Server (Docker)
- Redis (Docker)
- IBM MQ (Docker)
- Liquibase (schema migration)
- Maven

## 1. Run from Scratch

เริ่มจากศูนย์ด้วยคำสั่งเดียว ได้ App + SQL Server + Redis + IBM MQ:

```bash
docker compose up -d --build
```

รอประมาณ 60 วินาทีให้ SQL Server + IBM MQ เริ่มต้นพร้อม healthcheck แล้ว app จะ start เอง จาก `depends_on` condition

ตรวจสอบสถานะ:

```bash
docker compose ps
```

หยุดทั้งหมด:

```bash
docker compose down
```

หรือหยุดพร้อมลบ volume:

```bash
docker compose down -v
```

## 2. Run Tests

### 2.1 Unit Tests

```bash
docker run --rm -v "$(pwd):/app" -w /app maven:3.9-eclipse-temurin-21 mvn test
```

บน Windows:

```powershell
docker run --rm -v "C:\Users\krisa\Desktop\TESTBAY\Money-Transfer-Service:/app" -w /app maven:3.9-eclipse-temurin-21 mvn test
```

### 2.2 Coverage

Coverage ยังไม่มี JaCoCo plugin ใน pom.xml ปัจจุบัน หากต้องการรายงาน coverage ให้เพิ่ม `jacoco-maven-plugin` แล้วรัน:

```bash
mvn test jacoco:report
```

## 3. API Documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI Docs: http://localhost:8080/api-docs

เข้าผ่านเบราว์เซอร์หลังจาก `docker compose up` เสร็จสมบูรณ์

## 4. Main Flow Examples (curl)

> บน Windows PowerShell ควรเก็บ JSON body ไว้ในไฟล์ `.json` แล้วใช้ `@file.json` เนื่องจาก PowerShell จะ strip quote ถ้าส่ง inline string

### 4.1 เปิดบัญชี

```bash
curl -s -D - -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Alice","initialBalance":"1000","currency":"THB"}'
```

Response:

```http
HTTP/1.1 201
Location: http://localhost:8080/api/v1/accounts/3
X-Request-Id: ...
```

```json
{"id":3,"accountNumber":"0000000003","ownerName":"Alice","balance":1000.0000,"currency":"THB","status":"ACTIVE","createdAt":"2026-07-03T06:20:34Z","updatedAt":"2026-07-03T06:20:34Z"}
```

### 4.2 ฝากเงิน

```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/{accountId}/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount":"500"}'
```

### 4.3 ถอนเงิน

```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/{accountId}/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount":"200"}'
```

### 4.4 โอนเงิน

```bash
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: transfer-001" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":"100","currency":"THB"}'
```

Response ครั้งแรก:

```http
HTTP/1.1 201
Location: /api/v1/transfers/42
```

Replay ด้วย key + payload เดิมจะได้ 200 พร้อม transfer เดิม โดยไม่หักเงินซ้ำ

### 4.5 ดู statement

```bash
curl -s http://localhost:8080/api/v1/accounts/{accountId}/transactions
```

### 4.6 ดูสถานะการโอน

```bash
curl -s http://localhost:8080/api/v1/transfers/{transferId}
```

### Postman Collection

- `postman/Money Transfer Service.postman_collection.json`

## 5. Status Table

| Requirement | Status | Notes |
|------------|--------|-------|
| Account endpoints (create/get/deposit/withdraw/status/statement) | Done | ใช้ account ID เป็น path variable |
| Transfer endpoint with Idempotency-Key | Done | Redis 24h TTL + DB fallback |
| Rate limiting | Done | 10 req/min ต่อ IP บน `POST /transfers` |
| Redis distributed lock | Done | withdraw + transfer |
| Redis account cache | Done | cache แค่ static fields; balance อ่านจาก DB |
| Transactional outbox + JMS | Done | `OutboxPoller` ส่ง IBM MQ |
| RFC 7807 error + X-Request-Id | Done | |
| Auto accountNumber 0000000001 | Done | 10-digit zero-padded |
| Location header | Done | ทั้ง create account และ create transfer |
| UTC timestamp format | Done | `createdAt`, `updatedAt`, `asOf`, `occurredAt` |
| Unit tests | Done | `IdempotencyServiceTest`, `TransferServiceTest` |
| Integration tests with Testcontainers | Not done | ยังไม่มี |
| Concurrency tests | Not done | ยังไม่มี |
| README curl examples | Done | ครบ main flow |
| Docker seed data | Not done | ยังไม่มี |

ถ้ามีเวลาเพิ่ม จะทำ:
1. Integration test กับ Testcontainers (SQL Server + Redis + IBM MQ)
2. Concurrency test (parallel transfer, idempotency race)
3. Docker seed data สำหรับ demo

## 6. Limitations & Assumptions

- **MQ auth**: ใช้การตั้งค่า permissive สำหรับ dev (`CONNAUTH(' ')` + `MCAUSER(' ')`) เนื่องจาก `mq-config.mqsc` ที่ mount ไม่รองรับ `SET AUTHREC` โดยตรง สำหรับ production ต้อง setup MQ user/group ให้ถูกต้อง
- **Rate limit**: เป็น fixed window ต่อ IP ไม่ใช่ sliding window; ถ้า Redis ล่มจะ fail-closed (คืน 429)
- **Idempotency TTL**: 24 ชั่วโมง บน Redis; DB เป็น safety net ด้วย `UNIQUE (idempotency_key)`
- **Currency**: สมมติว่าทุกบัญชีในระบบใช้สกุลเงินเดียวกัน การโอนข้ามสกุลเงินจะ reject 422
- **Balances**: เก็บเป็น `BigDecimal` scale 4 แต่ response อาจแสดงทศนิยม 4 ตำแหน่ง

## Database Migration

Liquibase migrations อยู่ใน `src/main/resources/db/changelog/`:
- `001-create-account.xml`
- `002-create-transfer.xml`
- `003-create-ledger-entry.xml`
- `004-create-outbox-event.xml`