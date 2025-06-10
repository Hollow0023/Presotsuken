// グローバル変数が定義されていることを前提とする
const menuForm = document.getElementById('menuForm');
const formTitle = document.getElementById('formTitle');
const submitBtn = document.getElementById('submitBtn');
const deleteMenuBtn = document.getElementById('deleteMenuBtn');
const resetFormBtn = document.getElementById('resetFormBtn');
const addNewMenuBtn = document.getElementById('addNewMenuBtn');
const menuListDiv = document.getElementById('menuList');
const imageFileInput = document.getElementById('imageFileInput');
const imagePreview = document.getElementById('imagePreview');
const noImageText = document.getElementById('noImageText');
const removeImageBtn = document.getElementById('removeImageBtn');
const currentMenuImageInput = document.getElementById('currentMenuImage');

// オプション選択関連のDOM要素
const optionSelectsContainer = document.getElementById('optionSelectsContainer');
const addOptionSelectBtn = document.getElementById('addOptionSelectBtn');
const optionSelectTemplate = document.querySelector('.option-select-template');

// プリンター選択関連のDOM要素
const printerSelectsContainer = document.getElementById('printerSelectsContainer');
const addPrinterSelectBtn = document.getElementById('addPrinterSelectBtn');
const printerSelectTemplate = document.querySelector('.printer-select-template');

let selectedMenuId = null; // 現在選択中のメニューID

// ★★★ここから追加！飲み放題関連のDOM要素★★★
const isPlanStarterInput = document.getElementById('isPlanStarterInput');
const planIdGroup = document.getElementById('planIdGroup');
const planSelect = document.getElementById('planSelect');

// =======================================================
// フォームのリセットと新規登録モードへの切り替え
// =======================================================
function resetForm() {
    menuForm.reset();
    document.getElementById('menuId').value = '';
    currentMenuImageInput.value = '';

    formTitle.textContent = '新規メニュー登録';
    submitBtn.textContent = '登録する';
    deleteMenuBtn.style.display = 'none';

    // 画像プレビューをリセット
    imagePreview.src = '#';
    imagePreview.style.display = 'none';
    noImageText.style.display = 'none';
    removeImageBtn.style.display = 'none';

    // オプション選択を全てクリアして初期状態に戻す
    clearDynamicSelects(optionSelectsContainer, optionSelectTemplate);
    addDynamicSelect(optionSelectsContainer, optionSelectTemplate, window.allOptionGroups, 'optionGroupIds', '');

    // プリンター選択を全てクリアして初期状態に戻す
    clearDynamicSelects(printerSelectsContainer, printerSelectTemplate);
    addDynamicSelect(printerSelectsContainer, printerSelectTemplate, window.allPrinters, 'printerIds', '');

    // ★★★ここから追加！飲み放題関連のフィールドをリセット★★★
    isPlanStarterInput.checked = false;
    togglePlanIdGroup(); // プルダウンの表示/非表示を更新
    planSelect.value = ''; // プルダウンの選択をリセット

    // 選択中のメニューアイテムのハイライトを解除
    const selectedItem = document.querySelector('.menu-item.selected');
    if (selectedItem) {
        selectedItem.classList.remove('selected');
    }
    selectedMenuId = null;
}

// 動的に追加されたselect要素を全てクリアし、テンプレートを再配置 (変更なし)
function clearDynamicSelects(container, template) {
    Array.from(container.children).forEach(child => {
        if (child !== template) {
            child.remove();
        }
    });
    template.style.display = 'none';
}

