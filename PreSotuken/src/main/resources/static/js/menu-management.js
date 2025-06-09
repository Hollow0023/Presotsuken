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

// =======================================================
// フォームのリセットと新規登録モードへの切り替え (変更なし)
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
    addDynamicSelect(optionSelectsContainer, optionSelectTemplate, window.allOptionGroups, 'optionGroupIds', ''); // 最初の空の選択肢を追加

    // プリンター選択を全てクリアして初期状態に戻す
    clearDynamicSelects(printerSelectsContainer, printerSelectTemplate);
    addDynamicSelect(printerSelectsContainer, printerSelectTemplate, window.allPrinters, 'printerIds', ''); // 最初の空の選択肢を追加

    // 選択中のメニューアイテムのハイライトを解除
    const selectedItem = document.querySelector('.menu-item.selected');
    if (selectedItem) {
        selectedItem.classList.remove('selected');
    }
    selectedMenuId = null;
}

// 動的に追加されたselect要素を全てクリアし、テンプレートを再配置 (変更なし)
function clearDynamicSelects(container, template) {
    // テンプレート以外の全ての要素（selectと削除ボタンとbr）を削除
    Array.from(container.children).forEach(child => {
        if (child !== template) {
            child.remove();
        }
    });
    template.style.display = 'none'; // テンプレートは非表示のまま
}

// =======================================================
// メニュー詳細の表示 (編集モードへの切り替え) (修正点あり)
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
        const response = await fetch(`/menu/${menuId}/details`);
        if (!response.ok) {
            const errorText = await response.text(); // エラーレスポンスの本文を取得
            throw new Error(`メニュー詳細の取得に失敗しました: ${response.status} ${errorText}`);
        }
        const menu = await response.json();

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
        // 関連エンティティが存在しない場合のnullチェックを追加
        document.getElementById('timeSlotSelect').value = menu.timeSlot && menu.timeSlot.timeSlotId ? menu.timeSlot.timeSlotId : '';
        document.getElementById('taxRateSelect').value = menu.taxRate && menu.taxRate.taxRateId ? menu.taxRate.taxRateId : '';
        document.getElementById('menuGroupSelect').value = menu.menuGroup && menu.menuGroup.groupId ? menu.menuGroup.groupId : '';

        // オプション選択のセット
        // menu.menuOptions が MenuOption のリストとしてJSONから来ると想定
        setDynamicSelects(optionSelectsContainer, optionSelectTemplate, window.allOptionGroups,
            'optionGroupIds', menu.menuOptions ? menu.menuOptions.map(mo => mo.optionGroupId) : []);
        
        // プリンター選択のセット
        // menu.menuPrinterMaps が MenuPrinterMap のリストとしてJSONから来ると想定
        setDynamicSelects(printerSelectsContainer, printerSelectTemplate, window.allPrinters,
            'printerIds', menu.menuPrinterMaps ? menu.menuPrinterMaps.map(mpm => mpm.printer.printerId) : []);

    } catch (error) {
        console.error('Error fetching menu details:', error);
        toastr.error('メニュー詳細の読み込みに失敗しました。\n' + error.message, "エラー"); // トースト表示
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

    // 削除ボタンの追加
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.textContent = '削除';
    removeBtn.classList.add('btn', 'btn-secondary');
    removeBtn.style.marginLeft = '10px';
    removeBtn.onclick = () => {
        newSelect.remove();
        removeBtn.remove();
        // selectの後にbrがあれば削除（これもテンプレートに含めていた場合は別途調整）
        const nextSibling = newSelect.nextSibling;
        if (nextSibling && nextSibling.tagName === 'BR') {
            nextSibling.remove();
        }
        updateAllDynamicSelectOptions(container, template, nameAttribute);
    };

    // ドロップダウンリストのオプションを更新
    Array.from(newSelect.options).forEach(option => {
        option.disabled = false; // 一旦全て有効に
    });

    container.appendChild(newSelect);
    container.appendChild(removeBtn);
    container.appendChild(document.createElement('br')); // 各セレクトボックスの後に改行を追加

    // 新しいselectが追加されたら、全てのselectのoptionを更新する
    newSelect.onchange = () => updateAllDynamicSelectOptions(container, template, nameAttribute);

    // 初期値のセット
    if (selectedValue !== '') {
        newSelect.value = selectedValue;
    } else {
        newSelect.selectedIndex = 0; // 空のオプションを選択
    }

    updateAllDynamicSelectOptions(container, template, nameAttribute);
    return newSelect;
}

// 全ての動的select要素のoptionを更新し、重複選択を防止 (変更なし)
function updateAllDynamicSelectOptions(container, template, nameAttribute) {
    const allSelects = Array.from(container.querySelectorAll(`select[name="${nameAttribute}"]`));
    const selectedValues = allSelects.map(s => s.value).filter(v => v !== '');

    allSelects.forEach(select => {
        Array.from(select.options).forEach(option => {
            // valueが空のオプションは常に表示
            if (option.value === '') {
                option.hidden = false;
                return;
            }
            // 既に選択されている値で、かつ現在のselect自身で選択されている値ではない場合、非表示にする
            option.hidden = selectedValues.includes(option.value) && option.value !== select.value;
        });
    });
}

