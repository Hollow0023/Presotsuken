// JavaScript separated from admin_terminals.html
// Note: This file is loaded via Thymeleaf in the template with: <script th:src="@{/js/admin_terminals.js}"></script>

// グローバル変数と初期設定
// -----------------------------------------------------------------------------
const seatId = getCookie("seatId"); 

// ヘルパー関数
// -----------------------------------------------------------------------------
function getCookie(name) {
    // Cookie文字列全体を取得
    const cookies = document.cookie.split(';'); // ; で分割して個々のCookieに
    for (let i = 0; i < cookies.length; i++) {
        let cookie = cookies[i].trim(); // 前後の空白を削除
        // Cookieが'name='で始まるか確認
        if (cookie.startsWith(name + '=')) {
            // 'name=' の部分を除いた値を取得
            const value = cookie.substring(name.length + 1);
            return decodeURIComponent(value); // デコードして返す
        }
    }
    return null; // 見つからなければnullを返す
}
function showToast(message, duration = 2000, type = 'success') {
    const toast = document.getElementById("toast");
    if (!toast) { console.error("Toast element not found."); /* alert(message); */ return; } // alertは使わない
    toast.textContent = message;
    toast.className = '';
    toast.classList.add(type);
    toast.style.display = "block";
    toast.style.opacity = "1";
    setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => { toast.style.display = "none"; }, 500);
    }, duration);
}

// ★★★ 追加: 座席IDの必須状態を更新する関数 ★★★
function updateSeatRequiredState(isAdminCheckbox, seatSelect) {
    if (isAdminCheckbox.checked) {
        seatSelect.removeAttribute('required'); // 管理者端末なら必須を解除
    } else {
        seatSelect.setAttribute('required', 'required'); // 管理者端末でなければ必須にする
    }
}


// データフェッチとDOM更新
// -----------------------------------------------------------------------------

/**
 * 座席のプルダウンをバックエンドから取得して設定する関数
 * @param {string} [selectedValue] - 初期選択したい座席ID
 * @param {HTMLElement} [targetSeatSelect] - 更新対象のselect要素 (新規追加用か編集モーダル用か)
 */
async function fetchSeatsForDropdown(selectedValue = '', targetSeatSelect = null) {
    const seatSelect = targetSeatSelect || document.getElementById('seatSelect');
    const editSeatSelect = document.getElementById('editSeatSelect'); // 編集モーダル用

    const storeId = getCookie('storeId');

    if (!storeId) { showToast('店舗IDが取得できませんでした。', 3000, 'error'); return; }

    try {
        const response = await fetch(`/admin/terminals/seats`, { headers: { 'Cookie': `storeId=${storeId}` } });
        if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
        const seats = await response.json();
        
        // ターゲットのプルダウンを更新
        seatSelect.innerHTML = '<option value="">座席を選択してください</option>';
        seats.forEach(seat => {
            const option = document.createElement('option');
            option.value = seat.seatId;
            option.textContent = seat.seatName;
            seatSelect.appendChild(option);
        });

        // 編集モーダル用のプルダウンも同時に更新
        if (editSeatSelect && targetSeatSelect !== editSeatSelect) { // 重複更新を避ける
            editSeatSelect.innerHTML = '<option value="">座席を選択してください</option>';
            seats.forEach(seat => {
                const option = document.createElement('option');
                option.value = seat.seatId;
                option.textContent = seat.seatName;
                editSeatSelect.appendChild(option);
            });
        }
        
        // 初期選択値があれば設定
        if (selectedValue) {
            seatSelect.value = selectedValue;
            if (targetSeatSelect !== editSeatSelect) { // 重複設定を避ける
                editSeatSelect.value = selectedValue;
            }
        }

    } catch (error) {
        console.error('座席の取得に失敗しました:', error);
        showToast('座席の読み込みに失敗しました。', 3000, 'error');
    }
}

/**
 * 既存の端末リストをバックエンドから取得して表示する関数
 */
