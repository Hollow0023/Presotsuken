let currentGroupId = null;
let isEditMode = false;
let editingSeatId = null;
let editingGroupId = null;

document.addEventListener('DOMContentLoaded', function () {
    const seatTableBody = document.querySelector('#seatTable tbody');

    // 座席グループ行のクリックイベント（グループ選択）
    document.getElementById('seatGroupTabs').addEventListener('click', (e) => {
        const row = e.target.closest('.seat-group-row');
        if (!row) return;
        
        // 編集・削除ボタンのクリックの場合はグループ選択しない
        if (e.target.classList.contains('edit-group-btn') || 
            e.target.classList.contains('delete-group-btn')) {
            return;
        }
        
        document.querySelectorAll('.seat-group-row').forEach(r => r.classList.remove('active'));
        row.classList.add('active');
        currentGroupId = row.getAttribute('data-group-id');
        loadSeats(currentGroupId);
    });

    // 座席グループ編集ボタンのクリックイベント
    document.getElementById('seatGroupTabs').addEventListener('click', (e) => {
        if (e.target.classList.contains('edit-group-btn')) {
            const row = e.target.closest('.seat-group-row');
            const groupId = row.getAttribute('data-group-id');
            const groupName = row.querySelector('.group-name').textContent;
            openGroupEditModal(groupId, groupName);
        }
    });

    // 座席グループ削除ボタンのクリックイベント
    document.getElementById('seatGroupTabs').addEventListener('click', (e) => {
        if (e.target.classList.contains('delete-group-btn')) {
            const row = e.target.closest('.seat-group-row');
            const groupId = row.getAttribute('data-group-id');
            const groupName = row.querySelector('.group-name').textContent;
            if (confirm(`「${groupName}」を削除しますか？`)) {
                fetch(`/api/seat-groups/${groupId}`, { method: 'DELETE' })
                    .then(() => location.reload());
            }
        }
    });

    // グループ追加
    document.getElementById('addGroupBtn').addEventListener('click', () => {
        openGroupAddModal();
    });

    // グループモーダル保存
    document.getElementById('saveGroupBtn').addEventListener('click', () => {
        const groupName = document.getElementById('groupNameInput').value.trim();
        if (!groupName) {
            alert('グループ名を入力してください');
            return;
        }

        if (editingGroupId) {
            // 更新
            fetch(`/api/seat-groups/${editingGroupId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ seatGroupName: groupName })
            }).then(() => location.reload());
        } else {
            // 新規追加
            const storeId = getCookie('storeId');
            fetch('/api/seat-groups', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    seatGroupName: groupName,
                    store: { storeId: parseInt(storeId) }
                })
            }).then(() => location.reload());
        }
    });

    // グループモーダルキャンセル
    document.getElementById('cancelGroupBtn').addEventListener('click', closeGroupModal);
    document.getElementById('cancelGroupBtnBottom').addEventListener('click', closeGroupModal);

    function openGroupAddModal() {
        editingGroupId = null;
        document.getElementById('groupModalTitle').textContent = 'グループを追加';
        document.getElementById('groupNameInput').value = '';
        document.getElementById('saveGroupBtn').textContent = '追加';
        document.getElementById('groupModal').classList.remove('hidden');
    }

    function openGroupEditModal(groupId, groupName) {
        editingGroupId = groupId;
        document.getElementById('groupModalTitle').textContent = 'グループを編集';
        document.getElementById('groupNameInput').value = groupName;
        document.getElementById('saveGroupBtn').textContent = '保存';
        document.getElementById('groupModal').classList.remove('hidden');
    }

    function closeGroupModal() {
        document.getElementById('groupModal').classList.add('hidden');
        document.getElementById('groupNameInput').value = '';
        editingGroupId = null;
    }

    // グループ右クリック編集・削除（後方互換性のために残す）
    document.getElementById('seatGroupTabs').addEventListener('contextmenu', (e) => {
        const row = e.target.closest('.seat-group-row');
        if (row) {
            e.preventDefault();
            const groupId = row.getAttribute('data-group-id');
            const groupName = row.querySelector('.group-name').textContent;
            const action = prompt(`操作を選択：\n1: 名前変更\n2: 削除`);
            if (action === '1') {
                const newName = prompt('新しいグループ名を入力：', groupName);
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

    const firstRow = document.querySelector('.seat-group-row');
    if (firstRow) firstRow.click();

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
        openSeatAddModal();
    });

    // モーダルキャンセル
    document.getElementById('cancelSeatBtn').addEventListener('click', closeSeatModal);
    document.getElementById('cancelSeatBtnBottom').addEventListener('click', closeSeatModal);

    function openSeatAddModal() {
        isEditMode = false;
        editingSeatId = null;
        document.getElementById('seatModalTitle').textContent = '座席を追加';
        document.getElementById('seatNameInput').value = '';
        document.getElementById('maxCapacityInput').value = 1;
        document.getElementById('saveSeatBtn').textContent = '追加';
        document.getElementById('seatModal').classList.remove('hidden');
    }

    function openSeatEditModal(seat) {
        isEditMode = true;
        editingSeatId = seat.seatId;
        document.getElementById('seatModalTitle').textContent = '座席を編集';
        document.getElementById('seatNameInput').value = seat.seatName;
        document.getElementById('maxCapacityInput').value = seat.maxCapacity;
        document.getElementById('saveSeatBtn').textContent = '更新';
        document.getElementById('seatModal').classList.remove('hidden');
    }

    function closeSeatModal() {
        document.getElementById('seatModal').classList.add('hidden');
        clearModalInputs();
    }

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
            closeSeatModal();
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
        button.textContent = '編集';
        button.className = 'btn btn-warning btn-sm';
        button.addEventListener('click', () => {
            openSeatEditModal(seat);
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
	                td.className = 'action-buttons';
	                td.appendChild(editBtn);
	                td.appendChild(deleteBtn);
	                tr.appendChild(td);
	                seatTableBody.appendChild(tr);
	            });
	        });
	}

    function createDeleteButton(seat) {
        const button = document.createElement('button');
        button.textContent = '削除';
        button.className = 'btn btn-danger btn-sm';
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