// =======================================================
// メニュー詳細の表示 (編集モードへの切り替え)
// =======================================================
async function showMenuDetails(menuId) {
    const prevSelected = document.querySelector('.menu-item.selected');
    if (prevSelected) {
        prevSelected.classList.remove('selected');
    }
    const currentSelected = document.querySelector(`.menu-item[data-menu-id="${menuId}"]`);
    if (currentSelected) {
        currentSelected.classList.add('selected');
    }

    selectedMenuId = menuId;
    formTitle.textContent = 'メニュー編集';
    submitBtn.textContent = '更新する';
    deleteMenuBtn.style.display = 'inline-block';

    try {
        // ★修正: URLの先頭にスラッシュ'/'を追加して、ルートからの絶対パスにする
        const response = await fetch(`/menu/${menuId}/details`); // ControllerのGET("/{menuId}/details") に合わせる
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`メニュー詳細の取得に失敗しました: ${response.status} ${errorText}`);
        }
        const menu = await response.json(); // Controllerが直接Menuエンティティを返すことを想定

        // フォームにデータをセット
        document.getElementById('menuId').value = menu.menuId;
        document.getElementById('menuNameInput').value = menu.menuName;
        document.getElementById('priceInput').value = menu.price;
        document.getElementById('menuDescriptionInput').value = menu.menuDescription || '';
        document.getElementById('receiptLabelInput').value = menu.receiptLabel || '';
        document.getElementById('isSoldOutInput').checked = menu.isSoldOut || false;

        currentMenuImageInput.value = menu.menuImage || '';

        // 画像プレビューの表示
        if (menu.menuImage) {
            imagePreview.src = menu.menuImage;
            imagePreview.style.display = 'block';
            noImageText.style.display = 'none';
            removeImageBtn.style.display = 'inline-block';
        } else {
            imagePreview.src = '#';
            imagePreview.style.display = 'none';
            noImageText.style.display = 'inline-block';
            removeImageBtn.style.display = 'none';
        }
        imageFileInput.value = '';

        // プルダウンの選択
        // 関連エンティティのIDを正しくセット (例: menu.timeSlotはMenuTimeSlotオブジェクト)
        document.getElementById('timeSlotSelect').value = menu.timeSlot ? menu.timeSlot.timeSlotId : '';
        document.getElementById('taxRateSelect').value = menu.taxRate ? menu.taxRate.taxRateId : '';
        document.getElementById('menuGroupSelect').value = menu.menuGroup ? menu.menuGroup.groupId : '';

        // オプション選択のセット
        // ControllerのgetMenuDetailsでMenuOptionやMenuPrinterMapのIDリストが直接JSONで返されない場合、
        // ここでMenuOptionやMenuPrinterMapオブジェクトからIDを抽出する必要がある。
        // 現状、Menuエンティティが直接返されるため、関連エンティティのIDにアクセスするパスを確認。
        // もしMenuエンティティがList<MenuOption>を直接持つなら
        const fetchedOptionIds = menu.menuOptions ? menu.menuOptions.map(mo => mo.optionGroupId) : [];
        setDynamicSelects(optionSelectsContainer, optionSelectTemplate, window.allOptionGroups, 'optionGroupIds', fetchedOptionIds);
        
        // もしMenuエンティティがList<MenuPrinterMap>を直接持つなら
        const fetchedPrinterIds = menu.menuPrinterMaps ? menu.menuPrinterMaps.map(mp => mp.printer.printerId) : [];
        setDynamicSelects(printerSelectsContainer, printerSelectTemplate, window.allPrinters, 'printerIds', fetchedPrinterIds);

        // ★★★ここから追加！飲み放題関連のフィールドをセット★★★
        isPlanStarterInput.checked = menu.isPlanStarter || false;
        togglePlanIdGroup(); // プルダウンの表示/非表示を更新
        planSelect.value = menu.planId || ''; // プルダウンの選択をセット

    } catch (error) {
        console.error('Error fetching menu details:', error);
        toastr.error('メニュー詳細の読み込みに失敗しました。\n' + error.message, "エラー");
        resetForm();
    }
}

// =======================================================
// 動的セレクトボックスの管理 (オプション & プリンター共通) (変更なし)
// =======================================================
function addDynamicSelect(container, template, allItems, nameAttribute, selectedValue = '') {
    const newSelect = template.cloneNode(true);
    newSelect.style.display = '';
    newSelect.name = nameAttribute;
    newSelect.classList.remove(template.classList[0]);

    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.textContent = '削除';
    removeBtn.classList.add('btn', 'btn-secondary');
    removeBtn.style.marginLeft = '10px';
    removeBtn.onclick = () => {
        newSelect.remove();
        removeBtn.remove();
        const nextSibling = newSelect.nextSibling;
        if (nextSibling && nextSibling.tagName === 'BR') {
            nextSibling.remove();
        }
        updateAllDynamicSelectOptions(container, template, nameAttribute);
    };

    Array.from(newSelect.options).forEach(option => {
        option.disabled = false;
    });

    container.appendChild(newSelect);
    container.appendChild(removeBtn);
    container.appendChild(document.createElement('br'));

    newSelect.onchange = () => updateAllDynamicSelectOptions(container, template, nameAttribute);

    if (selectedValue !== '') {
        newSelect.value = selectedValue;
    } else {
        newSelect.selectedIndex = 0;
    }

    updateAllDynamicSelectOptions(container, template, nameAttribute);
    return newSelect;
}

