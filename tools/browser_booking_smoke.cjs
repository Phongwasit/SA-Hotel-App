const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const puppeteer=require('C:/Users/ChatPC/AppData/Local/npm-cache/_npx/668c188756b835f3/node_modules/puppeteer');
const root=path.resolve(__dirname,'..'),base='http://127.0.0.1:8081';
const settings=JSON.parse(fs.readFileSync(path.join(root,'.runtime/local-settings.json'),'utf8').replace(/^\uFEFF/,''));
const name='BROWSER_UC02_'+Date.now(),checks=[];let bookingId=null;
(async()=>{
 const browser=await puppeteer.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--disable-gpu']});
 const page=await browser.newPage();await page.setViewport({width:1440,height:1000});const errors=[];page.on('pageerror',error=>errors.push(error.message));
 const capture=async name=>page.screenshot({path:path.join(root,'docs/screenshots',name+'.png'),fullPage:true});
 const navigate=async action=>Promise.all([page.waitForNavigation({waitUntil:'networkidle0'}),action()]);
 try {
  await page.goto(base+'/login',{waitUntil:'networkidle0'});await page.type('#username','reception01');await page.type('#password',settings.staff01Password);await navigate(()=>page.click('button[type=submit]'));
  await page.goto(base+'/rooms',{waitUntil:'networkidle0'});
  await page.$eval('#checkInDate',e=>e.value='2032-07-10');await page.$eval('#checkOutDate',e=>e.value='2032-07-12');await navigate(()=>page.click('.search-submit'));
  await page.click('tr[data-room="2401"] input.select-room');await page.click('tr[data-room="2402"] input.select-room');await navigate(()=>page.click('#create-from-rooms'));
  assert.equal((await page.$$('#booking-rows tr')).length,2);checks.push('UC01 selected rooms/dates prefill the UC02 create form.');
  await page.type('#bookerName',name);await page.type('#phone','0800000000');await capture('08-booking-create');await navigate(()=>page.click('#save-booking'));
  const match=new URL(page.url()).pathname.match(/^\/bookings\/(\d+)$/);assert.ok(match,'Create did not reach saved booking details.');bookingId=Number(match[1]);
  assert.equal((await page.$$('[data-item]')).length,2);await capture('09-booking-detail');checks.push('Create commits one booking with two rooms and renders read-back.');
  await page.goto(base+'/rooms',{waitUntil:'networkidle0'});await page.$eval('#checkInDate',e=>e.value='2032-07-10');await page.$eval('#checkOutDate',e=>e.value='2032-07-12');await navigate(()=>page.click('.search-submit'));
  assert.equal(await page.$eval('#available-count',e=>e.textContent),'25');checks.push('A new UC01 search excludes both reserved rooms.');
  await page.goto(base+'/bookings/'+bookingId+'/edit',{waitUntil:'networkidle0'});
  await page.click('#booking-rows tr:nth-child(2) input[type=checkbox]');await page.click('#add-booking-row');
  await page.select('#booking-rows tr:last-child select',await page.$eval('#booking-rows tr:last-child select',e=>Array.from(e.options).find(o=>o.textContent.startsWith('1401 ')).value));
  await capture('10-booking-edit');await navigate(()=>page.click('#save-booking'));
  assert.equal((await page.$$('[data-item]')).length,3);assert.match(await page.$eval('main',e=>e.innerText),/ยกเลิกแล้ว/);checks.push('Edit adds Deluxe 1401 and cancels removed 2402 while retaining history rows.');
  await page.goto(base+'/bookings/'+bookingId+'/cancel',{waitUntil:'networkidle0'});await capture('11-booking-cancel-confirm');await navigate(()=>page.click('#confirm-cancel'));
  assert.equal((await page.$$('[data-item]')).length,3);const statuses=await page.$$eval('[data-item] .condition',nodes=>nodes.map(e=>e.textContent));assert.deepEqual(statuses,['ยกเลิกแล้ว','ยกเลิกแล้ว','ยกเลิกแล้ว']);await capture('12-booking-cancelled');checks.push('Cancel-all cancels every remaining BOOKED room and preserves all item rows.');
  await page.goto(base+'/rooms',{waitUntil:'networkidle0'});await page.$eval('#checkInDate',e=>e.value='2032-07-10');await page.$eval('#checkOutDate',e=>e.value='2032-07-12');await navigate(()=>page.click('.search-submit'));
  assert.equal(await page.$eval('#available-count',e=>e.textContent),'27');checks.push('UC01 availability returns to 27 Standard rooms after cancellation.');
  await page.goto(base+'/bookings?searchText='+encodeURIComponent(name),{waitUntil:'networkidle0'});assert.equal((await page.$$('[data-booking]')).length,1);await capture('13-booking-search');checks.push('The cancelled booking remains searchable by the original booker.');
  assert.deepEqual(errors,[]);checks.push('No browser JavaScript errors during dynamic row addition/removal and booking flow.');
  fs.writeFileSync(path.join(root,'docs/booking-browser-checks.json'),JSON.stringify({passed:true,checks,bookingId,fixtureBookerName:name,testDatabase:'hotel_frontdesk_test',testedAt:new Date().toISOString()},null,2));
  console.log(JSON.stringify({passed:true,checks:checks.length,bookingId}));
 } catch(error) {fs.writeFileSync(path.join(root,'docs/booking-browser-checks.json'),JSON.stringify({passed:false,checks,bookingId,fixtureBookerName:name,testDatabase:'hotel_frontdesk_test',error:error.message},null,2));throw error;}
 finally {await browser.close();}
})().catch(error=>{console.error(error.message);process.exit(1)});
