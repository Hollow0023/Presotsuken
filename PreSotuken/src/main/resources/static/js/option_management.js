
// グローバル変数として定義
let optionGroupsTableBody;
let newGroupNameInput;
let createNewGroupBtn;
let currentStoreId;
let messageArea;

// メッセージ表示関数をグローバルスコープに移動
const showMessage = (message, type = 'success') => {
    const messageArea = document.getElementById('messageArea');
    messageArea.textContent = message;
    messageArea.className = type + '-message'; // success-message or error-message
    messageArea.style.display = 'block';
    setTimeout(() => {
        messageArea.style.display = 'none';
    }, 3000); // 3秒後に消える
};

// DOM要素の表示/非表示を切り替えるヘルパーをグローバルスコープに移動
const toggleDisplay = (element, show) => {
    element.style.display = show ? 'inline-block' : 'none';
};

document.addEventListener('DOMContentLoaded', () => {
    // DOM要素を取得してグローバル変数に代入
    optionGroupsTableBody = document.getElementById('optionGroupsTableBody');
    newGroupNameInput = document.getElementById('newGroupNameInput');
    createNewGroupBtn = document.querySelector('.add-group-form .primary');
    currentStoreId = document.getElementById('currentStoreId').value;
    messageArea = document.getElementById('messageArea');

});

// --- オプショングループ関連のJavaScript関数 ---

// 新規オプショングループ作成
window.createNewOptionGroup = async () => {
    const newGroupNameInput = document.getElementById('newGroupNameInput');
    const currentStoreId = document.getElementById('currentStoreId').value;
    
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
            body: JSON.stringify({ groupName: groupName, storeId: parseInt(currentStoreId) }),
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || 'オプショングループの作成に失敗しました。');
        }

        const newGroup = await response.json();
        showMessage('オプショングループを作成しました！', 'success');
        newGroupNameInput.value = ''; // 入力欄をクリア
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
    const currentStoreId = document.getElementById('currentStoreId').value;

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
    try {
        // まず削除前チェックを行う
        const checkResponse = await fetch(`/options/groups/${groupId}/deletion-check`);
        if (!checkResponse.ok) {
            throw new Error('削除前チェックに失敗しました。');
        }
        
        const checkResult = await checkResponse.json();
        
        if (checkResult.hasLinkedMenus) {
            // 関連するメニューがある場合はモーダル表示
            showMenuLinkageModal(groupId, checkResult.linkedMenus);
        } else {
            // 関連するメニューがない場合は通常の削除確認
            showConfirmationModal(
                '削除確認',
                'このオプショングループと、その中の全てのアイテムを本当に削除しますか？',
                async () => {
                    await performOptionGroupDeletion(groupId);
                }
            );
        }
    } catch (error) {
        console.error('Error checking option group deletion:', error);
        showMessage('削除チェック中にエラーが発生しました: ' + error.message, 'error');
    }
};

// メニュー関連表示モーダルを表示
function showMenuLinkageModal(groupId, linkedMenus) {
    // モーダルHTMLを動的に作成
    const modalHtml = `
        <div id="menuLinkageModal" class="modal" style="display: flex;">
            <div class="modal-content">
                <span class="close" onclick="closeMenuLinkageModal()">&times;</span>
                <h3>メニューとの関連が見つかりました</h3>
                <p>このオプショングループは以下のメニューに紐づいています：</p>
                <ul>
                    ${linkedMenus.map(menu => `<li>${menu.menuName}</li>`).join('')}
                </ul>
                <p>削除を続けると、これらのメニューからオプションの紐づけも削除されます。</p>
                <p>本当に削除しますか？</p>
                <div class="modal-buttons">
                    <button class="primary" onclick="confirmMenuLinkageDeletion(${groupId})">削除する</button>
                    <button class="secondary" onclick="closeMenuLinkageModal()">キャンセル</button>
                </div>
            </div>
        </div>
    `;
    
    // モーダルをページに追加
    document.body.insertAdjacentHTML('beforeend', modalHtml);
}

// メニュー関連削除の確認
window.confirmMenuLinkageDeletion = async (groupId) => {
    try {
        await performOptionGroupDeletion(groupId);
        closeMenuLinkageModal();
    } catch (error) {
        console.error('Error during option group deletion:', error);
        showMessage('削除中にエラーが発生しました: ' + error.message, 'error');
    }
};

// モーダルを閉じる
window.closeMenuLinkageModal = () => {
    const modal = document.getElementById('menuLinkageModal');
    if (modal) {
        modal.remove();
    }
};

// 汎用的な確認モーダルを表示
function showConfirmationModal(title, message, onConfirm) {
    const modalHtml = `
        <div id="confirmationModal" class="modal" style="display: flex;">
            <div class="modal-content">
                <span class="close" onclick="closeConfirmationModal()">&times;</span>
                <h3>${title}</h3>
                <p>${message}</p>
                <div class="modal-buttons">
                    <button class="primary" onclick="confirmAction()">はい</button>
                    <button class="secondary" onclick="closeConfirmationModal()">キャンセル</button>
                </div>
            </div>
        </div>
    `;
    
    // 確認アクションを一時的にグローバルスコープに保存
    window.confirmationAction = onConfirm;
    
    // モーダルをページに追加
    document.body.insertAdjacentHTML('beforeend', modalHtml);
}

// 確認モーダルを閉じる
window.closeConfirmationModal = () => {
    const modal = document.getElementById('confirmationModal');
    if (modal) {
        modal.remove();
    }
    // 確認アクションをクリア
    window.confirmationAction = null;
};

// 確認アクションを実行
window.confirmAction = () => {
    if (window.confirmationAction) {
        window.confirmationAction();
    }
    closeConfirmationModal();
};

// 実際のオプショングループ削除処理
async function performOptionGroupDeletion(groupId) {
    const response = await fetch(`/options/groups/${groupId}`, {
        method: 'DELETE',
    });

    if (!response.ok) {
        throw new Error('オプショングループの削除に失敗しました。');
    }

    // 成功したらDOMから行を削除
    document.querySelector(`tr[data-group-id="${groupId}"]`).remove();
    showMessage('オプショングループを削除しました！', 'success');
}

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
    showConfirmationModal(
        '削除確認',
        'このオプションアイテムを本当に削除しますか？',
        async () => {
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
        }
    );
};