function updateAllDynamicSelectOptions(container, template, nameAttribute) {
    const allSelects = Array.from(container.querySelectorAll(`select[name="${nameAttribute}"]`));
    const selectedValues = allSelects.map(s => s.value).filter(v => v !== '');

    allSelects.forEach(select => {
        Array.from(select.options).forEach(option => {
            if (option.value === '') {
                option.hidden = false;
                return;
            }
            option.hidden = selectedValues.includes(option.value) && option.value !== select.value;
        });
    });
}

function setDynamicSelects(container, template, allItems, nameAttribute, selectedIds) {
    clearDynamicSelects(container, template);

    if (selectedIds && selectedIds.length > 0) {
        selectedIds.forEach(id => {
            if (id !== null && id !== undefined) {
                addDynamicSelect(container, template, allItems, nameAttribute, String(id));
            }
        });
    }
    addDynamicSelect(container, template, allItems, nameAttribute, '');
}


// =======================================================
// メニュー一覧の表示 (階層構造) (変更なし)
// =======================================================
function displayGroupedMenus() {
    menuListDiv.innerHTML = '';

    const groupedMenus = {};
    window.allMenus.forEach(menu => {
        const groupId = menu.menuGroup ? menu.menuGroup.groupId : 'null';
        if (!groupedMenus[groupId]) {
            groupedMenus[groupId] = [];
        }
        groupedMenus[groupId].push(menu);
    });

    // allMenuGroups は sort_order でソート済みであることを前提
    // グループ表示順をallMenuGroupsの順序に合わせる
    window.allMenuGroups.forEach(group => {
        const groupId = group.groupId;
        const groupName = group.groupName;
        const menusInGroup = groupedMenus[groupId] || []; // そのグループに紐づくメニューがなければ空配列

        const groupHeader = document.createElement('div');
        groupHeader.classList.add('menu-group-header');
        groupHeader.textContent = groupName;

        const menuCountSpan = document.createElement('span');
        menuCountSpan.style.fontSize = '0.8em';
        menuCountSpan.style.color = '#666';
        menuCountSpan.textContent = ` (${menusInGroup.length}件)`;
        groupHeader.appendChild(menuCountSpan);

        const groupItemsDiv = document.createElement('div');
        groupItemsDiv.classList.add('menu-group-items');
        groupItemsDiv.style.display = 'none';

        groupHeader.addEventListener('click', () => {
            groupItemsDiv.classList.toggle('expanded');
            groupHeader.classList.toggle('expanded');
            if (groupItemsDiv.classList.contains('expanded')) {
                groupItemsDiv.style.display = 'block';
            } else {
                groupItemsDiv.style.display = 'none';
            }
        });

        menusInGroup.forEach(menu => {
            const menuItemDiv = document.createElement('div');
            menuItemDiv.classList.add('menu-item');
            menuItemDiv.dataset.menuId = menu.menuId;
            menuItemDiv.onclick = () => showMenuDetails(menu.menuId);

            const menuItemHeader = document.createElement('div');
            menuItemHeader.classList.add('menu-item-header');
            menuItemHeader.textContent = menu.menuName;

            const menuPriceSpan = document.createElement('span');
            menuPriceSpan.textContent = `${menu.price.toLocaleString()}円`;
            menuItemHeader.appendChild(menuPriceSpan);

            menuItemDiv.appendChild(menuItemHeader);
            groupItemsDiv.appendChild(menuItemDiv);
        });

        menuListDiv.appendChild(groupHeader);
        menuListDiv.appendChild(groupItemsDiv);
    });

    const unassignedMenus = groupedMenus['null'] || [];
    if (unassignedMenus.length > 0) {
        const groupHeader = document.createElement('div');
        groupHeader.classList.add('menu-group-header');
        groupHeader.textContent = '未分類メニュー';

        const menuCountSpan = document.createElement('span');
        menuCountSpan.style.fontSize = '0.8em';
        menuCountSpan.style.color = '#666';
        menuCountSpan.textContent = ` (${unassignedMenus.length}件)`;
        groupHeader.appendChild(menuCountSpan);

        const groupItemsDiv = document.createElement('div');
        groupItemsDiv.classList.add('menu-group-items');
        groupItemsDiv.style.display = 'none';

        groupHeader.addEventListener('click', () => {
            groupItemsDiv.classList.toggle('expanded');
            groupHeader.classList.toggle('expanded');
            if (groupItemsDiv.classList.contains('expanded')) {
                groupItemsDiv.style.display = 'block';
            } else {
                groupItemsDiv.style.display = 'none';
            }
        });

        unassignedMenus.forEach(menu => {
            const menuItemDiv = document.createElement('div');
            menuItemDiv.classList.add('menu-item');
            menuItemDiv.dataset.menuId = menu.menuId;
            menuItemDiv.onclick = () => showMenuDetails(menu.menuId);

            const menuItemHeader = document.createElement('div');
            menuItemHeader.classList.add('menu-item-header');
            menuItemHeader.textContent = menu.menuName;

            const menuPriceSpan = document.createElement('span');
            menuPriceSpan.textContent = `${menu.price.toLocaleString()}円`;
            menuItemHeader.appendChild(menuPriceSpan);

            menuItemDiv.appendChild(menuItemHeader);
            groupItemsDiv.appendChild(menuItemDiv);
        });

        menuListDiv.appendChild(groupHeader);
        menuListDiv.appendChild(groupItemsDiv);
    }
}


