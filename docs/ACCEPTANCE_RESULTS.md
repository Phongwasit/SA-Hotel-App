# ผลทดสอบโปรแกรมตาม Use Case

ตรวจเมื่อ 2026-11-04T16:05:02.476510+00:00 บน MySQL 8.4.13 จริง: 71 กรณีผ่าน ไม่มี failure/error/skip และ Chrome 37 จุดตรวจ (9 เดิม + 8 การจอง + 20 ครบวงจร) ใช้ฐาน hotel_frontdesk_test แยกจากฐานใช้งาน และตรวจยอด/ล้างเฉพาะ fixture ที่ตัวทดสอบสร้างแล้ว

ตารางต่อไปนี้มี 10 ตารางตาม UC เป็นรายการกรณีสำคัญที่ตรวจผ่านจริง บางกรณีตรวจข้าม UC จึงอ้างซ้ำได้ ห้ามบวกจำนวนแถวแล้วเรียกว่าเป็นจำนวนทดสอบใหม่ ไม่ใช่ผลตรวจรับจากเพื่อนหรือหลักฐานว่า SQL ต้นแบบทั้ง 79 คำสั่งรันตรงตัวครบแล้ว

หลักฐาน: [ผลรวม](verification-summary.json), [JUnit XML](../target/surefire-reports), [Chrome ครบวงจร](workflow-browser-checks.json), [Chrome การจอง](booking-browser-checks.json), [Chrome Login/UC01](browser-checks.json), [ภาพจริง](screenshots/)

## UC01 ตรวจสอบห้องว่าง

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| จำนวนห้อง ราคา และเตียงตรงข้อสรุปผู้ใช้ | ผ่าน | `inventoryMatchesConfirmedNumbersBedsAndRates` |
| ค้นซ้ำอ่านฐานใหม่แม้เงื่อนไขเดิม | ผ่าน | `everySearchReloadsDatabaseEvenWithIdenticalCriteria` |
| โหลดล้มเหลวไม่แสดงศูนย์หรือใช้ผลเก่า | ผ่าน | `failedLoadDoesNotReusePreviousResultsOrReportZeroAndHappensBeforeValidation` |

## UC02 จัดการการจอง

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| สร้างหลายห้องและอ่านกลับ | ผ่าน | `createMultipleRoomsUsesAuthenticatedCreatorAndReadbackRenders` |
| เพิ่มห้องและลดห้องโดยเก็บรายการยกเลิก | ผ่าน | `editCanIncreaseRoomsAndDecreaseByCancellingRemovedItems` |
| ยกเลิกทั้งการจองและเก็บประวัติ | ผ่าน | `cancellationCancelsAllPendingItemsAndStoresCancelHistoryWithoutDeletingRows` |
| สองคำขอไม่จองห้องเดียวกันซ้ำ | ผ่าน | `twoConcurrentRequestsForTheSameRoomCannotBothCommit` |

## UC03 ข้อมูลผู้พักและผู้รับเอกสาร

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| เพิ่มและแก้ข้อมูลผู้พักโดยคงรหัส | ผ่าน | `createAndEditGuestUsesBoundParametersAndRetainsId` |
| ผู้รับยังไม่ครบได้และคงข้อมูลเมื่อกรอกผิด | ผ่าน | `incompleteRecipientCanBeCreatedAndBadProfileKeepsEnteredValues` |
| เติมผู้รับระหว่างพักและไม่แก้ค่าเริ่มต้นหลังออก | ผ่าน | `recipientMayBeFilledDuringStayButNotAfterCheckout` |

## UC04 เช็คอิน

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| หลายห้องใช้ราคาสุทธิและบัญชีผู้รับเช็คอินจริง | ผ่าน | `multiRoomCheckinUsesFixedNetRatesAndAuthenticatedStaff` |
| เปลี่ยนเฉพาะรายการจองที่นำมาใช้ | ผ่าน | `bookedCheckinConsumesOnlyMatchedSelectedItem` |
| ห้องหนึ่งไม่พร้อมต้องย้อนกลับทั้งหมด | ผ่าน | `oneUnreadyRoomRollsBackEntireMultiRoomCheckin` |
| ก่อนเที่ยงใช้คืนก่อนหน้า | ผ่าน | `earlyMorningUsesPreviousChargedNightAndNoonUsesToday` |
| เช็คอินพร้อมกันสำเร็จได้เพียงครั้งเดียว | ผ่าน | `concurrentCheckinCanCommitOnlyOnce` |

## UC05 รับชำระเงิน

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| แบ่งจ่ายและหลายวิธีเป็นค่าห้องเต็มคืน | ผ่าน | `installmentsAndMixedMethodsAllocateWholeNightsOnly` |
| ไม่รับเงินคืนที่จ่ายไปแล้วซ้ำ | ผ่าน | `duplicateAndPreviouslyPaidNightsCannotBePaidAgain` |
| สองคำขอไม่จ่ายคืนเดียวกันซ้ำ | ผ่าน | `concurrentPaymentCannotDoublePayANight` |
| อ่านกลับล้มเหลวย้อนกลับเงินรับทุกส่วน | ผ่าน | `failedPaymentReadbackRollsBackAllMoneyRows` |

