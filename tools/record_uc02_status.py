from pathlib import Path
import json,hashlib,tempfile,xml.etree.ElementTree as E,datetime,shutil
app=Path(__file__).resolve().parents[1];project=app.parent.parent/'Project';root=app.parent
stage=Path(tempfile.gettempdir())/'hotel-uc02-status-20261104';stage.mkdir(exist_ok=True)
baseline={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file() and 'hotel-app' not in p.relative_to(root).parts}
central=root/'เนื้อหารายงาน/CHANGES_AND_REVIEW.md';readme=root/'README.md'
s=central.read_text(encoding='utf-8-sig');head,history=s.split('## 4. ประวัติการแก้ไขและที่มาของบันทึก',1)
replacements={
'มีโปรแกรม Login + UC01 บน MySQL จริงแล้ว พร้อมภาพหน้าจอและผลทดสอบ 28 กรณี + Chrome 9 กรณี':'มีโปรแกรม Login + UC01 + UC02 บน MySQL จริงแล้ว พร้อมภาพหน้าจอและผลทดสอบ 44 กรณี + Chrome 17 กรณี',
'UC02–UC10 รายงานจริงเต็มระบบ':'UC03–UC10 รายงานจริงเต็มระบบ',
'เริ่มพัฒนา Login/UC01 และฐานข้อมูลจริงแล้วตาม H22 ส่วน UC02–UC10 ยังไม่ทำ':'พัฒนา Login/UC01/UC02 กับฐานข้อมูลจริงแล้วตาม H22/H23 ส่วน UC03–UC10 ยังไม่ทำ',
'ต่อมาสร้าง MySQL จริงกับโปรแกรม Login/UC01 ใน H22':'ต่อมาสร้าง MySQL จริงกับโปรแกรม Login/UC01 ใน H22 และเพิ่ม UC02 ใน H23',
'ส่วน H22 ผ่านการทดสอบ MySQL/JDBC/MVC/Security 28 กรณีและ Chrome 9 กรณี':'ถึง H23 ผ่านการทดสอบ MySQL/JDBC/MVC/Security 44 กรณีและ Chrome 17 กรณี',
'Login/UC01 และโครงสร้างดู H22':'Login/UC01/UC02 และโครงสร้างดู H22/H23',
'ทำบางส่วนแล้ว: MySQL 16 ตาราง/83 ฟิลด์ + Login/UC01 เปิดใช้ได้; เหลือ UC02–UC10 และ R02 ก่อนลง UC02':'ทำบางส่วนแล้ว: MySQL 16 ตาราง/83 ฟิลด์ + Login/UC01/UC02 เปิดใช้ได้; เหลือ UC03–UC10 และประสานเอกสาร UC02 ตาม H23',
'Login/queryAUTH_1 และ UC01/queryUC01_1 ผ่าน MySQL/JDBC แล้ว; ส่วนคำสั่ง UC02–UC10/ธุรกรรมบันทึกยังต้องตรวจจริง SQLite เดิมไม่ใช่ผลนั้น':'Login/queryAUTH_1, UC01/queryUC01_1 และ UC02/queryUC02_1–3 ผ่าน MySQL/JDBC แล้ว รวม generated keys/IN/transaction/read-back rollback และ concurrency; เหลือ UC03–UC10 และปรับ Query Table UC02 ให้ตรงโค้ด',
'มีผลจริง Login/UC01 และภาพใน hotel-app/docs แล้ว; ยังต้องทำผล UC อื่น':'มีผลจริง Login/UC01/UC02 และภาพใน hotel-app/docs แล้ว; ยังต้องทำผล UC อื่น',
'Login/UC01 และโครงสร้างผ่าน MySQL/JDBC/MVC จริงแล้ว 28 กรณี + Chrome 9 กรณี; UC02–UC10 ยังไม่ทดสอบผ่านโปรแกรม':'Login/UC01/UC02 และโครงสร้างผ่าน MySQL/JDBC/MVC จริงแล้ว 44 กรณี + Chrome 17 กรณี; UC03–UC10 ยังไม่ทดสอบผ่านโปรแกรม และต้องปรับ Query UC02 ตาม H23',
'มีภาพ Login/UC01 จริงแล้ว ส่วนรายงาน/UC อื่นยังขาด T06':'มีภาพ Login/UC01/UC02 จริงแล้ว ส่วนรายงาน/UC อื่นยังขาด T06',
'Login/UC01 มีผลจริงตาม H22 ส่วน UC02–UC10 ยังเหลือ T05/T06':'Login/UC01/UC02 มีผลจริงตาม H22/H23 ส่วน UC03–UC10 ยังเหลือ T05/T06',
'ยังมีข้อขัดแย้ง; alt และ Query ไม่ได้ขาด':'โปรแกรม UC02 ใช้ยกเลิกทั้งหมด/ลดห้องผ่านแก้ไข และเก็บ history EDIT+CANCEL แล้ว; เอกสาร Sequence/Collaboration/Query/DD ยังต้องประสานตาม H23 ไม่ปิด R02 ทุกไฟล์',
}
for a,b in replacements.items():
 if a not in head:raise SystemExit('Missing current status anchor: '+a[:85])
 head=head.replace(a,b)
