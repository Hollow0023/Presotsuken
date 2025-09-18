// script.js

// 純粋なヘルパー関数はここでまとめて定義して依存関係を明確化する
function getStoreIdFromCookie() {
    const name = "storeId=";
    const decodedCookie = decodeURIComponent(document.cookie);
    const ca = decodedCookie.split(';');
    for (let i = 0; i < ca.length; i++) {
        let c = ca[i];
        while (c.charAt(0) === ' ') {
            c = c.substring(1);
        }
        if (c.indexOf(name) === 0) {
            return parseInt(c.substring(name.length, c.length), 10);
        }
    }
    return null; // Cookieが見つからない場合
}

function updateToggleButton(button, isSoldOut) {
    if (isSoldOut) {
        button.textContent = '販売中にする';
        button.classList.remove('sold-out');
        button.classList.add('available');
    } else {
        button.textContent = '品切れにする';
        button.classList.remove('available');
        button.classList.add('sold-out');
    }
    button.dataset.isSoldOut = isSoldOut; // データ属性も更新
}

// API呼び出し関連の関数をヘルパーの次に配置する
async function fetchAndDisplayMenus({ currentStoreId, menuTableBody, selectAllCheckbox, onToggleButtonClick }) {
    try {
        // GET /api/admin/menu-sold-out?storeId={storeId} を呼び出す
        const response = await fetch(`/api/admin/menu-sold-out?storeId=${currentStoreId}`);
        if (!response.ok) {
            // エラーレスポンスの場合
            const errorText = await response.text();
            throw new Error(`メニューデータの取得に失敗しました: ${response.status} ${response.statusText} - ${errorText}`);
        }
        const menus = await response.json();

        menuTableBody.innerHTML = ''; // テーブルの中身をクリア

        menus.forEach(menu => {
            const row = menuTableBody.insertRow();
            row.dataset.menuId = menu.menuId; // 行にmenuIdをデータ属性として保持

            const checkboxCell = row.insertCell();
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.className = 'menu-checkbox'; // クラス名を追加
            checkbox.checked = false; // 初期状態は未選択
            checkboxCell.appendChild(checkbox);

            row.insertCell().textContent = menu.menuId;
            row.insertCell().textContent = menu.menuName;
            row.insertCell().textContent = `¥${menu.price}`; // 価格

            const soldOutStatusCell = row.insertCell();
            const statusSpan = document.createElement('span');
            statusSpan.textContent = menu.isSoldOut ? '品切れ中' : '販売中';
            statusSpan.style.fontWeight = 'bold';
            statusSpan.style.color = menu.isSoldOut ? '#dc3545' : '#28a745'; // 品切れなら赤、販売中なら緑
            soldOutStatusCell.appendChild(statusSpan);

            const actionCell = row.insertCell();
            const toggleButton = document.createElement('button');
            toggleButton.className = 'toggle-button';
            toggleButton.dataset.menuId = menu.menuId;
            toggleButton.dataset.isSoldOut = menu.isSoldOut; // 現在の状態をデータ属性に保持
            updateToggleButton(toggleButton, menu.isSoldOut); // ボタンの表示を更新

            toggleButton.addEventListener('click', async () => {
                const newStatus = !JSON.parse(toggleButton.dataset.isSoldOut); // 現在の表示と逆の状態
                await onToggleButtonClick(menu.menuId, newStatus);
            });
            actionCell.appendChild(toggleButton);
        });
        // 全選択チェックボックスの状態をリセット
        selectAllCheckbox.checked = false;
    } catch (error) {
        console.error('メニューデータの取得中にエラーが発生しました:', error);
        alert(`メニューデータの取得に失敗しました: ${error.message}`);
    }
}

async function updateMenuSoldOutStatus(menuId, isSoldOut) {
    try {
        // PUT /api/admin/menu-sold-out/{menuId} を呼び出す
        const response = await fetch(`/api/admin/menu-sold-out/${menuId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ isSoldOut: isSoldOut }),
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`メニューの品切れ状態更新に失敗しました: ${response.status} ${response.statusText} - ${errorText}`);
        }
        alert('メニューの品切れ状態を更新しました！');
    } catch (error) {
        console.error('メニューの品切れ状態更新中にエラーが発生しました:', error);
        alert(`メニューの品切れ状態更新に失敗しました: ${error.message}`);
    }
}

async function updateMultipleMenuSoldOutStatus(menuIds, isSoldOut, refreshCallback) {
    if (menuIds.length === 0) {
        alert('メニューが選択されていません。');
        return;
    }

    try {
        // PUT /api/admin/menu-sold-out/bulk を呼び出す
        const response = await fetch('/api/admin/menu-sold-out/bulk', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ menuIds: menuIds, isSoldOut: isSoldOut }),
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`複数メニューの品切れ状態更新に失敗しました: ${response.status} ${response.statusText} - ${errorText}`);
        }
        alert('選択したメニューの品切れ状態を一括更新しました！');
        await refreshCallback(); // 更新後に再読み込み
    } catch (error) {
        console.error('複数メニューの品切れ状態更新中にエラーが発生しました:', error);
        alert(`複数メニューの品切れ状態更新に失敗しました: ${error.message}`);
    }
}

// DOM操作とイベント登録は最後にまとめる
document.addEventListener('DOMContentLoaded', () => {
    const menuTableBody = document.querySelector('#menuTable tbody');
    const selectAllCheckbox = document.getElementById('selectAllCheckbox');
    const bulkSoldOutBtn = document.getElementById('bulkSoldOutBtn');
    const bulkAvailableBtn = document.getElementById('bulkAvailableBtn');

    const currentStoreId = getStoreIdFromCookie();

    if (!currentStoreId) {
        alert('店舗IDが取得できませんでした。ログインし直してください。');
        // 必要に応じてログインページにリダイレクト
        window.location.href = '/login';
        return;
    }

    let refreshMenus;

    const onToggleButtonClick = async (menuId, newStatus) => {
        await updateMenuSoldOutStatus(menuId, newStatus);
        await refreshMenus(); // 従来と同じ順序で再描画して最新状態を反映
    };

    refreshMenus = async () => {
        await fetchAndDisplayMenus({
            currentStoreId,
            menuTableBody,
            selectAllCheckbox,
            onToggleButtonClick,
        });
    };

    // 全選択チェックボックスのイベントリスナー
    selectAllCheckbox.addEventListener('change', (event) => {
        const isChecked = event.target.checked;
        menuTableBody.querySelectorAll('.menu-checkbox').forEach(checkbox => {
            checkbox.checked = isChecked;
        });
    });

    // 一括品切れボタンのイベントリスナー
    bulkSoldOutBtn.addEventListener('click', async () => {
        const selectedMenuIds = Array.from(menuTableBody.querySelectorAll('.menu-checkbox:checked'))
            .map(checkbox => parseInt(checkbox.closest('tr').dataset.menuId, 10));

        await updateMultipleMenuSoldOutStatus(selectedMenuIds, true, refreshMenus); // trueで品切れ
    });

    // 一括品切れ解除ボタンのイベントリスナー
    bulkAvailableBtn.addEventListener('click', async () => {
        const selectedMenuIds = Array.from(menuTableBody.querySelectorAll('.menu-checkbox:checked'))
            .map(checkbox => parseInt(checkbox.closest('tr').dataset.menuId, 10));

        await updateMultipleMenuSoldOutStatus(selectedMenuIds, false, refreshMenus); // falseで品切れ解除
    });

    // DOMContentLoaded 時の初期表示順序は変えずにメニューデータを読み込む
    refreshMenus();
});