// =======================================================
// Toastr通知の初期設定と表示
// =======================================================
function setupToastr() {
    toastr.options = {
        "closeButton": true,
        "debug": false,
        "newestOnTop": false,
        "progressBar": true,
        "positionClass": "toast-top-center",
        "preventDuplicates": false,
        "onclick": null,
        "showDuration": "300",
        "hideDuration": "1000",
        "timeOut": "5000",
        "extendedTimeOut": "1000",
        "showEasing": "swing",
        "hideEasing": "linear",
        "showMethod": "fadeIn",
        "hideMethod": "fadeOut"
    };
}


// =======================================================
// イベントリスナー設定
// =======================================================

// ファイル選択時に画像プレビューを表示 (変更なし)
imageFileInput.addEventListener('change', (event) => {
    const file = event.target.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = (e) => {
            imagePreview.src = e.target.result;
            imagePreview.style.display = 'block';
            noImageText.style.display = 'none';
            removeImageBtn.style.display = 'inline-block';
        };
        reader.readAsDataURL(file);
    } else {
        if (currentMenuImageInput.value) {
            imagePreview.src = currentMenuImageInput.value;
            imagePreview.style.display = 'block';
            noImageText.style.display = 'none';
            removeImageBtn.style.display = 'inline-block';
        } else {
            imagePreview.src = '#';
            imagePreview.style.display = 'none';
            noImageText.style.display = 'inline-block';
            removeImageBtn.style.display = 'none';
        }
    }
});

// 画像削除ボタン (変更なし)
removeImageBtn.addEventListener('click', () => {
    imageFileInput.value = '';
    imagePreview.src = '#';
    imagePreview.style.display = 'none';
    noImageText.style.display = 'inline-block';
    removeImageBtn.style.display = 'none';
    currentMenuImageInput.value = '';
});

// フォームリセットボタン (変更なし)
resetFormBtn.addEventListener('click', resetForm);

// 新規メニュー登録ボタン (変更なし)
addNewMenuBtn.addEventListener('click', resetForm);

// オプション追加ボタンクリックイベント (変更なし)
addOptionSelectBtn.addEventListener('click', () => addDynamicSelect(optionSelectsContainer, optionSelectTemplate, window.allOptionGroups, 'optionGroupIds', ''));

// プリンター追加ボタンクリックイベント (変更なし)
addPrinterSelectBtn.addEventListener('click', () => addDynamicSelect(printerSelectsContainer, printerSelectTemplate, window.allPrinters, 'printerIds', ''));