entry='| H23 | 4 พ.ย. 2569 | ต่อ UC02 หลังผู้ใช้ให้ทำต่อ | เลือกห้องจาก UC01 ไปสร้าง ค้นหา ดู แก้ไขเพิ่มลดห้อง และยกเลิกทั้งหมด เก็บภาพก่อน EDIT/CANCEL ล็อกรายการและห้องแล้วตรวจซ้ำใน transaction อ่านกลับก่อน commit คงผู้รับจองเดิม | โปรแกรม Login/UC01/UC02 ผ่าน MySQL/JDBC/MVC/Security รวม 44 กรณี + Chrome 17 กรณี รวม concurrency/rollback; UC03–UC10 ยังไม่ทำ เอกสาร UC02 ต้องประสานตามรายการด้านล่าง |\n'
history=history.replace('\n\nหัวข้อ13เดิม','\n'+entry+'\nหัวข้อ13เดิม',1)
details='''
#### H23 — UC02 ใช้งานแล้ว และสิ่งที่ต้องประสานในแบบ

- สร้างการจองหลายห้องจากผล UC01 ค้นหาด้วยรหัส/ชื่อ/โทร เปิดรายละเอียด และแก้ไขผู้จอง ห้อง คืนพักได้ การเพิ่ม/ลดอยู่ในแก้ไข ลดแล้วเปลี่ยน item เป็น CANCELLED และเก็บรายการเดิม ยกเลิกทั้งหมดใช้เมื่อไม่เข้าพักเลย ถ้ามี CHECKED_IN แล้วให้แก้เฉพาะรายการ BOOKED ที่เหลือ
- เก็บ booking_history ก่อนทั้ง EDIT และ CANCEL พร้อมข้อมูลเดิมและรหัสผู้แก้ใน JSON โดยไม่เขียนทับ staff_id ผู้รับจองเดิม พนักงานอีกคนทำต่อได้ การค้นหา/จองไม่เปลี่ยน occupancy/cleanliness/usability
- Transaction ใช้ READ_COMMITTED ล็อก booking เดิมและห้องที่เกี่ยวข้องเรียงรหัส โหลด/ตรวจห้องว่างซ้ำ แล้วเขียน–อ่านกลับ/map ก่อน commit และ applySavedBooking หลัง commit กรณีสองคำขอแย่งห้องเดียวกัน สำเร็จได้หนึ่งคำขอ อีกคำขอได้ข้อขัดแย้ง ไม่ทิ้ง booking บางส่วนเมื่อ read-back ล้มเหลว
- MySQL/JDBC/MVC/Security ผ่าน 44 กรณี (เดิม 28 + UC02 16) ไม่มี failure/error/skip Chrome ผ่าน 17 กรณี (เดิม 9 + UC02 8) ภาพ UC02 มาจาก test instance ชี้ hotel_frontdesk_test ตรวจแล้วมี item ที่ยกเลิกสามรายการกับ history EDIT/CANCEL อย่างละหนึ่ง จากนั้นลบเฉพาะรายการสมมติของตัวทดสอบ ไม่มีข้อมูลจองสมมติค้างในฐานใช้งาน
- [วิธีเปิดโปรแกรมและบัญชีทดลอง](<../hotel-app/README.md>) / [ผลตรวจและเหตุผล](<../hotel-app/docs/IMPLEMENTATION_LOG.md>) / [ภาพจากโปรแกรมจริง](<../hotel-app/docs/screenshots>) เปิดในเครื่องที่ http://127.0.0.1:8080 โปรแกรมส่วนที่เหลือ UC03–UC10 ยังไม่ทำ

**เอกสารที่ต้องปรับตามโปรแกรมก่อนจัดเล่ม (ยังค้าง ไม่ใช่เพื่อนตรวจผ่าน):**

| เปิดตรวจ | สิ่งที่ต้องประสาน |
|---|---|
| Sequence / Collaboration UC02 ขั้น 9 | เปลี่ยนข้อความ some or all rooms ให้เป็นยกเลิกการจองทั้งหมด ลดห้องให้ไป EDIT และคง CHECKED_IN ไว้ |
| Query Table / UCD / ER-with-Query | queryUC02_2 ต้องเก็บ history ทั้ง EDIT/CANCEL แก้ได้เฉพาะ BOOKED; แสดงการ lock booking/ห้องและโหลด queryUC02_1 ตรวจซ้ำใน transaction ก่อนเขียน รวม SQL เตรียมล็อกที่ใช้จริงเมื่อรวมรายการคำสั่งรอบต่อไป |
| DD_booking_history / คำอธิบาย | เปลี่ยนข้อความที่ยังบอก SQL เก็บเฉพาะ EDIT ให้ตรงกับโปรแกรมที่เก็บ EDIT และ CANCEL แล้ว |
| ผลทดสอบลงเล่ม | นำผลจริง Login/UC01/UC02 ไปจัดตาราง ไม่ถือว่าจำนวน test cases 44 เท่ากับตารางทดสอบตามเกณฑ์ ≥8 ตาราง และยังไม่มีผลรายงาน UC09/UC10 จริง |

รอบนี้เก็บ 7 ไฟล์แบบหลัก/Word/Excel ไว้ให้เพื่อนตรวจตามเดิม ไม่เขียนทับส่วนเหล่านั้นระหว่างพัฒนา จดความต่างข้างต้นไว้ให้ตามต่อได้ โปรแกรมยังมีตารางธุรกิจ 16 ตาราง/83 ฟิลด์ ไม่มี UC ธุรกิจใหม่เพิ่ม สำรอง README/MD กลางรอบนี้ไว้ใน Project/_support_from_Project_new_ver_20261101/before_uc02_implementation_20261104

'''
history=history.replace('### 4.2',details+'### 4.2',1)
(stage/'CHANGES_AND_REVIEW.md').write_text(head+'## 4. ประวัติการแก้ไขและที่มาของบันทึก'+history,encoding='utf-8')
s=readme.read_text(encoding='utf-8-sig').replace('Login/UC01 บน MySQL จริงแล้วตาม H22 ส่วน UC02–UC10 ยังไม่ทำ','Login/UC01/UC02 บน MySQL จริงแล้วตาม H22/H23 ส่วน UC03–UC10 ยังไม่ทำ').replace('คู่มือโปรแกรม Login และ UC01','คู่มือโปรแกรม Login / UC01 / UC02').replace('มีผลทดสอบ MySQL/JDBC 28 กรณีและ Chrome 9 กรณี','มีผลทดสอบ MySQL/JDBC 44 กรณีและ Chrome 17 กรณี')
(stage/'README.md').write_text(s,encoding='utf-8')
backup=project/'_support_from_Project_new_ver_20261101/before_uc02_implementation_20261104'
files=[]
for source in [central,readme]:
 rel=str(source.relative_to(root));staged=stage/source.name
 files.append({'relative':rel,'target':str(source),'stage':str(staged),'backup':str(backup/Path(rel).parent/(source.stem+'_old_version'+source.suffix)),'beforeHash':baseline[rel],'afterHash':hashlib.sha256(staged.read_bytes()).hexdigest()})
