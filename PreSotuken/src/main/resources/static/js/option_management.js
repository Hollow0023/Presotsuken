/**
 * オプション管理画面用JavaScript
 * オプショングループとオプションアイテムの管理機能を提供
 */

// =============================================================================
// グローバル変数とDOM要素の取得
// =============================================================================

/** @type {string} 現在の店舗ID */
let currentStoreId;

/** @type {HTMLElement} メッセージ表示エリア */
let messageArea;

/** @type {HTMLElement} オプショングループのテーブルボディ */
let optionGroupsTableBody;

/** @type {HTMLInputElement} 新規グループ名入力フィールド */
let newGroupNameInput;

// =============================================================================
// ユーティリティ関数
// =============================================================================

/**
 * メッセージを表示する関数
 * @param {string} message - 表示するメッセージ
 * @param {string} type - メッセージタイプ（success, error）
 */
const showMessage = (message, type = 'success') => {
    messageArea.textContent = message;
    messageArea.className = type + '-message';
    messageArea.style.display = 'block';
    setTimeout(() => {
        messageArea.style.display = 'none';
    }, 3000);
};

/**
 * DOM要素の表示/非表示を切り替えるヘルパー関数
 * @param {HTMLElement} element - 対象の要素
 * @param {boolean} show - 表示するかどうか
 */
const toggleDisplay = (element, show) => {
    element.style.display = show ? 'inline-block' : 'none';
};

// =============================================================================
// オプショングループ管理関数
// =============================================================================

/**
 * 新規オプショングループを作成する関数
 */
