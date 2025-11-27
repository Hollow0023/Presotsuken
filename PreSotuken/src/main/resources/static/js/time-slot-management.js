/**
 * 新規時間帯を作成します
 */
async function createTimeSlot() {
    const storeId = parseInt(document.getElementById('currentStoreId').value, 10);
    const name = document.getElementById('newSlotName').value;
    const startTime = document.getElementById('newStartTime').value;
    const endTime = document.getElementById('newEndTime').value;

    // 入力検証
    if (!name || !startTime || !endTime) {
        alert('すべての項目を入力してください。');
        return;
    }

    try {
        const response = await fetch('/admin/time-slots', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ storeId, name, startTime, endTime })
        });

        if (response.ok) {
            location.reload();
        } else {
            alert('時間帯の作成に失敗しました。');
        }
    } catch (error) {
        console.error('Error creating time slot:', error);
        alert('エラーが発生しました。');
    }
}

/**
 * 時間帯を更新します
 * @param {HTMLElement} li リスト要素
 */
async function updateTimeSlot(li) {
    const timeSlotId = parseInt(li.dataset.slotId, 10);
    const storeId = parseInt(document.getElementById('currentStoreId').value, 10);
    const name = li.querySelector('.slot-name-input').value;
    const startTime = li.querySelector('.start-time-input').value;
    const endTime = li.querySelector('.end-time-input').value;

    // 入力検証
    if (!name || !startTime || !endTime) {
        alert('すべての項目を入力してください。');
        return;
    }

    try {
        const response = await fetch(`/admin/time-slots/${timeSlotId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ timeSlotId, storeId, name, startTime, endTime })
        });

        if (response.ok) {
            location.reload();
        } else {
            alert('時間帯の更新に失敗しました。');
        }
    } catch (error) {
        console.error('Error updating time slot:', error);
        alert('エラーが発生しました。');
    }
}

/**
 * 時間帯を削除します
 * @param {number} timeSlotId 時間帯ID
 */
async function deleteTimeSlot(timeSlotId) {
    if (!confirm('この時間帯を削除しますか？')) {
        return;
    }

    try {
        const response = await fetch(`/admin/time-slots/${timeSlotId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            location.reload();
        } else {
            alert('時間帯の削除に失敗しました。');
        }
    } catch (error) {
        console.error('Error deleting time slot:', error);
        alert('エラーが発生しました。');
    }
}
