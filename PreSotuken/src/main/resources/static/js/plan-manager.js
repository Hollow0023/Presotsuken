/**
 * プラン管理画面の処理
 */

// グローバル変数
let initialPlans = [];
let allMenuGroups = [];
let currentStoreId = null;

// DOM要素の参照
let planListContainer, formTitle, planForm, planIdInput, planNameInput, planDescriptionInput;
let submitBtn, resetBtn, deleteBtn, newPlanBtn, storeIdFormInput, currentStoreIdDisplay;
let menuGroupSelectsContainer, addMenuGroupBtn;

// 選択済みメニューグループIDを管理するSet
let selectedGroupIds = new Set();

/**
 * 初期化処理
 */
function initializePlanManager(plans, menuGroups) {
    initialPlans = plans;
    allMenuGroups = menuGroups;
    
    currentStoreId = getCookie("storeId");
    
    if (currentStoreId === null || isNaN(currentStoreId)) {
        alert("店舗IDがCookieに設定されていません。管理画面へ戻ります。");
        window.location.href = "/admin/dashboard";
        return;
    }

    // DOM要素の参照を取得
    planListContainer = document.getElementById('planList');
    formTitle = document.getElementById('formTitle');
    planForm = document.getElementById('planForm');
    planIdInput = document.getElementById('planIdInput');
    planNameInput = document.getElementById('planNameInput');
    planDescriptionInput = document.getElementById('planDescriptionInput');
    submitBtn = document.getElementById('submitBtn');
    resetBtn = document.getElementById('resetBtn');
    deleteBtn = document.getElementById('deleteBtn');
    newPlanBtn = document.getElementById('newPlanBtn');
    storeIdFormInput = document.getElementById('storeIdFormInput');
    currentStoreIdDisplay = document.getElementById('currentStoreIdDisplay');
    menuGroupSelectsContainer = document.getElementById('menuGroupSelectsContainer');
    addMenuGroupBtn = document.getElementById('addMenuGroupBtn');

    // 現在の店舗IDを画面に表示
    if (currentStoreIdDisplay) {
        currentStoreIdDisplay.textContent = currentStoreId;
    }

    setupEventListeners();
    resetForm(); // 初期状態として新規作成フォームをセット
}

/**
 * イベントリスナーの設定
 */
function setupEventListeners() {
    if (newPlanBtn) {
        newPlanBtn.addEventListener('click', resetForm);
    }
    
    if (resetBtn) {
        resetBtn.addEventListener('click', resetForm);
    }
    
    if (planForm) {
        planForm.addEventListener('submit', handleSubmit);
    }
    
    // 初期ロード時に全てのプランアイテムにイベントリスナーを設定
    if (planListContainer) {
        planListContainer.addEventListener('click', function(event) {
            const planItem = event.target.closest('.plan-item');
            if (planItem) {
                const planId = parseInt(planItem.getAttribute('data-plan-id'));
                // APIからプランデータを取得してフォームにロード
                fetch(`/admin/plans/api/${planId}`)
                    .then(response => response.json())
                    .then(plan => {
                        loadPlanIntoForm(plan);
                    })
                    .catch(error => {
                        console.error('プランデータの取得に失敗しました:', error);
                        alert('プランデータの取得に失敗しました。');
                    });
            }
        });
    }
    
    // 「グループ追加」ボタンのイベントリスナー
    if (addMenuGroupBtn) {
        addMenuGroupBtn.addEventListener('click', () => {
            createOrUpdateSelect(); // 新しいプルダウンを追加
            updateAllSelectOptions(); // 他のプルダウンのオプションを更新
        });
    }
    
    // 削除ボタンのイベントリスナー
    if (deleteBtn) {
        deleteBtn.addEventListener('click', function() {
            const planId = planIdInput.value;
            if (planId && confirm('本当にこのプランを削除しますか？')) {
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = `/admin/plans/delete/${planId}`;
                document.body.appendChild(form);
                form.submit();
            }
        });
    }
}

/**
 * プルダウンを生成・更新する関数
 */