async function fetchTerminals() {
    const terminalListTbody = document.querySelector('#terminalListTable tbody');
    // colspanの数を5に修正
    terminalListTbody.innerHTML = '<tr><td colspan="5" class="text-center">読み込み中...</td></tr>';
    const storeId = getCookie('storeId');

    if (!storeId) { 
        // colspanの数を5に修正
        terminalListTbody.innerHTML = '<tr><td colspan="5" class="text-center">店舗IDが取得できませんでした。</td></tr>'; 
        return; 
    }

    try {
        const response = await fetch(`/admin/terminals/list`, { headers: { 'Cookie': `storeId=${storeId}` } });
        if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
        const terminals = await response.json();

        terminalListTbody.innerHTML = '';
        if (terminals.length === 0) {
            // colspanの数を5に修正
            terminalListTbody.innerHTML = '<tr><td colspan="5" class="text-center">登録されている端末はありません。</td></tr>';
        } else {
            terminals.forEach(terminal => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${terminal.terminalId}</td>
                    <td>${terminal.seat ? terminal.seat.seatName : 'N/A'}</td>
                    <td>${terminal.ipAddress}</td>
                    <td>${terminal.admin ? 'はい' : 'いいえ'}</td>
                    <td class="action-buttons">
                        <button class="edit-btn" data-terminal-id="${terminal.terminalId}">編集</button>
                        <button class="delete-btn" data-terminal-id="${terminal.terminalId}">削除</button>
                    </td>
                `;
                terminalListTbody.appendChild(row);
            });
            // ボタンにイベントリスナーを設定
            setupActionButtons();
        }
    } catch (error) {
        console.error('端末リストの取得に失敗しました:', error);
        // colspanの数を5に修正
        terminalListTbody.innerHTML = '<tr><td colspan="5" class="text-center">端末リストの読み込みに失敗しました。</td></tr>';
        showToast('端末リストの読み込みに失敗しました。', 3000, 'error');
    }
}

/**
 * 編集・削除ボタンにイベントリスナーを設定する関数
 */
function setupActionButtons() {
    document.querySelectorAll('.edit-btn').forEach(button => {
        button.addEventListener('click', (e) => openEditModal(e.target.dataset.terminalId));
    });
    document.querySelectorAll('.delete-btn').forEach(button => {
        button.addEventListener('click', (e) => deleteTerminal(e.target.dataset.terminalId));
    });
}


// 端末追加・編集・削除処理
// -----------------------------------------------------------------------------

/**
 * 端末追加フォームの送信処理
 */
document.addEventListener('DOMContentLoaded', () => {
    // IPアドレスのプレフィックスのデフォルト値を設定（HTMLにはないけど、もしあれば）
    const ipAddressPrefixInput = document.getElementById('ipAddressPrefix');
    if (ipAddressPrefixInput) {
        if (!ipAddressPrefixInput.value) { // 既に値がなければ
            ipAddressPrefixInput.value = '/24';
        }
    }

    // ★★★ ページロード時にデータ取得関数を呼び出す ★★★
    fetchSeatsForDropdown(undefined, document.getElementById('seatSelect')); // 新規追加フォームの座席プルダウンをロード
    fetchTerminals();        // 既存端末リストをロード

    // ★★★ 追加: isAdminCheckboxとseatSelectの参照を取得 ★★★
    const isAdminCheckbox = document.getElementById('isAdminCheckbox');
    const seatSelect = document.getElementById('seatSelect');
    const ipAddressBaseInput = document.getElementById('ipAddressBase');

    // ★★★ 追加: 初回ロード時に座席IDの必須状態を更新 ★★★
    updateSeatRequiredState(isAdminCheckbox, seatSelect);

    // ★★★ 追加: チェックボックスの状態変更時に座席IDの必須状態を更新 ★★★
    isAdminCheckbox.addEventListener('change', () => {
        updateSeatRequiredState(isAdminCheckbox, seatSelect);
    });


    // 端末追加フォームの送信イベントリスナーを設定
    document.getElementById('addTerminalForm').addEventListener('submit', async (e) => {
        e.preventDefault(); // フォームのデフォルト送信を防止

        const seatId = seatSelect.value;
        const ipAddressBase = ipAddressBaseInput.value;
        const isAdmin = isAdminCheckbox.checked;

        // ★★★ クライアントサイドのバリデーションロジックを修正 ★★★
        if ((!isAdmin && !seatId) || !ipAddressBase) { // 管理者でなく、座席IDが未選択の場合 or IPアドレスが空白の場合
            let errorMessage = '';
            if (!ipAddressBase) {
                errorMessage += 'IPアドレスは必須です。';
            }
            if (!isAdmin && !seatId) {
                errorMessage += (errorMessage ? ' また、' : '') + '管理者端末でない場合は座席を選択してください。';
            }
            showToast(errorMessage, 3000, 'error');
            return;
        }

        const data = {
            seatId: isAdmin ? null : parseInt(seatId), // 管理者端末ならnull、そうでなければ座席IDを数値に変換
            ipAddress: ipAddressBase,
            admin: isAdmin
        };

        try {
            const response = await fetch('/admin/terminals', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Cookie': `storeId=${getCookie('storeId')}`
                },
                body: JSON.stringify(data)
            });

            const responseBody = await response.json();

            if (response.ok) {
                showToast(responseBody.message || '端末が追加されました。', 3000, 'success');
                document.getElementById('addTerminalForm').reset(); // フォームをリセット
                isAdminCheckbox.checked = false; // リセット後にチェックボックスの状態もリセット
                updateSeatRequiredState(isAdminCheckbox, seatSelect); // 必須状態を再適用
                fetchTerminals(); // 端末リストを再読み込み
            } else {
                showToast(responseBody.message || '端末の追加に失敗しました。', 3000, 'error');
            }
        } catch (error) {
            console.error('端末追加中にエラーが発生しました:', error);
            showToast('端末追加中にエラーが発生しました。ネットワーク接続を確認してください。', 3000, 'error');
        }
    });

    // モーダルの閉じるボタンと外側クリックのイベントリスナー設定
    document.querySelector('#editTerminalModal .close-button').addEventListener('click', closeEditModal);
    window.addEventListener('click', (event) => {
        const modal = document.getElementById('editTerminalModal');
        if (event.target == modal) {
            closeEditModal();
        }
    });

    // ★★★ 追加: editIsAdminCheckboxとeditSeatSelectの参照を取得 ★★★
    const editIsAdminCheckbox = document.getElementById('editIsAdminCheckbox');
    const editSeatSelect = document.getElementById('editSeatSelect');
    const editIpAddressBaseInput = document.getElementById('editIpAddressBase');

    // ★★★ 追加: 初回ロード時に編集モーダル用の座席IDの必須状態を更新 ★★★
    // Note: openEditModalが呼ばれたときに更新されるので、ここでは不要かもしれないが、念のため
    updateSeatRequiredState(editIsAdminCheckbox, editSeatSelect);


    // ★★★ 追加: 編集モーダル内のチェックボックスの状態変更時に座席IDの必須状態を更新 ★★★
    editIsAdminCheckbox.addEventListener('change', () => {
        updateSeatRequiredState(editIsAdminCheckbox, editSeatSelect);
    });


    // 端末編集フォームの送信イベントリスナー設定
    document.getElementById('editTerminalForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const terminalId = document.getElementById('editTerminalId').value;

        const seatId = editSeatSelect.value; // 正しい参照を使用
        const ipAddressBase = editIpAddressBaseInput.value; // 正しい参照を使用
        const isAdmin = editIsAdminCheckbox.checked;

        // ★★★ クライアントサイドのバリデーションロジックを修正 ★★★
        if ((!isAdmin && !seatId) || !ipAddressBase) { // 管理者でなく、座席IDが未選択の場合 or IPアドレスが空白の場合
            let errorMessage = '';
            if (!ipAddressBase) {
                errorMessage += 'IPアドレスは必須です。';
            }
            if (!isAdmin && !seatId) {
                errorMessage += (errorMessage ? ' また、' : '') + '管理者端末でない場合は座席を選択してください。';
            }
            showToast(errorMessage, 3000, 'error');
            return;
        }

        const data = {
            seatId: isAdmin ? null : parseInt(seatId), // 管理者端末ならnull、そうでなければ座席IDを数値に変換
            ipAddress: ipAddressBase,
            admin: isAdmin
        };

        try {
            const response = await fetch(`/admin/terminals/${terminalId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Cookie': `storeId=${getCookie('storeId')}`
                },
                body: JSON.stringify(data)
            });

            const responseBody = await response.json();

            if (response.ok) {
                showToast(responseBody.message || '端末情報が更新されました。', 3000, 'success');
                closeEditModal();
                fetchTerminals();
            } else {
                showToast(responseBody.message || '端末情報の更新に失敗しました。', 3000, 'error');
            }
        } catch (error) {
            console.error('端末更新中にエラーが発生しました:', error);
            showToast('端末更新中にエラーが発生しました。ネットワーク接続を確認してください。', 3000, 'error');
        }
    });
});

