"""Remove only the synthetic UC02 browser fixture from the dedicated test database."""
from pathlib import Path
import json, re, subprocess
root=Path(__file__).resolve().parents[1]
p=root/'docs/booking-browser-checks.json';result=json.loads(p.read_text(encoding='utf-8'))
assert result['testDatabase']=='hotel_frontdesk_test' and result['passed']
booking_id=result['bookingId'];name=result['fixtureBookerName']
assert isinstance(booking_id,int) and booking_id>0 and re.fullmatch(r'BROWSER_UC02_\d+',name)
command=[str(root/'.runtime/mysql-8.4.13-winx64/bin/mysql.exe'),f'--defaults-extra-file={root/".runtime/mysql-admin.cnf"}','--batch','--skip-column-names','--default-character-set=utf8mb4','hotel_frontdesk_test']
def run(sql):
 r=subprocess.run(command,input=sql,text=True,encoding='utf-8',capture_output=True,check=True)
 return r.stdout.strip()
assert run(f'SELECT booker_name FROM booking WHERE booking_id={booking_id};')==name
counts=run(f"SELECT (SELECT COUNT(*) FROM booking_item WHERE booking_id={booking_id}), (SELECT COUNT(*) FROM booking_item WHERE booking_id={booking_id} AND status='CANCELLED'), (SELECT COUNT(*) FROM booking_history WHERE booking_id={booking_id} AND action='EDIT'), (SELECT COUNT(*) FROM booking_history WHERE booking_id={booking_id} AND action='CANCEL');")
assert counts.split()==['3','3','1','1'],counts
run(f'START TRANSACTION; DELETE FROM booking_history WHERE booking_id={booking_id}; DELETE FROM booking_item WHERE booking_id={booking_id}; DELETE FROM booking WHERE booking_id={booking_id}; COMMIT;')
assert run(f'SELECT COUNT(*) FROM booking WHERE booking_id={booking_id};')=='0'
result.update(databaseChecks={'retainedItemsBeforeCleanup':3,'cancelledItems':3,'editHistory':1,'cancelHistory':1},testFixtureCleanedUp=True)
p.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('Verified retained items and EDIT/CANCEL history; removed only the synthetic browser fixture from hotel_frontdesk_test.')