window.createNewOptionGroup = async () => {
    const groupName = newGroupNameInput.value.trim();
    if (!groupName) {
        showMessage('グループ名を入力してください。', 'error');
        return;
    }

    try {
        const response = await fetch('/options/groups', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ 
                groupName: groupName, 
                storeId: parseInt(currentStoreId) 
            }),
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || 'オプショングループの作成に失敗しました。');
        }

        const newGroup = await response.json();
        showMessage('オプショングループを作成しました！', 'success');
        newGroupNameInput.value = '';
        // ページをリロードして新しいグループを表示
            location.reload(); 
            // もしくは、DOMに直接新しい行を追加する処理を実装することも可能だが、リロードがシンプル
        } catch (error) {
            console.error('Error creating option group:', error);
            showMessage('オプショングループの作成中にエラーが発生しました: ' + error.message, 'error');
        }
    };

    // オプショングループの編集モード切り替えと保存
    window.toggleEditGroup = (button, groupId) => {
        const row = button.closest('.group-row');
        const displaySpan = document.getElementById(`groupNameDisplay-${groupId}`);
        const inputField = document.getElementById(`groupNameInput-${groupId}`);

        if (button.dataset.mode === 'display') {
            // 編集モードへ
            toggleDisplay(displaySpan, false);
            toggleDisplay(inputField, true);
            button.textContent = '保存';
            button.classList.remove('primary');
            button.classList.add('secondary'); // 色を変化させるなら
            button.dataset.mode = 'edit';
            // 削除ボタンも表示・非表示を切り替えるなら
            row.querySelector('.delete-group-btn').style.display = 'none';
        } else {
            // 保存処理 (API呼び出し)
            const newGroupName = inputField.value.trim();
            if (!newGroupName) {
                showMessage('グループ名を入力してください。', 'error');
                return;
            }
            
            fetch(`/options/groups/${groupId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ optionGroupId: groupId, groupName: newGroupName, storeId: parseInt(currentStoreId) }), // storeIdも一緒に送る
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('オプショングループの更新に失敗しました。');
                }
                return response.json();
            })
            .then(updatedGroup => {
                displaySpan.textContent = updatedGroup.groupName; // 表示を更新
                showMessage('オプショングループを更新しました！', 'success');
                // 表示モードへ戻す
                toggleDisplay(displaySpan, true);
                toggleDisplay(inputField, false);
                button.textContent = '編集';
                button.classList.remove('secondary');
                button.classList.add('primary');
                button.dataset.mode = 'display';
                row.querySelector('.delete-group-btn').style.display = 'inline-block';
            })
            .catch(error => {
                console.error('Error updating option group:', error);
                showMessage('オプショングループの更新中にエラーが発生しました: ' + error.message, 'error');
            });
        }
    };

    // オプショングループの削除
    window.deleteOptionGroup = async (groupId) => {
        if (!confirm('このオプショングループと、その中の全てのアイテムを本当に削除しますか？')) {
            return;
        }

        try {
            const response = await fetch(`/options/groups/${groupId}`, {
                method: 'DELETE',
            });

            if (!response.ok) {
                throw new Error('オプショングループの削除に失敗しました。');
            }

            // 成功したらDOMから行を削除
            document.querySelector(`tr[data-group-id="${groupId}"]`).remove();
            showMessage('オプショングループを削除しました！', 'success');
        } catch (error) {
            console.error('Error deleting option group:', error);
            showMessage('オプショングループの削除中にエラーが発生しました: ' + error.message, 'error');
        }
    };

    // --- オプションアイテム関連のJavaScript関数 ---

    // オプションアイテムの追加フォームの表示/非表示切り替え
    window.toggleAddItemForm = (button, groupId) => {
        const addItemForm = document.getElementById(`addItemForm-${groupId}`);
        const showAddItemBtn = document.getElementById(`showAddItemBtn-${groupId}`);
        const newItemNameInput = document.getElementById(`newItemNameInput-${groupId}`);

        if (addItemForm.style.display === 'none') {
            addItemForm.style.display = 'block';
            showAddItemBtn.style.display = 'none';
            newItemNameInput.focus(); // 入力フィールドにフォーカス
        } else {
            addItemForm.style.display = 'none';
            showAddItemBtn.style.display = 'inline-block';
            newItemNameInput.value = ''; // 入力値をクリア
        }
    };

    // 新規オプションアイテムの追加
    window.addOptionItem = async (groupId) => {
        const newItemNameInput = document.getElementById(`newItemNameInput-${groupId}`);
        const itemName = newItemNameInput.value.trim();
        if (!itemName) {
            showMessage('アイテム名を入力してください。', 'error');
            return;
        }

        try {
            const response = await fetch('/options/items', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ itemName: itemName, optionGroupId: groupId }),
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'オプションアイテムの作成に失敗しました。');
            }

            const newItem = await response.json();
            showMessage('オプションアイテムを追加しました！', 'success');
            newItemNameInput.value = ''; // 入力欄をクリア
            
            // 新しいアイテムをDOMに追加
            const itemListUl = document.getElementById(`itemList-${groupId}`);
            const li = document.createElement('li');
            li.setAttribute('data-item-id', newItem.optionItemId);
            li.classList.add('item-row');
            li.innerHTML = `
                <span id="itemNameDisplay-${newItem.optionItemId}">${newItem.itemName}</span>
                <input type="text" id="itemNameInput-${newItem.optionItemId}" value="${newItem.itemName}" style="display:none;" class="edit-input">
                <button class="secondary edit-item-btn" data-mode="display" data-item-id="${newItem.optionItemId}" onclick="toggleEditItem(this, ${newItem.optionItemId})">編集</button>
                <button class="danger delete-item-btn" data-item-id="${newItem.optionItemId}" onclick="deleteOptionItem(${newItem.optionItemId})">削除</button>
            `;
            itemListUl.appendChild(li);

            // フォームを非表示に戻す
            const addItemForm = document.getElementById(`addItemForm-${groupId}`);
            const showAddItemBtn = document.getElementById(`showAddItemBtn-${groupId}`);
            addItemForm.style.display = 'none';
            showAddItemBtn.style.display = 'inline-block';

        } catch (error) {
            console.error('Error adding option item:', error);
            showMessage('オプションアイテムの追加中にエラーが発生しました: ' + error.message, 'error');
        }
    };

    // オプションアイテムの編集モード切り替えと保存
    window.toggleEditItem = (button, itemId) => {
        const li = button.closest('.item-row');
        const displaySpan = document.getElementById(`itemNameDisplay-${itemId}`);
        const inputField = document.getElementById(`itemNameInput-${itemId}`);

        if (button.dataset.mode === 'display') {
            // 編集モードへ
            toggleDisplay(displaySpan, false);
            toggleDisplay(inputField, true);
            button.textContent = '保存';
            button.classList.remove('secondary');
            button.classList.add('primary');
            button.dataset.mode = 'edit';
            li.querySelector('.delete-item-btn').style.display = 'none';
        } else {
            // 保存処理 (API呼び出し)
            const newItemName = inputField.value.trim();
            if (!newItemName) {
                showMessage('アイテム名を入力してください。', 'error');
                return;
            }

            fetch(`/options/items/${itemId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ optionItemId: itemId, itemName: newItemName, optionGroupId: parseInt(li.closest('.group-row').dataset.groupId) }), // 所属グループIDも送る
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('オプションアイテムの更新に失敗しました。');
                }
                return response.json();
            })
            .then(updatedItem => {
                displaySpan.textContent = updatedItem.itemName; // 表示を更新
                showMessage('オプションアイテムを更新しました！', 'success');
                // 表示モードへ戻す
                toggleDisplay(displaySpan, true);
                toggleDisplay(inputField, false);
                button.textContent = '編集';
                button.classList.remove('primary');
                button.classList.add('secondary');
                button.dataset.mode = 'display';
                li.querySelector('.delete-item-btn').style.display = 'inline-block';
            })
            .catch(error => {
                console.error('Error updating option item:', error);
                showMessage('オプションアイテムの更新中にエラーが発生しました: ' + error.message, 'error');
            });
        }
    };
    
    // オプションアイテムの削除
    window.deleteOptionItem = async (itemId) => {
        if (!confirm('このオプションアイテムを本当に削除しますか？')) {
            return;
        }

        try {
            const response = await fetch(`/options/items/${itemId}`, {
                method: 'DELETE',
            });

            if (!response.ok) {
                throw new Error('オプションアイテムの削除に失敗しました。');
            }

            // 成功したらDOMから行を削除
            document.querySelector(`li[data-item-id="${itemId}"]`).remove();
            showMessage('オプションアイテムを削除しました！', 'success');
        } catch (error) {
            console.error('Error deleting option item:', error);
            showMessage('オプションアイテムの削除中にエラーが発生しました: ' + error.message, 'error');
        }
    };

    // =============================================================================
    // 初期化処理
    // =============================================================================
    
    /**
     * DOMが読み込まれた時の初期化処理
     */
    initializeOptionManagement();
}

/**
 * オプション管理画面の初期化関数
 */
function initializeOptionManagement() {
    // DOM要素の取得
    optionGroupsTableBody = document.getElementById('optionGroupsTableBody');
    newGroupNameInput = document.getElementById('newGroupNameInput');
    currentStoreId = document.getElementById('currentStoreId').value;
    messageArea = document.getElementById('messageArea');
}

// DOMコンテンツ読み込み完了時に初期化を実行
document.addEventListener('DOMContentLoaded', initializeOptionManagement);