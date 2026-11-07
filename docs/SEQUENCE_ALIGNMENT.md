# ชื่อกระบวนการในแอปและ Sequence Diagram

ฉบับวันที่ 7 พฤศจิกายน 2569 แก้โปรแกรมตาม `Hotel_Sequence_Diagrams_Controller_Only.drawio` ทั้ง UC01–UC10 และหน้า Class Detail โดยคงไดอะแกรมต้นฉบับไว้ ชื่อเมธอดและขั้นตอนหลักที่เคยเขียนรวมกัน แยกออกเป็นงานตามชื่อในแบบแล้ว

## เริ่มอ่านโค้ดจากตรงไหน

ไฟล์ Controller อยู่ใน `src/main/java/th/ac/hotel/controller` ส่วน UI boundary ที่รับผลลัพธ์ไปเตรียมหน้าเว็บอยู่ใน `src/main/java/th/ac/hotel/ui` ข้อมูลและการอัปเดต Collection อยู่ใน `src/main/java/th/ac/hotel/model`

| UC | จุดเริ่มใน Controller | ขั้นตอนที่แยกตามแบบ |
|---|---|---|
| 01 | `RoomListController.searchAvailableRooms` | โหลดข้อมูลใหม่ → `storeRoomList` → `validateSearchCriteria` → `getRooms` → `makeSearchResult` หรือ `makeValidationResult` → `RoomListUI.displaySearchResult` |
| 02 | `BookingController.findBookings`, `submitBookingChange` | `validateRequest` → `checkAvailability` เมื่อสร้าง/แก้ไข → `saveBookingChange` → `mapToBooking` → commit → `applySavedBooking` → `makeBookingResult` |
| 03 | `GuestController.findProfiles`, `saveProfile` | `loadProfiles` → `mapToProfiles`; บันทึกผ่าน `validateProfile` → `persistProfile` → `mapToProfile` → `makeProfileResult` |
| 04 | `CheckInController.checkIn` | โหลด `CheckInContext` → `storeCheckInData` → `validateCheckIn` → `checkCheckInReadiness` → `buildStayDraft` → `saveCheckIn` → เตรียมคำสั่ง/เชื่อมรายการจอง → `mapToCheckInData` → commit → `applySavedCheckIn` → `makeCheckInResult` |
| 05 | `PaymentController.findStayBalances`, `recordPayment` | `findStayPaymentData` → `mapToStayPaymentData` → `calculateBalances`; รับเงินผ่าน `validatePayment` → `savePayment` → `mapToSavedPaymentData` → `calculateBalances` → `makeSuccessResult` หรือ `makeSaveFailureResult` |
| 06 | `DocumentController.loadDocumentContext`, `createFolioPreview`, `saveReceiptReference` | `getDocumentData` → `mapToDocumentData` → `checkDocumentContext`; Folio ผ่าน `buildFolio`; อ้างอิงใบเสร็จผ่าน `validateReceiptReference` → `persistReceiptReference` → `mapToReceiptReference` → `makeReceiptResult` |
| 07 | `CheckOutController.findCheckOutDetails`, `checkOut` | `getActiveStayCheckOutData` → `mapToCheckOutData` → `prepareCheckOutDetails`; เช็คเอาต์ผ่าน `validateCheckOut` → `saveCheckOut` → `mapToSavedCheckOutData` → commit → `applySavedCheckOut` → `makeCheckOutResult` |
| 08 | `RoomStatusController.getRoomStatus`, `updateRoomStatus` | `findRoom` → `evaluateReadiness` เมื่อห้องว่าง → `makeRoomStatusResult`; บันทึกผ่าน `validateStatusChange` → `saveRoomCondition` → `mapToRoom` → commit → `applySavedRoom` → `makeRoomStatusSaveResult` |
| 09 | `StayReportController.generateStayReport` | `validateCriteria` → `loadStayReportData` → `mapToStayReportRows` → `buildStayReport`; แยกผลตรวจเงื่อนไขและผลอ่านข้อมูลล้มเหลว |
| 10 | `CashReportController.generateCashReport` | `validateCriteria` → `loadCashReportData` → อ่านเงินสด/การพัก/ใบเสร็จเป็นสามชุด → `mapToCashReportRows` → `buildCashReport`; ไม่มีเงินสดจึงข้ามการอ่านรายละเอียด |

## ส่วนที่เว็บแอปต้องรับเพิ่ม

เมธอดที่มี `@GetMapping` หรือ `@PostMapping` รับค่าจากฟอร์มผ่าน `@RequestParam`, `RoomSearchForm` หรือ `MultiValueMap` และรับ `Model` สำหรับแสดงหน้าเว็บ ส่วน `staffId` อ่านจากพนักงานใน session ค่าที่ใช้ทำงานเป็นข้อมูลชุดเดียวกับใน Sequence แต่ลายเซ็นของเมธอดรับ HTTP มีอาร์กิวเมนต์ของ Spring MVC เพิ่มด้วย ผลลัพธ์ที่ Controller เตรียมส่งต่อให้ UI boundary ใช้แสดงหน้าหรือไปหน้ารายละเอียดหลังบันทึกสำเร็จ