function createOrUpdateSelect(selectElement = null, selectedValue = null) {
    let select;

    if (selectElement === null) {
        // 新しいプルダウンを作成
        const selectDiv = document.createElement('div');
        selectDiv.className = 'select-group';

        select = document.createElement('select');
        select.className = 'menu-group-select';
        select.name = 'menuGroupIds';
        select.required = true;

        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.textContent = '削除';
        removeBtn.className = 'remove-group-btn';
        removeBtn.onclick = function() {
            selectDiv.remove();
            updateAllSelectOptions(); // 削除後にオプションを更新
        };

        selectDiv.appendChild(select);
        selectDiv.appendChild(removeBtn);
        menuGroupSelectsContainer.appendChild(selectDiv);
    } else {
        // 既存のプルダウンを更新
        select = selectElement;
    }

    // 現在の選択値を保存
    const currentSelectValue = select.value;

    // 他のプルダウンで選択されているIDを一時的に収集
    const tempSelectedIds = new Set();
    document.querySelectorAll('.menu-group-select').forEach(otherSelect => {
        if (otherSelect !== select && otherSelect.value !== "") {
            tempSelectedIds.add(parseInt(otherSelect.value));
        }
    });

    // 既存のオプションをクリア
    select.innerHTML = '';

    // デフォルトオプションを追加
    const defaultOption = document.createElement('option');
    defaultOption.value = '';
    defaultOption.textContent = 'メニューグループを選択';
    select.appendChild(defaultOption);

    // メニューグループのオプションを追加
    allMenuGroups.forEach(group => {
        // 他のプルダウンで既に選択されていないか、またはこのプルダウン自身で選択されているグループである場合
        if (!tempSelectedIds.has(group.groupId) || group.groupId === currentSelectValue || group.groupId === selectedValue) {
            const option = document.createElement('option');
            option.value = group.groupId;
            option.textContent = group.groupName;
            select.appendChild(option);
        }
    });

    // 選択値をセット
    if (selectedValue !== null) { // 初期ロード時や編集時の明示的な選択値
        select.value = selectedValue;
    } else if (currentSelectValue !== null) { // プルダウン更新時の元の選択値
        select.value = currentSelectValue;
    } else {
        select.value = ""; // デフォルトに戻す
    }
}

/**
 * 全てのプルダウンのオプションを更新する関数
 */
function updateAllSelectOptions() {
    selectedGroupIds.clear(); // 全ての選択済みIDをリセット

    // 現在の全てのプルダウンの選択値を収集し、selectedGroupIdsに格納
    document.querySelectorAll('.menu-group-select').forEach(select => {
        if (select.value !== "") {
            selectedGroupIds.add(parseInt(select.value));
        }
    });

    // 各プルダウンのオプションを再生成（現在の選択値を保持しながら）
    document.querySelectorAll('.menu-group-select').forEach(select => {
        createOrUpdateSelect(select); // 既存のselect要素を渡して更新
    });
}

/**
 * プランデータをフォームに読み込む関数
 */
function loadPlanIntoForm(plan) {
    formTitle.textContent = "プラン編集 (ID: " + plan.planId + ")";
    planForm.action = "/admin/plans/update";
    planIdInput.value = plan.planId;
    planNameInput.value = plan.planName;
    planDescriptionInput.value = plan.planDescription;
    storeIdFormInput.value = plan.storeId;
    submitBtn.textContent = "更新";
    deleteBtn.style.display = "inline-block";

    // 既存のプルダウンを全てクリア
    menuGroupSelectsContainer.innerHTML = '';
    selectedGroupIds.clear(); // 選択済みIDをクリア

    // 紐づくグループの数だけプルダウンを生成し、値をセット
    if (plan.menuGroupIds && plan.menuGroupIds.length > 0) {
        plan.menuGroupIds.forEach(groupId => {
            createOrUpdateSelect(null, groupId); // nullを渡して新しいselectを作成し、groupIdを選択
            selectedGroupIds.add(groupId); // 選択済みとして追加
        });
    } else {
        createOrUpdateSelect(); // グループがない場合は最初の空のプルダウンを追加
    }
    // updateAllSelectOptionsを呼び出してオプションを更新して重複を避ける
    updateAllSelectOptions();
}

/**
 * フォームをリセット
 */
function resetForm() {
    if (planForm) planForm.reset();
    if (planIdInput) planIdInput.value = '';
    if (formTitle) formTitle.textContent = '新規プラン作成';
    if (submitBtn) submitBtn.textContent = '作成';
    if (deleteBtn) deleteBtn.style.display = 'none';
    if (storeIdFormInput) storeIdFormInput.value = currentStoreId;
    
    // 既存のプルダウンを全てクリア
    if (menuGroupSelectsContainer) {
        menuGroupSelectsContainer.innerHTML = '';
    }
    selectedGroupIds.clear(); // 選択済みIDをクリア

    // 最初の空のプルダウンを追加
    createOrUpdateSelect();
}

/**
 * フォーム送信処理
 */
function handleSubmit(event) {
    event.preventDefault();
    
    const formData = new FormData(planForm);
    const isEdit = planIdInput && planIdInput.value;
    const url = isEdit ? '/admin/plans/update' : '/admin/plans/create';
    const method = 'POST';
    
    fetch(url, {
        method: method,
        body: formData
    })
    .then(response => {
        if (response.ok) {
            window.location.reload();
        } else {
            alert('保存に失敗しました。');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        alert('エラーが発生しました。');
    });
}

/**
 * ページロード時の処理
 */
window.addEventListener('load', () => {
    // URLからメッセージパラメータを取得して表示
    const urlParams = new URLSearchParams(window.location.search);
    const message = urlParams.get('message');
    
    if (message) {
        alert(decodeURIComponent(message));
        // URLからメッセージパラメータを削除
        const newUrl = new URL(window.location.href);
        newUrl.searchParams.delete('message');
        window.history.replaceState({}, document.title, newUrl.toString());
    }
});

// グローバルスコープに公開（必要に応じて）
window.initializePlanManager = initializePlanManager;
window.loadPlanIntoForm = loadPlanIntoForm;
window.createOrUpdateSelect = createOrUpdateSelect;
window.updateAllSelectOptions = updateAllSelectOptions;