// スタッフ管理画面JavaScript

document.addEventListener('DOMContentLoaded', function() {
    // DOM要素の取得
    const addStaffBtn = document.getElementById('addStaffBtn');
    const staffModal = document.getElementById('staffModal');
    const closeModalBtn = document.getElementById('closeModalBtn');
    const cancelStaffBtn = document.getElementById('cancelStaffBtn');
    const staffForm = document.getElementById('staffForm');
    const modalTitle = document.getElementById('modalTitle');
    const userIdInput = document.getElementById('userId');
    const userNameInput = document.getElementById('userNameInput');
    const isAdminInput = document.getElementById('isAdminInput');
    const messageArea = document.getElementById('messageArea');
    const messageContent = document.getElementById('messageContent');
    const closeMessageBtn = document.getElementById('closeMessageBtn');

    // 初期化
    loadStaffList();

    // イベントリスナーの設定
    addStaffBtn.addEventListener('click', openAddModal);
    closeModalBtn.addEventListener('click', closeModal);
    cancelStaffBtn.addEventListener('click', closeModal);
    staffForm.addEventListener('submit', handleFormSubmit);
    closeMessageBtn.addEventListener('click', closeMessage);

    // モーダルの外側をクリックした時に閉じる
    staffModal.addEventListener('click', function(e) {
        if (e.target === staffModal) {
            closeModal();
        }
    });

    /**
     * スタッフ一覧を読み込み
     */
    function loadStaffList() {
        fetch('/admin/staff/list')
            .then(response => {
                if (!response.ok) {
                    throw new Error('スタッフ一覧の取得に失敗しました');
                }
                return response.json();
            })
            .then(data => {
                renderStaffTable(data);
            })
            .catch(error => {
                console.error('Error:', error);
                showMessage('スタッフ一覧の取得に失敗しました', 'error');
            });
    }

    /**
     * スタッフテーブルをレンダリング
     */
    function renderStaffTable(staffList) {
        const tbody = document.querySelector('#staffTable tbody');
        tbody.innerHTML = '';

        if (staffList.length === 0) {
            const row = tbody.insertRow();
            const cell = row.insertCell();
            cell.colSpan = 3;
            cell.textContent = 'スタッフが登録されていません';
            cell.style.textAlign = 'center';
            cell.style.color = '#666';
            return;
        }

        staffList.forEach(staff => {
            const row = tbody.insertRow();
            
            // スタッフ名
            const nameCell = row.insertCell();
            nameCell.textContent = staff.userName;

            // 管理者権限
            const adminCell = row.insertCell();
            if (staff.isAdmin) {
                adminCell.innerHTML = '<span class="admin-badge">管理者</span>';
            } else {
                adminCell.innerHTML = '<span class="non-admin-badge">一般</span>';
            }

            // 操作ボタン
            const actionCell = row.insertCell();
            actionCell.innerHTML = `
                <div class="action-buttons">
                    <button class="btn btn-warning btn-sm" onclick="openEditModal(${staff.userId}, '${escapeHtml(staff.userName)}', ${staff.isAdmin})">編集</button>
                    <button class="btn btn-danger btn-sm" onclick="deleteStaff(${staff.userId}, '${escapeHtml(staff.userName)}')">削除</button>
                </div>
            `;
        });
    }

    /**
     * HTMLエスケープ
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * 追加モーダルを開く
     */
    function openAddModal() {
        modalTitle.textContent = 'スタッフを追加';
        userIdInput.value = '';
        userNameInput.value = '';
        isAdminInput.checked = false;
        staffModal.classList.remove('hidden');
        userNameInput.focus();
    }

    /**
     * 編集モーダルを開く
     */
    window.openEditModal = function(userId, userName, isAdmin) {
        modalTitle.textContent = 'スタッフを編集';
        userIdInput.value = userId;
        userNameInput.value = userName;
        isAdminInput.checked = isAdmin;
        staffModal.classList.remove('hidden');
        userNameInput.focus();
    };

    /**
     * モーダルを閉じる
     */
    function closeModal() {
        staffModal.classList.add('hidden');
        staffForm.reset();
    }

    /**
     * フォーム送信処理
     */
    function handleFormSubmit(e) {
        e.preventDefault();
        
        const userId = userIdInput.value;
        const userName = userNameInput.value.trim();
        const isAdmin = isAdminInput.checked;

        if (!userName) {
            showMessage('スタッフ名を入力してください', 'error');
            return;
        }

        if (userId) {
            updateStaff(userId, userName, isAdmin);
        } else {
            addStaff(userName, isAdmin);
        }
    }

    /**
     * スタッフを追加
     */
    function addStaff(userName, isAdmin) {
        const formData = new FormData();
        formData.append('userName', userName);
        formData.append('isAdmin', isAdmin);

        fetch('/admin/staff', {
            method: 'POST',
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            if (data.message) {
                showMessage(data.message, 'success');
                closeModal();
                loadStaffList();
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showMessage('スタッフの追加に失敗しました', 'error');
        });
    }

    /**
     * スタッフを更新
     */
    function updateStaff(userId, userName, isAdmin) {
        const formData = new FormData();
        formData.append('userName', userName);
        formData.append('isAdmin', isAdmin);

        fetch(`/admin/staff/${userId}`, {
            method: 'PUT',
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            if (data.message) {
                showMessage(data.message, 'success');
                closeModal();
                loadStaffList();
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showMessage('スタッフの更新に失敗しました', 'error');
        });
    }

    /**
     * スタッフを削除
     */
    window.deleteStaff = function(userId, userName) {
        if (!confirm(`「${userName}」を削除しますか？\nこの操作は取り消せません。`)) {
            return;
        }

        fetch(`/admin/staff/${userId}`, {
            method: 'DELETE'
        })
        .then(response => response.json())
        .then(data => {
            if (data.message) {
                showMessage(data.message, 'success');
                loadStaffList();
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showMessage('スタッフの削除に失敗しました', 'error');
        });
    };

    /**
     * メッセージを表示
     */
    function showMessage(message, type = 'success') {
        messageContent.textContent = message;
        messageArea.className = 'message-area';
        if (type === 'error') {
            messageArea.classList.add('error');
        } else if (type === 'warning') {
            messageArea.classList.add('warning');
        }
        messageArea.classList.remove('hidden');

        // 5秒後に自動で閉じる
        setTimeout(() => {
            closeMessage();
        }, 5000);
    }

    /**
     * メッセージを閉じる
     */
    function closeMessage() {
        messageArea.classList.add('hidden');
    }
});