`submitSearch` อยู่ใน `static/js/room-selection.js` และผูกกับเหตุการณ์ submit ของฟอร์มจริง ยังใช้การส่งฟอร์มปกติ รักษา CSRF ห้องที่เลือกข้ามประเภท และปุ่มที่ผู้ใช้กดไว้ `openPrintDialog` อยู่ใน `static/js/desk.js` เป็นตัวเปิดหน้าต่างพิมพ์ร่วมของ DocumentUI, StayReportUI และ CashReportUI รับเฉพาะ preview ที่ยังแสดงอยู่ เมื่อเปลี่ยนเงื่อนไขรายงานแล้วจะไม่พิมพ์ผลเก่า

## กฎเดิมที่ยังใช้

- UC01 อ่านฐานข้อมูลและสร้าง Collection ใหม่ทุกครั้งที่ค้นหา ไม่มี Singleton หรือรายการห้องค้างจากการโหลดครั้งก่อน
- งานบันทึกยังล็อกข้อมูล ตรวจซ้ำหลังได้ล็อก และอ่านผลกลับใน transaction เดียวกัน หากเขียน อ่านกลับ หรือ commit ล้มเหลว จะไม่อัปเดต Collection ด้วยผลที่ยังไม่สำเร็จ
- ราคาค่าห้องยังอ่านจากประเภทห้อง รับเงินเต็มห้องต่อคืน ไม่รับราคาที่ผู้ใช้ดัดแปลงจากฟอร์ม การยกเลิกยังยกเลิกทั้งการจองตามข้อจำกัดเดิม
- รายงานเงินสดนับเงินสดครั้งเดียวต่อรายการรับเงิน รายละเอียดห้องและใบเสร็จยังเลือกเฉพาะส่วนที่เกี่ยวกับเงินสด การเปลี่ยนจากอ่านรายละเอียดทีละรายการเป็นอ่านแบบกลุ่มไม่เปลี่ยนผลรายงาน
- ชื่อพนักงาน การเลือกพนักงานโดยไม่ใช้รหัสผ่าน เลขเอกสารผู้พัก หน้าจอ และการรีเฟรชสถานะห้องทุก 10 วินาทียังคงเดิม

## ผลตรวจและคำสั่งที่ใช้ซ้ำได้

1. ก่อนแก้ ตัวทดสอบชื่อเมธอดพบว่ามีขั้นตอนในแบบที่ไม่มีเมธอดตรงชื่อและคลาสเจ้าของจริง หลังแก้ตรวจผ่าน 117 เมธอดใน Controller, Collection และ UI boundary รวม 21 คลาส
2. ชุดทดสอบเดิม 110 เคสและชุดใหม่ 5 เคส ผ่านทั้งหมด **115 เคส** ครอบคลุมการจองซ้อน เช็คอินซ้ำ รับเงินซ้ำ การย้อนกลับเมื่ออ่านผลกลับล้มเหลว และการรักษาข้อมูลห้องอื่นใน Collection
3. ตัวตรวจ `javap` ตรวจโค้ดที่คอมไพล์แล้วผ่านลำดับเรียกหลัก 17 เส้นทาง ใช้คู่กับชุดทดสอบการทำงาน เพราะการมีเมธอดตรงชื่อเพียงอย่างเดียวไม่ยืนยันผลของทุกเงื่อนไข
4. Chrome ตรวจหน้าจอครบทั้ง 10 UC เลือกห้องข้ามประเภทไปหน้าสร้างการจอง ตรวจชื่อที่กรอกไม่ถูกต้อง การพิมพ์/ป้องกันพิมพ์ผลเก่า การคงแบบร่างระหว่างรีเฟรช หน้าจอคอม/มือถือ และค้นหาเมื่อปิด JavaScript

รันทดสอบการทำงานด้วย `Test-Hotel.ps1` แล้วตรวจชื่อและลำดับจากโค้ดที่คอมไพล์ด้วย:

```powershell
py -3.12 tools\verify_sequence_alignment.py
```

รายการชื่อที่ใช้ตรวจอยู่ใน `docs/sequence-method-contract.json` ไม่ต้องใช้ฐานข้อมูลสำหรับตัวตรวจชื่อและลำดับ ส่วนผลทดสอบฐานข้อมูลใช้เฉพาะ `hotel_frontdesk_test` ข้อมูลธุรกิจทั้ง 16 ตารางในฐานหลักและฐานทดสอบตรงกับก่อนเริ่มงานหลังเก็บ fixture แล้ว