// ★★★ フォームのSUBMITイベントをAjaxに切り替える (再修正) ★★★
menuForm.addEventListener('submit', async (event) => {
    event.preventDefault(); // デフォルトのフォーム送信を防止

    const formData = new FormData(menuForm); // フォームデータを取得 (ファイルも含む)

    // hiddenフィールドのmenuIdをFormDataに追加（これがないと更新対象が不明になる）
    formData.append('menuId', document.getElementById('menuId').value);

    // プルダウンから選択された関連エンティティのIDもFormDataに追加
    // MenuオブジェクトのTaxRate, MenuGroup, MenuTimeSlotはIDのみをセット
    // ここで直接オブジェクトとしてセットしようとすると、マッピングエラーになる可能性が高いので、
    // IDを直接FormDataに追加する
    formData.append('taxRate.taxRateId', document.getElementById('taxRateSelect').value);
    formData.append('menuGroup.groupId', document.getElementById('menuGroupSelect').value);
    formData.append('timeSlot.timeSlotId', document.getElementById('timeSlotSelect').value);
    
    // isSoldOut (チェックボックス) はチェックが入ってないとFormDataに含まれないので、明示的に追加
    formData.append('isSoldOut', document.getElementById('isSoldOutInput').checked);

    // ★★★ここから追加！isPlanStarterとplanIdをFormDataに追加★★★
    formData.append('isPlanStarter', isPlanStarterInput.checked); // チェックボックスの値
    if (isPlanStarterInput.checked) { // チェックされている場合のみplanIdを送信
        formData.append('planId', planSelect.value);
    } else {
        formData.append('planId', ''); // チェックされていない場合は空文字列を送信
    }

    // オプション選択の値を正しく FormData に追加 (変更なし)
    const optionSelects = optionSelectsContainer.querySelectorAll('select[name="optionGroupIds"]');
    formData.delete('optionGroupIds'); // 既存の formData の optionGroupIds を削除
    optionSelects.forEach(select => {
        if (select.value) { // 選択されている値のみ
            formData.append('optionGroupIds', select.value);
        }
    });

    // プリンター選択の値を正しく FormData に追加 (変更なし)
    const printerSelects = printerSelectsContainer.querySelectorAll('select[name="printerIds"]');
    formData.delete('printerIds'); // 既存の formData の printerIds を削除
    printerSelects.forEach(select => {
        if (select.value) { // 選択されている値のみ
            formData.append('printerIds', select.value);
        }
    });
    
    // 画像ファイルの扱い:
    if (!imageFileInput.files.length) {
        formData.append('currentMenuImage', currentMenuImageInput.value || '');
    } else {
        formData.delete('currentMenuImage');
    }


    try {
        const response = await fetch('/menu/save', {
            method: 'POST',
            body: formData
        });

        // レスポンスがJSONではない可能性があるので、response.json()をtry-catchで囲む
        let result;
        try {
            result = await response.json();
        } catch (jsonError) {
            console.error('JSONパースエラー:', jsonError);
            toastr.error('サーバーからの応答が不正です。', "エラー");
            return;
        }

        if (response.ok && result.status === 'success') {
            toastr.success(result.message, "成功");
            await refreshMenuList();
            resetForm();
        } else {
            toastr.error(result.message || '不明なエラーが発生しました。', "エラー");
        }
    } catch (error) {
        console.error('メニュー保存エラー:', error);
        toastr.error('メニューの保存中に予期せぬエラーが発生しました。', "エラー");
    }
});


// ★★★ 削除ボタンのクリックイベントもAjaxに切り替える (再修正) ★★★
deleteMenuBtn.addEventListener('click', async () => {
    if (!selectedMenuId) return;

    if (confirm('本当にこのメニューを削除しますか？')) {
        try {
            const response = await fetch(`/menu/delete/${selectedMenuId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            let result;
            try {
                result = await response.json();
            } catch (jsonError) {
                console.error('JSONパースエラー:', jsonError);
                toastr.error('サーバーからの応答が不正です。', "エラー");
                return;
            }

            if (response.ok && result.status === 'success') {
                toastr.success(result.message, "成功");
                await refreshMenuList();
                resetForm();
            } else {
                toastr.error(result.message || '不明なエラーが発生しました。', "エラー");
            }
        } catch (error) {
            console.error('メニュー削除エラー:', error);
            toastr.error('メニューの削除中に予期せぬエラーが発生しました。', "エラー");
        }
    }
});


// メニューリストをサーバーから再取得して表示を更新する関数 (変更なし)
async function refreshMenuList() {
    try {
        // storeIdをCookieから取得してリクエストに含める
        const storeId = getCookie("storeId"); 
        const response = await fetch(`/menu/list_data?storeId=${storeId}`); // storeIdをクエリパラメータとして渡す
        if (!response.ok) {
            throw new Error('メニューリストの取得に失敗しました。');
        }
        const data = await response.json();
        window.allMenus = data.menus;
        window.allMenuGroups = data.menuGroups; // menuGroupsも更新されることを想定
        displayGroupedMenus();
    } catch (error) {
        console.error('メニューリストの更新エラー:', error);
        toastr.error('メニューリストの更新に失敗しました。', "エラー");
    }
}


// =======================================================
// 飲み放題関連の表示制御 (新しく追加)
// =======================================================
function togglePlanIdGroup() {
    if (isPlanStarterInput.checked) {
        planIdGroup.style.display = 'block';
        planSelect.setAttribute('required', 'required'); // 必須にする
    } else {
        planIdGroup.style.display = 'none';
        planSelect.removeAttribute('required'); // 必須を解除
        planSelect.value = ''; // チェックを外したら選択をクリア
    }
}


// ページロード時の初期化
document.addEventListener('DOMContentLoaded', () => {
    setupToastr();
    resetForm(); // まずフォームをリセットして、飲み放題関連の初期表示を適用
    displayGroupedMenus(); // メニューリストを表示

    // 飲み放題開始メニューチェックボックスのイベントリスナー
    isPlanStarterInput.addEventListener('change', togglePlanIdGroup);
});