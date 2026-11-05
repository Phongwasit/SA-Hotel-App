"""Prepare (do not silently publish) central status edits for the completed slice."""
from pathlib import Path
import json, hashlib, tempfile, xml.etree.ElementTree as E, datetime

app = Path(__file__).resolve().parents[1]
project = app.parent.parent / 'Project'
current = project.parent / 'Project_new_ver'
stage = Path(tempfile.gettempdir()) / 'hotel-login-uc01-status-20261104'
stage.mkdir(exist_ok=True)
central = current / 'เนื้อหารายงาน/CHANGES_AND_REVIEW.md'
readme = current / 'README.md'
baseline = {str(p.relative_to(current)):hashlib.sha256(p.read_bytes()).hexdigest() for p in current.rglob('*') if p.is_file() and 'hotel-app' not in p.relative_to(current).parts}
s = central.read_text(encoding='utf-8-sig')
head, history = s.split('## 4. ประวัติการแก้ไขและที่มาของบันทึก', 1)
replacements = {
'- ยังไม่มีหลักฐานโปรแกรมทำงานจริง ภาพทดสอบจริง รายงานเต็มเล่ม/สไลด์ หรือ Platform ของชุดเครื่องมือที่เลือกแล้วในชุดปัจจุบัน':'- มีโปรแกรม Login + UC01 บน MySQL จริงแล้ว พร้อมภาพหน้าจอและผลทดสอบ 28 กรณี + Chrome 9 กรณี ดู [โปรแกรมและวิธีเปิด](<../hotel-app/README.md>); UC02–UC10 รายงานจริงเต็มระบบ รายงานเต็มเล่ม/สไลด์ และภาพ Platform ยังไม่ครบ',
'ยังไม่เริ่มเขียนโปรแกรม รายละเอียดการสัมภาษณ์':'เริ่มพัฒนา Login/UC01 และฐานข้อมูลจริงแล้วตาม H22 ส่วน UC02–UC10 ยังไม่ทำ รายละเอียดการสัมภาษณ์',
'**สถานะ: ผู้ใช้อนุมัติแล้ว ปรับไฟล์แบบและตัวอย่างข้อมูลเรียบร้อย ยังไม่สร้างฐานข้อมูลหรือโปรแกรมจริง** ตรวจความสอดคล้องไฟล์และ SQLite จำลอง 19 กรณี/prepare 79 คำสั่ง ไม่ใช่ผลทดสอบ MySQL/JDBC จริง รายการแก้และเหตุผลอยู่ใน H21':'**สถานะ: ผู้ใช้อนุมัติและปรับแบบแล้ว ต่อมาสร้าง MySQL จริงกับโปรแกรม Login/UC01 ใน H22** H21 เป็นผลตรวจแบบ/SQLite 19 กรณี/prepare 79 คำสั่ง ส่วน H22 ผ่านการทดสอบ MySQL/JDBC/MVC/Security 28 กรณีและ Chrome 9 กรณี ครอบคลุมเฉพาะส่วนที่พัฒนา ไม่ใช่ผลรัน SQL ทั้ง 79 คำสั่ง',
'#### 1.6.6 กรณีที่ต้องทดสอบหลังพัฒนา — ยังไม่มีผลรันจริง':'#### 1.6.6 กรณีที่ต้องทดสอบหลังพัฒนาครบ — เงิน/เอกสารยังไม่ทำ; ผล Login/UC01 และโครงสร้างดู H22',
'Java 21/Spring Boot/JdbcTemplate/Thymeleaf/Maven และ MySQL ยังเป็นชุดที่เสนอ ดู 1.5':'ใช้จริงใน H22: Java 21.0.7 / Spring Boot 3.5.16 / JdbcTemplate / Thymeleaf / Maven 3.9.10 / MySQL 8.4.13; ยังต้องทำภาพ Platform ลงเล่ม',
'แผน 1.6 อนุมัติและปรับแบบแล้ว; ขั้นต่อไปตั้งโปรเจกต์/ฐานข้อมูลจริงและทำ Login + UC01 ยังไม่มีโปรแกรม':'ทำบางส่วนแล้ว: MySQL 16 ตาราง/83 ฟิลด์ + Login/UC01 เปิดใช้ได้; เหลือ UC02–UC10 และ R02 ก่อนลง UC02',
'SQLite จำลอง/prepare เดิมไม่ใช่ผลทดสอบนี้':'Login/queryAUTH_1 และ UC01/queryUC01_1 ผ่าน MySQL/JDBC แล้ว; ส่วนคำสั่ง UC02–UC10/ธุรกรรมบันทึกยังต้องตรวจจริง SQLite เดิมไม่ใช่ผลนั้น',
'ใช้ Test Cases ที่มีเป็นจุดเริ่ม ไม่แต่งผลจริง':'มีผลจริง Login/UC01 และภาพใน hotel-app/docs แล้ว; ยังต้องทำผล UC อื่น รายงาน 4 แบบ และจัดตารางทดสอบตามเกณฑ์ ≥8 ตาราง',
'| ยังไม่ทดสอบโปรแกรมจริง |':'| Login/UC01 และโครงสร้างผ่าน MySQL/JDBC/MVC จริงแล้ว 28 กรณี + Chrome 9 กรณี; UC02–UC10 ยังไม่ทดสอบผ่านโปรแกรม |',
'16 ตาราง/83 ฟิลด์/22 ความสัมพันธ์ FK; ยังไม่สร้าง MySQL จริง':'16 ตาราง/83 ฟิลด์/22 ความสัมพันธ์ FK; สร้างและตรวจบน MySQL 8.4.13 แล้วตาม H22',
'ขอบเขตและแบบบัญชีปรับแล้ว มี Login prerequisite/Query/Class ประกอบ ยังไม่มีโค้ด Login':'ทำ Login จริงและบัญชีทดสอบแยกสองบัญชีแล้ว สิทธิ์เท่ากัน ไม่มีหน้าจัดการบัญชี; รอเพื่อนตรวจขอบเขต',
'มีร่าง 12 หน้าหลัก/รายงาน4แบบ; หลักฐานโปรแกรมจริงยังขาด T06':'มีร่าง 12 หน้าหลัก/รายงาน4แบบ; มีภาพ Login/UC01 จริงแล้ว ส่วนรายงาน/UC อื่นยังขาด T06',
'SQL มีแล้ว; runtime/JDBC/ผลจริงยังขาด T05/T06':'SQL มีแล้ว; Login/UC01 มีผลจริงตาม H22 ส่วน UC02–UC10 ยังเหลือ T05/T06',
'| 29 | SW development platform (ขั้นต่ำ 1 รูป) | ยังไม่ทำ | ยังไม่มีส่วน Platform ของเครื่องมือที่จะใช้พัฒนาจริง | เลือกชุดเครื่องมือ/DBMS แล้วทำภาพพร้อมหน้าที่และหลักฐานการใช้ |':'| 29 | SW development platform (ขั้นต่ำ 1 รูป) | ทำบางส่วน | มีเครื่องมือที่ใช้จริงและหมายเลขเวอร์ชันใน hotel-app/README | ยังต้องทำภาพ Platform พร้อมหน้าที่ของเครื่องมือเพื่อจัดลงเล่ม |',
}
for before, after in replacements.items():
    if before not in head: raise SystemExit('Status anchor missing: ' + before[:100])
    head = head.replace(before, after)