## UC06 Folio และอ้างอิงใบเสร็จ

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| ชำระครบและผู้รับครบก่อนออกเอกสาร | ผ่าน | `folioRequiresPaidChargesAndCompleteCurrentRecipient` |
| ค่าเริ่มต้นต่างกันหรือว่างต้องเลือกผู้รับเอง | ผ่าน | `mixedOrMissingDefaultsRequireExplicitRecipient` |
| หลายครั้งจ่ายรวมใบเดียวและไม่เปลี่ยนค่าเริ่มต้น stay | ผ่าน | `finalReceiptCombinesInstallmentsAndEditDoesNotChangeStayDefault` |
| ไม่เชื่อมเงินรับที่ไม่เกี่ยวข้องหรือขาดรายการ | ผ่าน | `foreignOrOmittedPaymentLinksCannotIssueReceipt` |

## UC07 เช็คเอาต์

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| ยอดค้างและการยืนยันออกจริงต้องพร้อม | ผ่าน | `checkoutRequiresPaidBalanceAndConfirmedDeparture` |
| ห้องว่างและสกปรกโดยคงงดใช้และประวัติ | ผ่าน | `checkoutMakesVacantDirtyAndPreservesOutOfOrderAndHistory` |
| อ่านกลับล้มเหลวย้อนกลับทั้ง stay และ room | ผ่าน | `failedCheckoutReadbackRollsBackBothStayAndRoom` |

## UC08 สถานะห้อง

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| ปรับสภาพโดยไม่เปลี่ยน occupancy และไม่ทำความสะอาดอัตโนมัติ | ผ่าน | `roomConditionCannotChangeOccupancyAndReopeningDoesNotCleanIt` |
| หน้าแสดงผลใช้ข้อมูลจริงผ่าน JDBC | ผ่าน | `allNewScreensRenderWithRealJdbcData` |

## UC09 รายงานการเข้าพัก

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| Arrival ใช้เวลาเข้าจริง; In-house รวมเวลาเข้าและไม่รวมเวลาออก; Expected Departure เฉพาะ ACTIVE | ผ่าน | `stayReportsUseActualArrivalAndInclusiveExclusiveInHouseBoundaries` |
| เวลาอ้างอิงอนาคตและโหลดล้มเหลวไม่สร้างตัวอย่างผิด | ผ่าน | `reportFailuresNeverRenderZeroOrStalePreview` |

## UC10 รายงานเงินสดรับจริง

| กรณีและผลที่คาดหวัง | ผลจริง | ชื่อกรณีใน JUnit XML |
|---|---|---|
| ยอดเท่ากันและหลายใบเสร็จไม่คูณเงินสด; ไม่รวมห้องที่จ่ายโอน | ผ่าน | `cashReportDoesNotMultiplyIdenticalAmountsOrReceiptLinksAndExcludesTransferRooms` |
| ไม่รวมเวลาสิ้นสุดและรายงานว่างเป็นศูนย์ได้เมื่ออ่านสำเร็จ | ผ่าน | `cashReportEndIsExclusiveAndEmptyReportIsValid` |
| ฐานข้อมูลผิดพลาดไม่แสดงยอดศูนย์ | ผ่าน | `reportFailuresNeverRenderZeroOrStalePreview` |

## ผลตรวจผ่านเบราว์เซอร์ครบวงจร

1. Staff login opens all ten working UC entries.
2. UC03 creates a separate guest profile through the real UI.
3. UC03 accepts an initially incomplete organization, then updates the same profile.
4. UC01 and UC02 reserve two confirmed Standard rooms for two nights.
5. UC04 checks in only the two selected booking items and creates nightly charges.
6. UC05 records a first installment: one full cash night and one full transfer night.
7. UC05 retains the second payment event and settles remaining full nights by cash/card.
8. UC06 combines two stays and installments in a printable Folio with signature spaces.
9. UC06 invalidates changed previews and stores one handwritten receipt reference for both payments.
10. Reprinted Folio uses the latest shared recipient details and the receipt recipient.
11. UC09 Arrival uses real arrival date and marks reserved arrivals.
12. UC09 In-house shows both real active arrivals at the selected Bangkok timestamp.
13. UC09 Expected Departure lists both ACTIVE rooms on the selected departure date.
14. UC10 totals actual cash once per payment and disables stale report previews.
15. UC07 ends both stays after key/document/departure confirmation without waiting for housekeeping.
16. UC08 records repair/cleaning reports; marking usable never automatically marks a room clean.
17. The complete lifecycle releases only the ended stay intervals and restores room availability.
18. Checkout and room-state changes preserve cash history and receipt references.
19. Operational screens fit a 390px viewport; large tables scroll inside their own cards.
20. Logout succeeds with no JavaScript errors or external font/CDN calls.

ภาพรายงานจริง: [In-house](screenshots/26-inhouse-report.png), [Arrival](screenshots/27-arrival-report.png), [Expected Departure](screenshots/21-expected-departures.png), [เงินสดรับจริง](screenshots/22-actual-cash-report.png) และ [Folio](screenshots/18-combined-folio.png)

ฐานใช้งานยังมี 41 ห้อง/3 ประเภท/2 บัญชี และไม่มี guest/booking/stay/payment/receipt สมมติ คีย์รหัสผ่านและไฟล์ตั้งค่าตรงกับก่อนย้าย โปรแกรมเปิดจริงที่ http://127.0.0.1:8080