/**
 * 編集モーダルを開く関数
 * @param {number} terminalId - 編集対象の端末ID
 */
async function openEditModal(terminalId) {
    const modal = document.getElementById('editTerminalModal');
    const editTerminalIdInput = document.getElementById('editTerminalId');
    const editIpAddressBaseInput = document.getElementById('editIpAddressBase');
    const editIsAdminCheckbox = document.getElementById('editIsAdminCheckbox');

    editTerminalIdInput.value = terminalId;

    try {
        const response = await fetch(`/admin/terminals/list`, {
            headers: { 'Cookie': `storeId=${getCookie('storeId')}` }
        });
        if (!response.ok) throw new Error('Failed to fetch terminals for edit.');
        const terminals = await response.json();
        const terminalToEdit = terminals.find(t => t.terminalId == terminalId);

        if (!terminalToEdit) {
            showToast('編集対象の端末が見つかりませんでした。', 3000, 'error');
            return;
        }

        // 座席プルダウンをロードし、現在の座席を選択状態にする
        await fetchSeatsForDropdown(terminalToEdit.seat ? terminalToEdit.seat.seatId : '', document.getElementById('editSeatSelect'));

        editIpAddressBaseInput.value = terminalToEdit.ipAddress.split('/')[0] || '';
        editIsAdminCheckbox.checked = terminalToEdit.admin || false;

        // ★★★ 追加: モーダル表示時に座席IDの必須状態を更新 ★★★
        updateSeatRequiredState(editIsAdminCheckbox, document.getElementById('editSeatSelect'));

        // 座席IDがnullの場合でも、プルダウンのデフォルト値が選択されるように調整
        document.getElementById('editSeatSelect').value = terminalToEdit.seat ? terminalToEdit.seat.seatId : '';

        modal.style.display = 'flex';
    } catch (error) {
        console.error('端末編集モーダルの表示に失敗しました:', error);
        showToast('端末情報の取得に失敗しました。', 3000, 'error');
    }
}

/**
 * 編集モーダルを閉じる関数
 */
function closeEditModal() {
    document.getElementById('editTerminalModal').style.display = 'none';
}

/**
 * 端末を削除する関数
 * @param {number} terminalId - 削除対象の端末ID
 */
async function deleteTerminal(terminalId) {
    if (!confirm('本当にこの端末を削除しますか？')) {
        return;
    }

    try {
        const response = await fetch(`/admin/terminals/${terminalId}`, {
            method: 'DELETE',
            headers: {
                'Cookie': `storeId=${getCookie('storeId')}`
            }
        });

        const responseBody = await response.json();

        if (response.ok) {
            showToast(responseBody.message || '端末が削除されました。', 3000, 'success');
            fetchTerminals();
        } else {
            showToast(responseBody.message || '端末の削除に失敗しました。', 3000, 'error');
        }
    } catch (error) {
        console.error('端末削除中にエラーが発生しました:', error);
        showToast('端末削除中にエラーが発生しました。ネットワーク接続を確認してください。', 3000, 'error');
    }
}
