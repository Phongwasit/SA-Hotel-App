// Filters only the rendered room snapshot; business updates use the existing form.
const roomBoard = document.querySelector('[data-room-board]');
if (roomBoard) {
    const cards = [...roomBoard.querySelectorAll('[data-room-number]')];
    const groups = [...roomBoard.querySelectorAll('[data-room-group]')];
    const buttons = [...roomBoard.querySelectorAll('[data-state]')];
    const search = roomBoard.querySelector('#roomBoardSearch');
    const type = roomBoard.querySelector('#roomBoardType');
    const params = new URL(window.location.href).searchParams;
    const matches = {
        all: () => true,
        ready: card => card.dataset.occupancy === 'VACANT' && card.dataset.cleanliness === 'CLEAN' && card.dataset.usability === 'USABLE',
        occupied: card => card.dataset.occupancy === 'OCCUPIED',
        dirty: card => card.dataset.cleanliness === 'DIRTY',
        blocked: card => card.dataset.usability === 'OUT_OF_ORDER'
    };
    let state = Object.hasOwn(matches, params.get('viewState')) ? params.get('viewState') : 'all';
    const allowedTypes = [...type.options].map(option => option.value);
    type.value = allowedTypes.includes(params.get('viewType')) ? params.get('viewType') : 'all';
    search.value = (params.get('roomSearch') || '').slice(0, 20);
    for (const name of Object.keys(matches)) {
        roomBoard.querySelector('[data-count="' + name + '"]').textContent = cards.filter(matches[name]).length;
    }
    function filterRooms() {
        const query = search.value.trim();
        let visible = 0;
        for (const card of cards) {
            card.hidden = !(matches[state](card) && (type.value === 'all' || card.dataset.roomType === type.value) && card.dataset.roomNumber.includes(query));
            if (!card.hidden) visible++;
            const link = new URL(card.href);
            for (const [key, value] of [['viewState', state], ['viewType', type.value], ['roomSearch', query]]) {
                if (value && value !== 'all') link.searchParams.set(key, value);
                else link.searchParams.delete(key);
            }
            card.href = link.pathname + link.search;
        }
        for (const group of groups) {
            const count = [...group.querySelectorAll('[data-room-number]')].filter(card => !card.hidden).length;
            group.hidden = count === 0;
            group.querySelector('[data-group-count]').textContent = count + ' ห้อง';
        }
        for (const button of buttons) {
            const active = button.dataset.state === state;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        }
        roomBoard.querySelector('[data-result-count]').textContent = 'แสดง ' + visible + ' จาก ' + cards.length + ' ห้อง';
        roomBoard.querySelector('.room-board-empty').hidden = visible !== 0;
        const current = new URL(window.location.href);
        for (const [key, value] of [['viewState', state], ['viewType', type.value], ['roomSearch', query]]) {
            if (value && value !== 'all') current.searchParams.set(key, value);
            else current.searchParams.delete(key);
        }
        window.history.replaceState(null, '', current.pathname + current.search + current.hash);
    }
    for (const button of buttons) button.addEventListener('click', () => { state = button.dataset.state; filterRooms(); });
    search.addEventListener('input', filterRooms);
    type.addEventListener('change', filterRooms);
    roomBoard.querySelectorAll('[data-reset-filters]').forEach(button => button.addEventListener('click', () => {
        state = 'all'; type.value = 'all'; search.value = ''; filterRooms();
    }));
    window.addEventListener('pageshow', filterRooms);
    filterRooms();
    roomBoard.querySelector('.room-board-controls').hidden = false;
}
