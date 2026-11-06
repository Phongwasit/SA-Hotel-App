'use strict';
const roomSearch = document.getElementById('room-search-form');
if (roomSearch) {
    const list = document.getElementById('selected-room-list');
    const count = document.getElementById('selected-room-count');
    function selectedInputs() {
        return Array.from(roomSearch.elements).filter(input => input.name === 'roomIds' && (input.type === 'hidden' || input.checked));
    }
    function refreshSelection() {
        const inputs = new Map(selectedInputs().map(input => [input.value, input]));
        count.textContent = inputs.size;
        list.replaceChildren();
        for (const [id, input] of inputs) {
            const tag = document.createElement('span'); tag.className = 'selected-room-tag';
            const text = document.createElement('span'); text.textContent = input.dataset.roomNumber + ' · ' + input.dataset.roomType;
            const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '×'; remove.setAttribute('aria-label', 'นำห้อง ' + input.dataset.roomNumber + ' ออกจากรายการ');
            remove.addEventListener('click', () => {
                for (const field of selectedInputs().filter(field => field.value === id)) {
                    if (field.type === 'hidden') field.remove(); else field.checked = false;
                }
                refreshSelection();
            });
            tag.append(text, remove); list.append(tag);
        }
    }
    document.querySelectorAll('.select-room').forEach(input => input.addEventListener('change', refreshSelection));
    for (const name of ['checkInDate', 'checkOutDate']) {
        roomSearch.elements[name].addEventListener('change', () => {
            for (const input of selectedInputs()) { if (input.type === 'hidden') input.remove(); else input.checked = false; }
            document.querySelectorAll('.select-room').forEach(input => { input.disabled = true; });
            document.getElementById('create-from-rooms').disabled = true;
            document.getElementById('selection-date-notice').hidden = false;
            refreshSelection();
        });
    }
    refreshSelection();
}
