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

    setupEventListeners();
    loadPlans();
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
    
    document.getElementById('addMenuGroupBtn')?.addEventListener('click', addMenuGroupRow);
}

/**
 * プランリストを読み込み
 */
function loadPlans() {
    if (!planListContainer) return;
    
    planListContainer.innerHTML = '';
    
    initialPlans.forEach(plan => {
        const planElement = createPlanElement(plan);
        planListContainer.appendChild(planElement);
    });
}

/**
 * プラン要素を作成
 */
function createPlanElement(plan) {
    const div = document.createElement('div');
    div.className = 'plan-item';
    div.innerHTML = `
        <h3>${plan.planName}</h3>
        <p>${plan.description || '説明なし'}</p>
        <button type="button" onclick="editPlan(${plan.planId})">編集</button>
    `;
    return div;
}

/**
 * プランを編集
 */
function editPlan(planId) {
    const plan = initialPlans.find(p => p.planId === planId);
    if (!plan) return;
    
    if (planIdInput) planIdInput.value = plan.planId;
    if (planNameInput) planNameInput.value = plan.planName;
    if (planDescriptionInput) planDescriptionInput.value = plan.description || '';
    
    if (formTitle) formTitle.textContent = 'プラン編集';
    if (submitBtn) submitBtn.textContent = '更新';
    if (deleteBtn) deleteBtn.style.display = 'inline-block';
    
    // メニューグループの設定処理も追加
    loadMenuGroupsForPlan(plan);
}

/**
 * プラン用のメニューグループを読み込み
 */
function loadMenuGroupsForPlan(plan) {
    // メニューグループの設定処理を実装
    console.log('Loading menu groups for plan:', plan);
}

/**
 * メニューグループ行を追加
 */
function addMenuGroupRow() {
    const container = document.getElementById('menuGroupsContainer');
    if (!container) return;
    
    const row = document.createElement('div');
    row.className = 'menu-group-row';
    row.innerHTML = `
        <select name="menuGroupIds" required>
            <option value="">メニューグループを選択</option>
            ${allMenuGroups.map(group => 
                `<option value="${group.menuGroupId}">${group.groupName}</option>`
            ).join('')}
        </select>
        <button type="button" onclick="removeMenuGroupRow(this)">削除</button>
    `;
    container.appendChild(row);
}

/**
 * メニューグループ行を削除
 */
function removeMenuGroupRow(button) {
    button.parentElement.remove();
}

/**
 * フォームをリセット
 */
function resetForm() {
    if (planForm) planForm.reset();
    if (planIdInput) planIdInput.value = '';
    if (formTitle) formTitle.textContent = 'プラン作成';
    if (submitBtn) submitBtn.textContent = '作成';
    if (deleteBtn) deleteBtn.style.display = 'none';
    if (storeIdFormInput) storeIdFormInput.value = currentStoreId;
    
    // メニューグループをクリア
    const container = document.getElementById('menuGroupsContainer');
    if (container) container.innerHTML = '';
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
    resetForm();
    
    if (currentStoreIdDisplay) {
        currentStoreIdDisplay.textContent = currentStoreId;
    }
    
    const urlParams = new URLSearchParams(window.location.search);
    const message = urlParams.get('message');
    if (message) {
        alert(decodeURIComponent(message));
        const newUrl = new URL(window.location.href);
        newUrl.searchParams.delete('message');
        window.history.replaceState({}, '', newUrl);
    }
});

// グローバルスコープに公開
window.initializePlanManager = initializePlanManager;
window.editPlan = editPlan;
window.addMenuGroupRow = addMenuGroupRow;
window.removeMenuGroupRow = removeMenuGroupRow;