head = head.replace('ชุดเครื่องมือที่เสนอ: Java 21 + Spring Boot + JdbcTemplate + Thymeleaf/HTML/CSS + Maven และ MySQL ยังไม่ยืนยันเวอร์ชันเฟรมเวิร์ก/DBMS ในเครื่องพบ Java 21.0.7 และ Maven 3.9.10 แล้ว แต่ยังไม่ยืนยันฐานข้อมูลพร้อมใช้งาน', 'ชุดเครื่องมือที่ใช้ในโปรแกรมรอบ H22: Java 21.0.7 + Spring Boot 3.5.16 + JdbcTemplate + Thymeleaf/HTML/CSS + Maven 3.9.10 และ MySQL 8.4.13 เปิดในเครื่องเดียวได้แล้ว ภาพ Platform สำหรับรายงานยังต้องจัดทำ')
h22 = '| H22 | 4 พ.ย. 2569 | พัฒนา Login และ UC01 บนฐานข้อมูลจริง | ตั้งเว็บแอป Java/Spring Boot/JdbcTemplate/Thymeleaf กับ MySQL ZIP ในเครื่องเดียว ใส่ 41 ห้อง/3 ประเภท บัญชีแยกสองบัญชี คง query → storeRoomList ก่อนค้นหาและโหลดใหม่ทุกรอบ ไม่มี Singleton/DatabaseController | โค้ดอยู่ Project_new_ver/hotel-app เปิด 127.0.0.1:8080; ผ่าน MySQL/JDBC/MVC/Security 28 กรณีและ Chrome 9 กรณี มีภาพ/README/สคริปต์เปิด–ปิด; ยังไม่พัฒนา UC02–UC10 ไม่ปิด R02/R19 หรือผลตรวจของเพื่อน |\n'
history = history.replace('\n\nหัวข้อ13เดิม', '\n'+h22+'\nหัวข้อ13เดิม', 1)
details = '''
#### H22 — โปรแกรมส่วนแรกที่ทำงานจริงแล้ว

- โค้ดและวิธีรัน: [hotel-app/README.md](<../hotel-app/README.md>) บันทึกเหตุผล/ปัญหา/ผลตรวจ: [IMPLEMENTATION_LOG.md](<../hotel-app/docs/IMPLEMENTATION_LOG.md>) ภาพจากโปรแกรมจริง: [screenshots](<../hotel-app/docs/screenshots>)
- มี Login/Logout เมนูหลัก และ UC01 ค้นตามประเภท วันเริ่มพัก วันออก จำนวนห้อง แสดงห้องที่พบและห้องที่ขาด ทุกครั้งอ่าน queryUC01_1 แล้ว storeRoomList ก่อน validate/search ไม่ใช้ข้อมูลเก่าเมื่อโหลดล้มเหลว
- MySQL มี 16 ตารางธุรกิจ/83 ฟิลด์/22 FK และตารางเทคนิค flyway_schema_history สำหรับเวอร์ชันฐานข้อมูล Seed 3 ประเภท/41 ห้องตามผู้ใช้ ห้องเริ่ม VACANT/CLEAN/USABLE เพื่อสาธิต ไม่ใช่ข้อมูลสภาพหน้างานจริง ไม่มีแขก/จอง/พัก/เงินจริงในฐานใช้งานเริ่มต้น
- บัญชี reception01/reception02 มีสิทธิ์เท่ากัน รหัสสุ่มแยกไว้ในไฟล์ .runtime/demo-accounts.txt ของโปรแกรม ไม่มีรหัสผ่านใน MD นี้หรือ source และการเปิดซ้ำไม่รีเซ็ตบัญชีเดิม
- ผลทดสอบ 28 กรณีใช้ MySQL/JDBC/MVC/Security จริง ฐาน hotel_frontdesk_test แยกจาก hotel_frontdesk และ rollback fixture ทุกกรณี ผล Chrome 9 กรณีครอบคลุม Login/ค้นหา/จำนวนขาด/วันที่ผิด/ออกจากระบบ/จอแคบ/ไม่เรียก CDN ตรวจภาพแล้ว ไม่มีการแต่งผลโปรแกรม
- queryAUTH_1 และ queryUC01_1 ทำงานผ่านโปรแกรมแล้ว แต่ SQL/ธุรกรรมบันทึกของ UC02–UC10 ยังไม่ทำ ผล constraint/คืนพักเช้ามืดใน fixture เป็นหลักฐานโครงสร้าง ไม่ใช่ผล UC04/UC05 ที่พัฒนาครบแล้ว
- เพิ่ม Start-Hotel/Stop-Hotel/Test-Hotel และ migration เพื่อไม่ล้างข้อมูลตอนเปิดซ้ำ คุมพอร์ตและหยุดเฉพาะ Java ของโปรเจกต์นี้ ก่อน rebuild ต้องปิดแอปเพื่อไม่ให้ JAR ถูกใช้งานอยู่บน Windows
- รอบนี้ไม่แก้ 7 ไฟล์แบบหลักหรือ workbook/Word และไม่แตะ online_old_ver ปรับเฉพาะ README/สถานะกลาง/memo ให้ตรงงานจริง สำรองชื่อ old_version ไว้ที่ Project/_support_from_Project_new_ver_20261101/before_login_uc01_20261104
- งานต่อไป: ทำข้อสรุปยกเลิกการจอง R02 ให้ตรงกันก่อนลง UC02 จากนั้นทำ UC03/UC04 และวงจรที่เหลือ ข้อ room_type 3 ประเภทกับเกณฑ์ 5 ตัวอย่างยังเปิด R19 ผลตรวจเพื่อน R01–R24 ยังรออยู่

'''
history = history.replace('### 4.2', details + '### 4.2', 1)
newcentral = head + '## 4. ประวัติการแก้ไขและที่มาของบันทึก' + history
(stage/'CHANGES_AND_REVIEW.md').write_text(newcentral,encoding='utf-8')
s = readme.read_text(encoding='utf-8-sig')
s = s.replace('ยังไม่สร้างโปรแกรมหรือทดสอบ MySQL/JDBC จริง ดู H21 และงานค้างในไฟล์กลาง', 'ต่อมาทำ Login/UC01 บน MySQL จริงแล้วตาม H22 ส่วน UC02–UC10 ยังไม่ทำ ดูงานค้างในไฟล์กลาง')
s += '\n## โปรแกรมที่เริ่มพัฒนาแล้ว\n\n[เปิดคู่มือโปรแกรม Login และ UC01](<hotel-app/README.md>) — โค้ดหลักอยู่ Project_new_ver/hotel-app เปิด http://127.0.0.1:8080 มีผลทดสอบ MySQL/JDBC 28 กรณีและ Chrome 9 กรณี ยังไม่ใช่โปรแกรมครบ 10 UC\n'
(stage/'README.md').write_text(s,encoding='utf-8')
backup = project/'_support_from_Project_new_ver_20261101/before_login_uc01_20261104'
files=[]
for source in [central,readme]:
    staged=stage/source.name
    rel=str(source.relative_to(current))
    files.append({'relative':rel,'target':str(source),'stage':str(staged),'backup':str(backup/Path(rel).parent/(source.stem+'_old_version'+source.suffix)), 'beforeHash':baseline[rel],'afterHash':hashlib.sha256(staged.read_bytes()).hexdigest()})
(stage/'manifest.json').write_text(json.dumps({'root':str(current),'workspace':str(project),'backup':str(backup),'baseline':baseline,'files':files},ensure_ascii=False,indent=2),encoding='utf-8')
suite=E.parse(app/'target/surefire-reports/TEST-th.ac.hotel.HotelIntegrationTest.xml').getroot()
browser=json.loads((app/'docs/browser-checks.json').read_text(encoding='utf-8'))
result={'testedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'mysqlVersion':'8.4.13','jdk':'21.0.7','springBoot':'3.5.16','businessTables':16,'storedFields':83,'foreignKeyRelationships':22,'rooms':41,'staffAccounts':2,'junit':{key:suite.get(key) for key in ['tests','failures','errors','skipped','time']},'browserChecks':len(browser['checks']),'browserPassed':browser['passed'],'implemented':['Staff Login/Logout','Main menu','UC01'],'notImplemented':'UC02–UC10'}
assert result['junit']['tests']=='28' and result['junit']['failures']=='0' and result['junit']['errors']=='0' and browser['passed']
(app/'docs/verification-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'statusStage':str(stage),'readyToPublish':len(files),'tests':result['junit'],'browser':result['browserChecks']},ensure_ascii=False))
