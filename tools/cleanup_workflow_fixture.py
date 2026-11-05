"""Verify and remove only the named browser workflow's fixture, from the test database."""
from pathlib import Path
import json,re,subprocess,sys

root=Path(__file__).resolve().parents[1]
p=root/'docs/workflow-browser-checks.json';result=json.loads(p.read_text(encoding='utf-8'))
assert result['testDatabase']=='hotel_frontdesk_test'
assert result['passed'] or ('--failed-after-checkout' in sys.argv and len(result['checks'])>=17)
fixture=result['fixture'];assert re.fullmatch(r'BROWSER_FLOW_\d+',fixture)
def positive(n):assert type(n) is int and n>0;return n
guest=positive(result['guestId']);recipient=positive(result['recipientId']);booking=positive(result['bookingId']);reference=positive(result['referenceId'])
stays=[positive(x) for x in result['stayIds']];payments=[positive(x) for x in result['paymentIds']];rooms=[positive(x) for x in result['roomIds']]
assert len(stays)==len(payments)==len(rooms)==2
command=[str(root/'.runtime/mysql-8.4.13-winx64/bin/mysql.exe'),f'--defaults-extra-file={root/".runtime/mysql-admin.cnf"}','--batch','--skip-column-names','--default-character-set=utf8mb4','hotel_frontdesk_test']
def run(sql):return subprocess.run(command,input=sql,text=True,encoding='utf-8',capture_output=True,check=True).stdout.strip()
assert run('SELECT DATABASE();')=='hotel_frontdesk_test'
assert run(f'SELECT first_name FROM guest WHERE guest_id={guest};')==fixture
assert run(f'SELECT recipient_name FROM document_recipient WHERE recipient_id={recipient};')==fixture+'_LATEST'
assert run(f'SELECT booker_name FROM booking WHERE booking_id={booking};')==fixture
assert run(f'SELECT book_number FROM receipt_reference WHERE reference_id={reference};')==fixture
s=','.join(map(str,stays));pids=','.join(map(str,payments));r=','.join(map(str,rooms))
assert run(f"SELECT COUNT(*) FROM stay WHERE stay_id IN ({s}) AND guest_id={guest} AND status='CHECKED_OUT';")=='2'
assert run(f'SELECT COUNT(*),SUM(amount) FROM stay_charge WHERE stay_id IN ({s});').split()==['4','3200.00']
assert run(f'SELECT COUNT(*),SUM(amount) FROM payment_allocation WHERE stay_id IN ({s});').split()==['4','3200.00']
assert run(f"SELECT SUM(amount) FROM payment_method WHERE payment_id IN ({pids}) AND method='CASH';")=='1600.00'
assert run(f'SELECT COUNT(*) FROM receipt_stay WHERE reference_id={reference};')=='2'
assert run(f'SELECT COUNT(*) FROM receipt_payment WHERE reference_id={reference};')=='2'
assert run(f"SELECT COUNT(*) FROM booking_item WHERE booking_id={booking} AND status='CHECKED_IN';")=='2'
assert run(f"SELECT COUNT(*) FROM room WHERE room_id IN ({r}) AND occupancy='VACANT' AND cleanliness='CLEAN' AND usability='USABLE';")=='2'
run(f'''START TRANSACTION;
DELETE FROM receipt_payment WHERE reference_id={reference};
DELETE FROM receipt_stay WHERE reference_id={reference};
DELETE FROM receipt_reference WHERE reference_id={reference};
DELETE FROM payment_allocation WHERE stay_id IN ({s}) AND payment_id IN ({pids});
DELETE FROM payment_method WHERE payment_id IN ({pids});
DELETE FROM payment WHERE payment_id IN ({pids});
DELETE FROM stay_charge WHERE stay_id IN ({s});
DELETE FROM stay WHERE stay_id IN ({s}) AND guest_id={guest};
DELETE FROM booking_history WHERE booking_id={booking};
DELETE FROM booking_item WHERE booking_id={booking};
DELETE FROM booking WHERE booking_id={booking};
DELETE FROM guest WHERE guest_id={guest};
DELETE FROM document_recipient WHERE recipient_id={recipient};
UPDATE room SET condition_reason=NULL WHERE room_id IN ({r}) AND condition_reason='แม่บ้านแจ้งทำความสะอาดเรียบร้อย';
COMMIT;''')
assert run(f'SELECT COUNT(*) FROM stay WHERE stay_id IN ({s});')=='0'
result['databaseChecks']={'checkedOutStays':2,'nightlyCharges':4,'totalCharges':'3200.00','paymentEvents':2,'wholeNightAllocations':4,'cashOnly':'1600.00','finalReceipt':1,'receiptStayLinks':2,'receiptPaymentLinks':2,'roomsReadyAfterCleaning':2}
result['testFixtureCleanedUp']=True
p=root/'docs/workflow-browser-checks.json';p.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Verified complete lifecycle totals/links; removed only the named workflow fixture from hotel_frontdesk_test.')
