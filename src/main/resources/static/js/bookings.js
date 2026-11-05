'use strict';
const rows = document.querySelector('#booking-rows');
document.querySelector('#add-booking-row')?.addEventListener('click', () => {
    const row = document.querySelector('#booking-row-template').content.cloneNode(true);
    for (const name of ['checkInDates', 'checkOutDates']) {
        const first = rows.querySelector(`input[name="${name}"]`);
        if (first) row.querySelector(`input[name="${name}"]`).value = first.value;
    }
    rows.append(row);
});
rows?.addEventListener('click', event => {
    if (event.target.closest('.remove-new-row')) event.target.closest('tr').remove();
});
rows?.addEventListener('change', event => {
    if (event.target.name === 'removedItemIds') event.target.closest('tr').classList.toggle('row-removed', event.target.checked);
});
