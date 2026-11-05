
document.querySelectorAll('[data-back]').forEach(button=>button.addEventListener('click',()=>history.back()));
document.querySelectorAll('[data-print]').forEach(button=>button.addEventListener('click',()=>window.print()));
document.querySelectorAll('.report-criteria').forEach(form=>form.addEventListener('input',()=>{document.querySelectorAll('.report-output').forEach(output=>{output.hidden=true;output.querySelectorAll('button').forEach(button=>button.disabled=true);});}));
const type=document.getElementById('reportType');
function reportFields(){if(!type)return;document.getElementById('report-date-field').hidden=type.value==='IN_HOUSE';document.getElementById('report-asof-field').hidden=type.value!=='IN_HOUSE';}
if(type){type.addEventListener('change',reportFields);reportFields();}
const payment=document.getElementById('payment-form');
if(payment){payment.addEventListener('change',()=>{const sums={CASH:0,TRANSFER:0,CARD:0};payment.querySelectorAll('[name=chargeIds]:checked').forEach(c=>{sums[payment.elements['method_'+c.value].value]+=Number(c.dataset.amount);});const total=Object.values(sums).reduce((a,b)=>a+b,0);document.getElementById('payment-selected-total').textContent='ยอดที่เลือก '+total.toLocaleString('th-TH')+' บาท · เงินสด '+sums.CASH+' / โอน '+sums.TRANSFER+' / บัตร '+sums.CARD;});}