(stage/'manifest.json').write_text(json.dumps({'root':str(root),'workspace':str(project),'backup':str(backup),'baseline':baseline,'files':files},ensure_ascii=False,indent=2),encoding='utf-8')
shutil.copy2(Path(tempfile.gettempdir())/'hotel-login-uc01-status-20261104/publish.ps1',stage/'publish.ps1')
suites=[E.parse(p).getroot() for p in (app/'target/surefire-reports').glob('TEST-th.ac.hotel.*.xml')]
totals={key:sum(int(s.get(key,'0')) for s in suites) for key in ['tests','failures','errors','skipped']}
assert totals=={'tests':44,'failures':0,'errors':0,'skipped':0},totals
browser=json.loads((app/'docs/browser-checks.json').read_text(encoding='utf-8'));booking=json.loads((app/'docs/booking-browser-checks.json').read_text(encoding='utf-8'))
assert browser['passed'] and booking['passed'] and booking['testFixtureCleanedUp']
result={'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'businessTables':16,'storedFields':83,'foreignKeyRelationships':22,'rooms':41,'staffAccounts':2,'jdk':'21.0.7','springBoot':'3.5.16','mysqlVersion':'8.4.13','junit':totals,'browserChecks':len(browser['checks'])+len(booking['checks']),'browserPassed':True,'browserBookingFixtureCleanedUp':True,'implemented':['Login/Logout','Main menu','UC01','UC02'],'notImplemented':'UC03–UC10','designSynchronizationPending':'UC02 cancellation/history/transaction details; see central H23'}
(app/'docs/verification-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'stage':str(stage),'junit':totals,'chrome':result['browserChecks'],'designFilesUnmodified':True}))
