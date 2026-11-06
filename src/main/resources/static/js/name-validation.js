'use strict';
document.querySelectorAll('[data-name-kind]').forEach(input => {
    input.addEventListener('input', () => input.setCustomValidity(''));
    input.addEventListener('invalid', () => {
        if (input.validity.patternMismatch) input.setCustomValidity(input.title);
    });
});
const recipientName = document.getElementById('recipientName');
const recipientType = document.getElementById('recipientType');
if (recipientName && recipientType) {
    function recipientRule() {
        const organization = recipientType.value === 'ORGANIZATION';
        recipientName.pattern = organization ? recipientName.dataset.organizationPattern : recipientName.dataset.personPattern;
        recipientName.title = organization ? 'ชื่อหน่วยงานต้องมีตัวอักษร ไม่ใช่ตัวเลขหรือสัญลักษณ์ล้วน' : 'ชื่อผู้รับต้องมีตัวอักษรและไม่มีตัวเลข';
        recipientName.setCustomValidity('');
    }
    recipientType.addEventListener('change', recipientRule);
    recipientRule();
}