// 既存データに基づいて動的セレクトボックスをセットする (変更なし)
function setDynamicSelects(container, template, allItems, nameAttribute, selectedIds) {
    clearDynamicSelects(container, template); // まず全てクリア

    if (selectedIds && selectedIds.length > 0) {
        selectedIds.forEach(id => {
            if (id !== null && id !== undefined) {
                addDynamicSelect(container, template, allItems, nameAttribute, String(id));
            }
        });
    }
    // 選択済みのものがセットされたら、追加で空の選択肢を1つ追加
    addDynamicSelect(container, template, allItems, nameAttribute, '');
}

// =======================================================
// メニュー一覧の表示 (階層構造)
// =======================================================
function displayGroupedMenus() {
    menuListDiv.innerHTML = ''; // まず中身をクリア

    // メニューをグループごとにまとめる
    const groupedMenus = {};
    window.allMenus.forEach(menu => {
        const groupId = menu.menuGroup ? menu.menuGroup.groupId : 'null'; // グループがない場合は 'null' をキーにする
        if (!groupedMenus[groupId]) {
            groupedMenus[groupId] = [];
        }
        groupedMenus[groupId].push(menu);
    });

    // グループごとにHTMLを生成
    for (const groupId in groupedMenus) {
        const group = window.allMenuGroups.find(g => g.groupId === parseInt(groupId)); // groupIdが文字列なのでparseInt
        const groupName = group ? group.groupName : '未分類';
        const menusInGroup = groupedMenus[groupId];

        // グループヘッダーの要素を作成
        const groupHeader = document.createElement('div');
        groupHeader.classList.add('menu-group-header');
        groupHeader.textContent = groupName;

        // グループ内のメニュー数を表示
        const menuCountSpan = document.createElement('span');
        menuCountSpan.style.fontSize = '0.8em';
        menuCountSpan.style.color = '#666';
        menuCountSpan.textContent = ` (${menusInGroup.length}件)`;
        groupHeader.appendChild(menuCountSpan);

        // グループヘッダーをクリックしたときの処理
        groupHeader.addEventListener('click', () => {
            groupItemsDiv.classList.toggle('expanded');
            groupHeader.classList.toggle('expanded');
        });

        // グループ内のメニューを表示する要素を作成
        const groupItemsDiv = document.createElement('div');
        groupItemsDiv.classList.add('menu-group-items');

        // グループ内の各メニューのHTMLを生成
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
        "positionClass": "toast-top-center", // ★ここを 'toast-top-center' に変更！
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
        if (currentMenuImageInput.value) { // 既存の画像パスがあれば
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
    currentMenuImageInput.value = ''; // hidden fieldの画像パスをクリア
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
    // 1. 新しいファイルが選択された場合: formDataは imageFile を自動で含む
    // 2. 新しいファイルはなし、かつ currentMenuImageInput が値を持つ場合:
    //    これは既存の画像をそのまま使う場合。formData.append('currentMenuImage', ...) でサーバーに伝える
    // 3. 新しいファイルはなし、かつ currentMenuImageInput が空の場合:
    //    これは既存の画像を削除した場合。formData.append('currentMenuImage', '') でサーバーに伝える
    if (!imageFileInput.files.length) { // 新しいファイルが選択されていない場合
        formData.append('currentMenuImage', currentMenuImageInput.value || ''); // 値がなければ空文字列
    } else {
        formData.delete('currentMenuImage'); // 新しいファイルがあるなら既存パスは不要
    }


    try {
        const response = await fetch('/menu/save', {
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (response.ok && result.status === 'success') {
            toastr.success(result.message, "成功");
            await refreshMenuList();
            resetForm(); // フォームをクリアして新規登録モードに戻す
        } else {
            // response.ok が false の場合 (HTTPステータスコードが2xx以外)
            // または result.status が 'error' の場合
            toastr.error(result.message, "エラー");
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
                // body: JSON.stringify({ menuId: selectedMenuId }), // 必要ならボディにIDを含める
                headers: {
                    'Content-Type': 'application/json' // ボディがないので、これは必須ではないが、明示的に
                }
            });

            const result = await response.json();

            if (response.ok && result.status === 'success') {
                toastr.success(result.message, "成功");
                await refreshMenuList();
                resetForm();
            } else {
                toastr.error(result.message, "エラー");
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
        const response = await fetch('/menu/list_data');
        if (!response.ok) {
            throw new Error('メニューリストの取得に失敗しました。');
        }
        const data = await response.json();
        window.allMenus = data.menus;
        window.allMenuGroups = data.menuGroups;
        displayGroupedMenus();
    } catch (error) {
        console.error('メニューリストの更新エラー:', error);
        toastr.error('メニューリストの更新に失敗しました。', "エラー");
    }
}


// ページロード時の初期化
document.addEventListener('DOMContentLoaded', () => {
    resetForm();
    displayGroupedMenus();
    setupToastr();
});