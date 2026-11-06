// Refresh room state in place; business updates still use the existing form.
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
    const setText = (element, value) => { if (element.textContent !== String(value)) element.textContent = value; };
    function filterRooms() {
        for (const name of Object.keys(matches)) setText(roomBoard.querySelector('[data-count="' + name + '"]'), cards.filter(matches[name]).length);
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
            setText(group.querySelector('[data-group-count]'), count + ' ห้อง');
        }
        for (const button of buttons) {
            const active = button.dataset.state === state;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        }
        setText(roomBoard.querySelector('[data-result-count]'), 'แสดง ' + visible + ' จาก ' + cards.length + ' ห้อง');
        roomBoard.querySelector('.room-board-empty').hidden = visible !== 0;
        const current = new URL(window.location.href);
        for (const [key, value] of [['viewState', state], ['viewType', type.value], ['roomSearch', query]]) {
            if (value && value !== 'all') current.searchParams.set(key, value);
            else current.searchParams.delete(key);
        }
        window.history.replaceState(null, '', current.pathname + current.search + current.hash);
        const refresh = document.getElementById('refresh-room-status');
        if (refresh) {
            const target = new URL(current);
            target.searchParams.delete('saved');
            target.hash = '';
            refresh.href = target.pathname + target.search;
        }
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

    const refresh = document.getElementById('refresh-room-status');
    const message = document.getElementById('room-refresh-message');
    const status = document.getElementById('room-refresh-status');
    const form = document.getElementById('room-status-form');
    const formUpdate = document.getElementById('room-form-update');
    const byId = new Map(cards.map(card => [Number(card.dataset.roomId), card]));
    const dataUrl = new URL(roomBoard.dataset.roomStateUrl, window.location.href);
    const labels = { VACANT: 'ว่าง', OCCUPIED: 'มีผู้พัก', CLEAN: 'สะอาด', DIRTY: 'รอทำความสะอาด', USABLE: 'ใช้งานได้', OUT_OF_ORDER: 'งดใช้ / รอซ่อม' };
    const draft = () => form ? [form.elements.cleanliness.value, form.elements.usability.value, form.elements.reason.value] : [];
    let baseline = JSON.stringify(draft()), latestSelected;
    function updateForm(room) {
        latestSelected = room;
        setText(document.querySelector('.selected-room-heading .muted'), room.roomType + ' · ' + labels[room.occupancy]);
        const latest = [room.cleanliness, room.usability, room.conditionReason || ''];
        if (JSON.stringify(draft()) === baseline && !form.contains(document.activeElement)) {
            [form.elements.cleanliness.value, form.elements.usability.value, form.elements.reason.value] = latest;
            baseline = JSON.stringify(latest);
        }
        formUpdate.hidden = JSON.stringify(latest) === baseline;
        if (!formUpdate.hidden) setText(formUpdate, 'ข้อมูลล่าสุด: ' + labels[room.cleanliness] + ' · ' + labels[room.usability] + (room.conditionReason ? ' · ' + room.conditionReason : '') + ' — เก็บค่าที่กำลังกรอกไว้แล้ว กรุณาตรวจสอบก่อนบันทึก');
    }
    if (form) form.addEventListener('focusout', () => setTimeout(() => { if (latestSelected) updateForm(latestSelected); }, 0));
    function applySnapshot(rows) {
        const seen = new Set();
        // Validate the full response before changing any displayed room or count.
        if (!Array.isArray(rows) || rows.length !== cards.length || !rows.every(room => {
            const card = room && byId.get(room.roomId);
            if (!card || seen.has(room.roomId) || room.roomNumber !== card.dataset.roomNumber || room.roomType !== card.dataset.roomType ||
                !['VACANT', 'OCCUPIED'].includes(room.occupancy) || !['CLEAN', 'DIRTY'].includes(room.cleanliness) || !['USABLE', 'OUT_OF_ORDER'].includes(room.usability) ||
                !(room.conditionReason === null || typeof room.conditionReason === 'string')) return false;
            seen.add(room.roomId); return true;
        })) throw new Error('Incomplete room snapshot');
        for (const room of rows) {
            const card = byId.get(room.roomId);
            for (const name of ['occupancy', 'cleanliness', 'usability']) card.dataset[name] = room[name];
            const primary = room.usability === 'OUT_OF_ORDER' ? 'blocked' : room.occupancy === 'OCCUPIED' ? 'occupied' : room.cleanliness === 'DIRTY' ? 'dirty' : 'ready';
            for (const name of ['ready', 'occupied', 'dirty', 'blocked']) card.classList.toggle('room-' + name, primary === name);
            setText(card.querySelector('.room-primary-state'), { ready: 'พร้อมเข้าพักตอนนี้', occupied: 'มีผู้พักตอนนี้', dirty: labels.DIRTY, blocked: labels.OUT_OF_ORDER }[primary]);
            const details = card.querySelectorAll('.room-state-details .condition');
            [['occupancy', 'condition-occupied', 'OCCUPIED'], ['cleanliness', 'condition-dirty', 'DIRTY'], ['usability', 'condition-cancelled', 'OUT_OF_ORDER']].forEach(([name, style, value], index) => {
                setText(details[index], labels[room[name]]); details[index].classList.toggle(style, room[name] === value);
            });
            let reason = card.querySelector('.room-condition-reason');
            if (room.conditionReason) {
                if (!reason) { reason = document.createElement('small'); reason.className = 'room-condition-reason'; card.append(reason); }
                setText(reason, room.conditionReason);
            } else if (reason) reason.remove();
            if (form && Number(form.elements.roomId.value) === room.roomId) updateForm(room);
        }
        filterRooms();
    }
    let timer, inFlight = false, sessionExpired = false, controller;
    function schedule() {
        clearTimeout(timer);
        if (!document.hidden && !sessionExpired) timer = setTimeout(() => refreshData(false), 10000);
    }
    async function refreshData(manual) {
        if (inFlight || sessionExpired) return;
        clearTimeout(timer); inFlight = true;
        refresh.setAttribute('aria-busy', 'true');
        controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 8000);
        try {
            const response = await fetch(dataUrl, { headers: { Accept: 'application/json' }, credentials: 'same-origin', cache: 'no-store', signal: controller.signal });
            if ([401, 403].includes(response.status) || (response.redirected && new URL(response.url).pathname !== dataUrl.pathname)) {
                sessionExpired = true; throw new Error('Session expired');
            }
            if (!response.ok) throw new Error('Load failed');
            applySnapshot(await response.json());
            message.hidden = true;
            setText(status, 'อัปเดตล่าสุด ' + new Date().toLocaleTimeString('th-TH', { timeZone: 'Asia/Bangkok', hour12: false }) + ' · อัปเดตอัตโนมัติทุก 10 วินาที');
            if (manual) {
                document.getElementById('room-saved-notice')?.remove();
                const current = new URL(window.location.href); current.searchParams.delete('saved');
                window.history.replaceState(null, '', current.pathname + current.search + current.hash);
            }
        } catch (error) {
            message.hidden = false;
            setText(message, sessionExpired ? 'หมดเวลาการใช้งาน เก็บค่าที่กำลังกรอกไว้แล้ว กรุณาจดค่าก่อนกดรีเฟรชเพื่อเลือกพนักงานใหม่' : 'ยังอัปเดตข้อมูลไม่ได้ กำลังแสดงข้อมูลครั้งล่าสุด ระบบจะลองใหม่ หรือกดรีเฟรชข้อมูลได้');
        } finally {
            clearTimeout(timeout); inFlight = false; refresh.removeAttribute('aria-busy'); schedule();
        }
    }
    refresh.addEventListener('click', event => { if (!sessionExpired) { event.preventDefault(); refreshData(true); } });
    document.addEventListener('visibilitychange', () => { clearTimeout(timer); if (!document.hidden) refreshData(false); });
    window.addEventListener('pagehide', () => { clearTimeout(timer); controller?.abort(); });
    window.addEventListener('pageshow', event => { if (event.persisted) refreshData(false); });
    schedule();
}
