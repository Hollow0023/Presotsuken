let currentGroupId = null;
let isEditMode = false;
let editingSeatId = null;

document.addEventListener('DOMContentLoaded', function () {
    const seatTableBody = document.querySelector('#seatTable tbody');

    // タブのクリックイベントは委任で拾う（安定）
    document.getElementById('seatGroupTabs').addEventListener('click', (e) => {
        if (e.target.classList.contains('seat-group-tab')) {
            document.querySelectorAll('.seat-group-tab').forEach(t => t.classList.remove('active'));
            e.target.classList.add('active');
            currentGroupId = e.target.getAttribute('data-group-id');
            loadSeats(currentGroupId);
        }
    });

    // グループ追加
    document.getElementById('addGroupBtn').addEventListener('click', () => {
        const name = prompt('新しいグループ名を入力してください');
        if (!name) return;

        const storeId = getCookie('storeId');
        fetch('/api/seat-groups', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                seatGroupName: name,
                storeId: parseInt(storeId)
            })
        }).then(() => location.reload());
    });

    // グループ右クリック編集・削除
    document.getElementById('seatGroupTabs').addEventListener('contextmenu', (e) => {
        if (e.target.classList.contains('seat-group-tab')) {
            e.preventDefault();
            const groupId = e.target.getAttribute('data-group-id');
            const action = prompt(`操作を選択：\n1: 名前変更\n2: 削除`);
            if (action === '1') {
                const newName = prompt('新しいグループ名を入力：', e.target.textContent);
                if (newName) {
                    fetch(`/api/seat-groups/${groupId}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ seatGroupName: newName })
                    }).then(() => location.reload());
                }
            } else if (action === '2') {
                if (confirm('本当に削除しますか？')) {
                    fetch(`/api/seat-groups/${groupId}`, { method: 'DELETE' })
                        .then(() => location.reload());
                }
            }
        }
    });

    const firstTab = document.querySelector('.seat-group-tab');
    if (firstTab) firstTab.click();

    function getCookie(name) {
        const match = document.cookie.match(new RegExp(name + '=([^;]+)'));
        return match ? match[1] : null;
    }

    // 座席追加モーダル表示
    document.getElementById('addSeatBtn').addEventListener('click', () => {
        if (!currentGroupId) {
            alert('まず座席グループを選択してください');
            return;
        }
        document.getElementById('seatModal').classList.remove('hidden');
    });

    // モーダルキャンセル
    document.getElementById('cancelSeatBtn').addEventListener('click', () => {
        document.getElementById('seatModal').classList.add('hidden');
        clearModalInputs();
    });

    // モーダル保存（追加・更新）
    document.getElementById('saveSeatBtn').addEventListener('click', () => {
        const seatName = document.getElementById('seatNameInput').value.trim();
        const maxCapacity = parseInt(document.getElementById('maxCapacityInput').value, 10);

        if (!seatName || isNaN(maxCapacity) || maxCapacity <= 0) {
            alert('正しい情報を入力してください');
            return;
        }
        
		const storeId = getCookie('storeId');
		console.log(storeId);
        const url = isEditMode ? '/seat/update' : '/seat/save';
        const payload = {
            seatName: seatName,
            maxCapacity: maxCapacity,
            seatGroupId: parseInt(currentGroupId),
            storeId: parseInt(storeId)
        };
        if (isEditMode) payload.seatId = editingSeatId;

        fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        }).then(() => {
            document.getElementById('seatModal').classList.add('hidden');
            clearModalInputs();
            loadSeats(currentGroupId);
        });
    });

    function clearModalInputs() {
        document.getElementById('seatNameInput').value = '';
        document.getElementById('maxCapacityInput').value = 1;
        document.getElementById('saveSeatBtn').textContent = '追加';
        isEditMode = false;
        editingSeatId = null;
    }

    function createEditButton(seat) {
        const button = document.createElement('button');
        button.textContent = 'Edit';
        button.addEventListener('click', () => {
            isEditMode = true;
            editingSeatId = seat.seatId;
            document.getElementById('seatNameInput').value = seat.seatName;
            document.getElementById('maxCapacityInput').value = seat.maxCapacity;
            document.getElementById('saveSeatBtn').textContent = '更新';
            document.getElementById('seatModal').classList.remove('hidden');
        });
        return button;
    }

	function loadSeats(groupId) {
	    fetch(`/seat/by-group/${groupId}`)
	        .then(res => res.json())
	        .then(seats => {
	            seatTableBody.innerHTML = '';
	            seats.forEach(seat => {
	                const tr = document.createElement('tr');
	                const editBtn = createEditButton(seat);
	                const deleteBtn = createDeleteButton(seat);
	
	                tr.innerHTML = `
	                    <td>${seat.seatName}</td>
	                    <td>${seat.maxCapacity}</td>
	                `;
	                const td = document.createElement('td');
	                td.appendChild(editBtn);
	                td.appendChild(deleteBtn);
	                tr.appendChild(td);
	                seatTableBody.appendChild(tr);
	            });
	        });
	}

    function createDeleteButton(seat) {
    const button = document.createElement('button');
    button.textContent = 'Delete';
    button.addEventListener('click', () => {
        if (confirm(`「${seat.seatName}」を削除しますか？`)) {
            fetch(`/seat/delete/${seat.seatId}`, {
                method: 'DELETE'
            }).then(() => {
                loadSeats(currentGroupId);
            });
        }
    });
    return button;
}

    
});


