# Postman Collection

## การใช้งาน

1. **Import Collection**:
   - เปิด Postman
   - ไปที่ `Import` > `File`
   - เลือกไฟล์ `Money Transfer Service.postman_collection.json`

2. **ตั้งค่า Environment Variables**:
   - `baseUrl`: http://localhost:8080 (default)
   - `testAccountNumber`: จะถูกสร้างอัตโนมัติหลังจากสร้างบัญชี
   - `testTransferId`: จะถูกสร้างอัตโนมัติหลังจากสร้างการโอนเงิน

## API Endpoints

### Account API
- **Create Account**: `POST /api/v1/accounts`
- **Get Account**: `GET /api/v1/accounts/{accountNumber}`
- **Get Balance**: `GET /api/v1/accounts/{accountNumber}/balance`

### Transfer API
- **Create Transfer**: `POST /api/v1/transfers`
- **Get Transfer**: `GET /api/v1/transfers/{id}`
- **Transfer History**: `GET /api/v1/transfers?accountNumber={accountNumber}`

## การทดสอบ

1. เริ่มจาก **Create Account** เพื่อสร้างบัญชีทดสอบ
2. ใช้ **Get Account** และ **Get Balance** เพื่อตรวจสอบข้อมูล
3. สร้างอีกบัญชีหนึ่งสำหรับทดสอบการโอนเงิน
4. ทดสอบ **Transfer API** เมื่อ implement เสร็จ

## Notes

- Collection มีการตั้งค่า Tests อัตโนมัติ
- จะเก็บค่า `testAccountNumber` และ `testTransferId` ไว้ใช้ใน request ถัดไป
- ต้องรัน application ก่อนถึงจะใช้งานได้
