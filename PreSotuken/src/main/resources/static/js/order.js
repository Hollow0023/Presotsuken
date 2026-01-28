/**
 * 注文画面メイン処理
 * 座席での注文機能を提供するメインスクリプト
 */

// =============================================================================
// グローバル変数定義
// =============================================================================

/** @type {Array} カート内の商品データを保持する配列 */
const cart = [];

/** @type {Object} 税率データを保持するマップオブジェクト */
let taxRateMap = {};

/** @type {string|number} 現在の座席ID */
let seatId = getCookie("seatId");

/** @type {string} 座席名 */
let seatName = getCookie("seatName") || window.seatNameFromModel || seatId;

// =============================================================================
// 初期化処理
// =============================================================================

// 座席情報の初期化
if (!seatId || seatId === "null" || seatId === "undefined") {
    seatId = window.seatIdFromModel;
    seatName = getCookie("seatName") || window.seatNameFromModel || seatId;
}

/**
 * 座席情報を画面に表示する
 */
function displaySeatInfo() {
    const seatInfoElement = document.getElementById("seatInfo");
    if (seatInfoElement) {
        seatInfoElement.innerText = seatName;
    }
}

// =============================================================================
// ユーティリティ関数
// =============================================================================

/**
 * Cookieから指定されたキーの値を取得する
 * @param {string} name - 取得するCookieのキー名
 * @returns {string|null} Cookieの値、存在しない場合はnull
 */
function getCookie(name) {
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
        const [key, value] = cookie.trim().split('=');
        if (key === name) {
            return decodeURIComponent(value);
        }
    }
    return null;
}

/**
 * トーストメッセージを表示する
 * @param {string} message - 表示するメッセージ
 * @param {number} duration - 表示時間（ミリ秒）
 * @param {string} type - メッセージタイプ（success, error, info）
 */
function showToast(message, duration = 3000, type = 'success') {
    const toast = document.getElementById('toast');
    if (!toast) {
        console.warn('Toast element not found');
        alert(message);
        return;
    }
    
    toast.textContent = message;
    toast.className = `toast show ${type}`;
    
    setTimeout(() => {
        toast.classList.remove('show');
    }, duration);
}

// =============================================================================
// モーダル・パネル制御関数
// =============================================================================

/**
 * 注文履歴モーダルを開閉する関数
 * モーダルが開いている場合は閉じ、閉じている場合は履歴データを取得して表示する
 */
function toggleHistory() {
    const historyModal = document.getElementById("historyModal");
    const toggleBtn = document.getElementById("historyToggleButton");
    
    // 座席IDをCookieに保存
    document.cookie = `seatId=${seatId};`; 

    // モーダルが表示されている場合は閉じる
    if (historyModal.classList.contains("show")) {
        historyModal.classList.remove("show");
        toggleBtn.textContent = "注文履歴";
    } else {
        // モーダルが閉じている場合は、履歴を取得して表示
        fetchOrderHistoryForDisplay(historyModal, toggleBtn);
    }
}

/**
 * 注文履歴データを取得して表示する内部関数
 * @param {HTMLElement} historyModal - 履歴モーダル要素
 * @param {HTMLElement} toggleBtn - トグルボタン要素
 */
function fetchOrderHistoryForDisplay(historyModal, toggleBtn) {
    fetch('/order/history')
        .then(res => res.json())
        .then(data => {
            const tbody = document.querySelector('#historyTable tbody');
            const totalEl = document.getElementById('historyTotal');
            const countEl = document.getElementById('historyCount');
            const taxEl = document.getElementById('historyTax');
            
            // テーブルの中身をクリア
            tbody.innerHTML = '';
            taxEl.innerHTML = '';

            let total = 0;
            let count = 0;
            const rateTotals = {}; // 税率ごとの合計金額を保持

            // 取得した履歴データをループして表示を生成
            data.forEach(item => {
                const subtotal = parseInt(item.subtotal) || 0;
                const quantity = parseInt(item.quantity) || 0;
                const rate = parseFloat(item.taxRate) || 0;

                total += subtotal;
                count += quantity;

                // 税率ごとの合計を計算
                if (!rateTotals[rate]) rateTotals[rate] = 0;
                rateTotals[rate] += item.price * quantity;

                // オプション表示用の文字列を生成
                let optionsText = '';
                if (item.selectedOptionNames && item.selectedOptionNames.length > 0) {
                    optionsText = ` (${item.selectedOptionNames.join(', ')})`;
                }

                // テーブルに行を追加
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${item.menuName}${optionsText}</td>
                    <td class="text-center">${quantity}</td>
                    <td class="text-right">${subtotal}円</td>
                `;
                tbody.appendChild(row);
            });

            totalEl.textContent = `${total}円`;
            countEl.textContent = `${count}点`;

            // 税率ごとの合計を表示
            Object.entries(rateTotals)
                .sort((a, b) => a[0] - b[0])
                .forEach(([rate, amount]) => {
                    const line = document.createElement('div');
                    const percent = (parseFloat(rate) * 100).toFixed(0);
                    line.textContent = `${percent}%対象：¥${amount}(税別)`;
                    line.className = 'text-right';
                    taxEl.appendChild(line);
                });

            historyModal.classList.add('show');
            toggleBtn.textContent = "✕ 注文履歴を閉じる";
        })
        .catch(error => {
            console.error("注文履歴の取得に失敗しました:", error);
            const tbody = document.querySelector('#historyTable tbody');
            tbody.innerHTML = '<tr><td colspan="3">注文履歴の読み込み中にエラーが発生しました。</td></tr>';
        });
}


// グローバル関数として公開（外部からの呼び出し用）
window.toggleHistory = toggleHistory;

/**
 * 注文履歴モーダルを閉じる関数
 */
function closeHistoryModal() {
    document.getElementById('historyModal').classList.remove('show');
    document.getElementById("historyToggleButton").textContent = "注文履歴";
}

/**
 * カートパネルを開閉する関数
 * @param {boolean} [show] - trueで開く、falseで閉じる。指定なしでトグル。
 */
function toggleCart(show) {
    const cartPanel = document.getElementById("cartPanel");
    const toggleButton = document.getElementById("cartToggleButton");
    if (!cartPanel || !toggleButton) return;

    let isOpening;

    // show引数によって開閉を制御
    if (show === true) {
        cartPanel.classList.add("show");
        isOpening = true;
    } else if (show === false) {
        cartPanel.classList.remove("show");
        isOpening = false;
    } else {
        cartPanel.classList.toggle("show"); // showが指定されない場合はトグル
        isOpening = cartPanel.classList.contains("show");
    }

    // ボタンのテキストを切り替える
    if (isOpening) {
        toggleButton.textContent = "✕ カートを閉じる";
    } else {
        toggleButton.textContent = "🛒 カートを見る";
    }
}

/**
 * メニューアイテムの詳細表示をトグルする関数
 * @param {HTMLElement} elem - メニューアイテムの要素
 */
function toggleDetails(elem) {
    const detail = elem.querySelector(".menu-detail");
    const isExpanded = elem.classList.contains("expanded");

    // 閉じる処理
    if (isExpanded) {
        elem.style.height = elem.offsetHeight + "px"; // 現在の高さを設定
        elem.classList.remove("expanded");

        const onTransitionEnd = () => {
            detail.style.display = 'none'; // 詳細を非表示
            elem.removeEventListener('transitionend', onTransitionEnd);
            elem.style.height = ''; // アニメーション後にheightをクリア
        };
        elem.addEventListener('transitionend', onTransitionEnd);

        requestAnimationFrame(() => {
            elem.style.height = '200px'; // 閉じた時の初期の高さに戻す
        });

    } else {
        // 開く処理
        elem.classList.add("expanded");
        detail.style.display = 'block'; // 詳細を表示

        requestAnimationFrame(() => {
            requestAnimationFrame(() => { // ネストされたRAFで確実性を高める
                let fullHeight = elem.scrollHeight; // コンテンツ全体の高さを取得

                elem.style.height = fullHeight + "px"; // 全体の高さに設定して展開
                 setTimeout(() => {
                    elem.scrollIntoView({ behavior: 'smooth', block: 'end' });
                 }, 300); 
            });
        });
    }
}

// =============================================================================
// カート管理関数
// =============================================================================

/**
 * カート内の商品の数量を更新する関数
 * @param {number} index - カート配列内の商品のインデックス
 * @param {string} newVal - 新しい数量の文字列
 */
function updateQuantity(index, newVal) {
    const qty = parseInt(newVal);
    // 数量が有効な数値で1以上の場合のみ更新
    if (!isNaN(qty) && qty > 0) {
        cart[index].quantity = qty;
        updateMiniCart(); // ミニカートの表示も更新
    } else {
        showToast("数量は1以上を指定してください");
    }
}

/**
 * ミニカートの表示を更新する関数
 */
function updateMiniCart() {
    const list = document.getElementById('cartMiniList');
    const totalEl = document.getElementById('cartMiniTotal');
    const countEl = document.getElementById('cartMiniCount');
    const taxEl = document.getElementById('cartMiniTax');

    list.innerHTML = ''; // リストをクリア
    let total = 0; // 合計金額
    let totalCount = 0; // 合計点数
    const rateTotals = {}; // 税率ごとの税抜き合計金額 { 0.1: 1000, 0.08: 2000 }

    // ヘッダー行を追加
    const header = document.createElement('tr');
    header.innerHTML = `
        <th class="text-left">商品名</th>
        <th class="text-center">数量</th>
        <th class="text-right">小計</th>
        <th></th>
    `;
    list.appendChild(header);

    // カート内の各アイテムを処理
    cart.forEach((item, index) => {
        // taxRateMap は {ID: 率(10, 8)} の形式なので、0.1や0.08に変換して使う
        const taxRateValue = parseFloat(taxRateMap[item.taxRateId]) / 100;
        const subtotal = item.price * item.quantity * (1 + taxRateValue); // 税抜き価格から再計算
        const subtotalRounded = Math.round(subtotal); // 税込小計を四捨五入

        total += subtotalRounded; // 合計金額に加算
        totalCount += item.quantity; // 合計点数に加算

        // 税率別の税抜き価格合計を計算 (明細表示のため)
        if (!rateTotals[taxRateValue]) rateTotals[taxRateValue] = 0;
        rateTotals[taxRateValue] += item.price * item.quantity;

        // オプション表示用の文字列を生成
        let optionsText = '';
        if (item.selectedOptionNames && item.selectedOptionNames.length > 0) {
            optionsText = ` (${item.selectedOptionNames.join(', ')})`;
        }

        // 行を作成してリストに追加
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${item.name}${optionsText}</td>
            <td class="text-center">
                <input type="number" min="1" value="${item.quantity}" 
                        onchange="updateQuantity(${index}, this.value)" 
                        class="quantity-input" />
            </td>
            <td class="text-right">${subtotalRounded}円</td>
            <td><button onclick="removeFromCart(${index})">削除</button></td>
        `;
        list.appendChild(row);
    });

    totalEl.textContent = `${total}円`; // 合計金額を表示
    countEl.textContent = `${totalCount}点`; // 合計点数を表示

    taxEl.innerHTML = ''; // 税率ごとの表示エリアをクリア
    
    // 税率ごとの合計を表示
    Object.entries(rateTotals)
        .sort((a, b) => a[0] - b[0]) // 税率でソート
        .forEach(([rate, amount]) => {
            const line = document.createElement('div');
            // 税率をパーセンテージに変換して表示
            line.textContent = `${(parseFloat(rate) * 100).toFixed(0)}%対象：¥${Math.round(amount)}(税別)`; // 金額も丸める
            taxEl.appendChild(line);
        });

    // カートが空の場合の表示
    if (cart.length === 0) {
        list.innerHTML = `<tr><td colspan="4" class="text-center empty-cart">カートは空です</td></tr>`;
        totalEl.textContent = `0円`;
        countEl.textContent = `0点`;
        taxEl.innerHTML = '';
    }
}

/**
 * 商品をカートに追加する関数
 * @param {HTMLElement} button - 「カートに追加」ボタン要素
 */
function addToCart(button) {
    const menuId = button.getAttribute('data-menu-id');
    const taxRateId = button.getAttribute('data-tax-rate-id');
    const price = parseFloat(button.getAttribute('data-price'));
    const name = button.getAttribute('data-name');

    // ★★★ここを修正するよ！★★★
    // ボタンの親要素（menu-detail）から quantity-input を探す
    const menuDetail = button.closest('.menu-detail'); // 親の.menu-detail要素を取得
    const quantityInput = menuDetail.querySelector('.quantity-input'); // その中から.quantity-inputを探す
    
    const quantity = parseInt(quantityInput.value, 10); // 10進数として数値に変換

    // 数量のバリデーション
    if (isNaN(quantity) || quantity <= 0) {
        showToast('数量は1以上を入力してください。');
        return;
    }

    // オプションの選択状態をチェックする処理を追加
    const menuItem = button.closest('.menu-item');
    const optionSelects = menuItem.querySelectorAll('.option-select'); // このメニューアイテム内の全てのオプション選択欄を取得

    const selectedOptions = []; // 選択されたオプションアイテムのIDを格納する配列
    const selectedOptionNames = []; // 選択されたオプションアイテムの名前を格納する配列
    let optionsAllSelected = true; // 全てのオプションが選択されているかどうかのフラグ

    optionSelects.forEach(select => {
        if (select.value === "") { // 選択されていないオプションがある場合
            optionsAllSelected = false;
            return; // ループを中断
        }
        selectedOptions.push(parseInt(select.value)); // 選択されたオプションIDを追加
        // 選択された<option>要素のテキスト（オプション名）を取得
        const selectedText = select.options[select.selectedIndex].text;
        selectedOptionNames.push(selectedText); // 選択されたオプション名を追加
    });

    if (!optionsAllSelected) {
        showToast('全てのオプションを選択してください。',4000,'error');
        return; // カート追加処理を中断
    }

    // 税率をマップから取得し、10 -> 0.1 の形式に変換
    const taxRateValue = parseFloat(taxRateMap[taxRateId]) / 100;
    // 税込価格を計算し、四捨五入（これは表示用なので、内部データは税抜き価格と税率で持つ方が柔軟）
    const priceWithTax = Math.round(price * (1 + taxRateValue));

    // 既存の商品がカートにあるか確認（オプションも考慮して識別）
    // selectedOptionsをソートしてから文字列化して比較することで、順序が異なっても同じオプションセットとして認識
    const existing = cart.find(item =>
        item.menuId === menuId &&
        JSON.stringify(item.selectedOptions.slice().sort()) === JSON.stringify(selectedOptions.slice().sort()) 
    );

    if (existing) {
        existing.quantity += quantity; // 既存の商品があれば数量を加算
    } else {
        // なければ新しい商品としてカートに追加
        cart.push({ menuId, taxRateId, price, priceWithTax, quantity, name, selectedOptions, selectedOptionNames });
    }

    showToast("カートに追加しました"); // トーストメッセージを表示

    // メニューアイテムが展開状態であれば閉じる
    if (menuItem && menuItem.classList.contains('expanded')) {
        toggleDetails(menuItem);
    }

    updateMiniCart(); // ミニカートの表示を更新
}

/**
 * カートから商品を削除する関数
 * @param {number} index - 削除する商品のカート配列内のインデックス
 */
/**
 * カートから商品を削除する関数
 * @param {number} index - 削除する商品のカート内インデックス
 */
function removeFromCart(index) {
    // 削除ボタンのクリックイベントがメニューアイテムのクリックイベントに伝播しないように
    event.stopPropagation(); 
    cart.splice(index, 1);
    showToast("カートから削除しました");
    updateMiniCart();
}

// =============================================================================
// 注文処理関数
// =============================================================================

/**
 * 注文をサーバーに送信する関数
 * カート内の商品をまとめて注文として送信する
 */

/**
 * 注文を確定する関数
 */
function submitOrder() {
    if (cart.length === 0) {
        showToast('カートに商品がありません。');
        return;
    }
    
    const orderItems = cart.map(item => ({
        menuId: parseInt(item.menuId),
        taxRateId: parseInt(item.taxRateId),
        quantity: parseInt(item.quantity),
        optionItemIds: item.selectedOptions || []
    }));
    
    toggleCart(false);

    fetch('/order/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderItems)
    }).then(res => {
        if (res.ok) {
            cart.length = 0;
            updateMiniCart();
            showToast("注文を確定しました", 3000);
        } else {
            cart.splice(index, 1);
        }
    }).catch(error => {
        console.error('注文送信中にエラーが発生しました:', error);
        showToast('注文送信中にエラーが発生しました。ネットワーク接続を確認してください。');
    });
}


/**
 * 注文履歴モーダル内の表示を更新するためのフェッチ関数
 */
function fetchOrderHistoryForHistoryModal() {
    fetch('/order/history')
        .then(res => res.json())
        .then(data => {
            const tbody = document.querySelector('#historyTable tbody');
            const totalEl = document.getElementById('historyTotal');
            const countEl = document.getElementById('historyCount');
            const taxEl = document.getElementById('historyTax');
            tbody.innerHTML = '';
            taxEl.innerHTML = '';

            let total = 0;
            let count = 0;
            const rateTotals = {};

            data.forEach(item => {
                const subtotal = parseInt(item.subtotal) || 0;
                const quantity = parseInt(item.quantity) || 0;
                const rate = parseFloat(item.taxRate) || 0;

                total += subtotal;
                count += quantity;

                if (!rateTotals[rate]) rateTotals[rate] = 0;
                rateTotals[rate] += item.price * quantity; 

                let optionsText = '';
                if (item.selectedOptionNames && item.selectedOptionNames.length > 0) {
                    optionsText = ` (${item.selectedOptionNames.join(', ')})`;
                }

                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${item.menuName}${optionsText}</td>
                    <td class="text-center">${quantity}</td>
                    <td class="text-right">${subtotal}円</td>
                `;
                tbody.appendChild(row);
            });

            totalEl.textContent = `${total}円`;
            countEl.textContent = `${count}点`;

            Object.entries(rateTotals)
                .sort((a, b) => a[0] - b[0])
                .forEach(([rate, amount]) => {
                    const line = document.createElement('div');
                    const percent = (parseFloat(rate) * 100).toFixed(0);
                    line.textContent = `${percent}%対象：¥${Math.round(amount)}`;
                    line.style.textAlign = "right";
                    taxEl.appendChild(line);
                });
        })
        .catch(error => {
            console.error("注文履歴の再取得に失敗しました:", error);
        });
}


// メニュー表示関連処理
// -----------------------------------------------------------------------------

/**
 * データ属性から商品説明をアラートで表示する関数
 * @param {HTMLElement} btn - クリックされたボタン要素
 */
function showDescriptionFromData(btn) {
    const title = btn.getAttribute('data-name');
    const desc = btn.getAttribute('data-desc');
    showToast(`${title}\n\n${desc}`, 5000); // alertをshowToastに変更済み
}

// =============================================================================
// UI制御・ユーティリティ関数
// =============================================================================

/**
 * タブを切り替える関数
 * @param {HTMLElement} tabElement - クリックされたタブ要素
 */
function switchTab(tabElement) {
    // 全てのタブからactiveクラスを削除
    document.querySelectorAll('.menu-tab').forEach(t => t.classList.remove('active'));
    tabElement.classList.add('active');

    const groupId = tabElement.getAttribute('data-group-id');

    // 関連するメニューアイテムのみ表示し、他は非表示にする
    document.querySelectorAll('.menu-item').forEach(item => {
        const itemGroupId = item.getAttribute('data-group-id');
        
        if (itemGroupId === groupId) {
            item.classList.remove('d-none');
        } else {
            item.classList.add('d-none');
        }
    });
}

// =============================================================================
// イベントリスナーと初期化処理
// =============================================================================

// DOMコンテンツが完全にロードされた後に実行される処理
window.addEventListener('DOMContentLoaded', () => {
	// 座席情報を表示
	displaySeatInfo();
	
	document.querySelectorAll('.menu-item').forEach(menuItem => {
        const quantityInput = menuItem.querySelector('.quantity-input');
        const minusBtn = menuItem.querySelector('.minus-btn');
        const plusBtn = menuItem.querySelector('.plus-btn');

        // マイナスボタンのクリックイベント
        minusBtn.addEventListener('click', () => {
            let currentValue = parseInt(quantityInput.value);
            if (currentValue > parseInt(quantityInput.min)) { // min属性の値より大きい場合のみ減らす
                quantityInput.value = currentValue - 1;
            }
        });

        // プラスボタンのクリックイベント
        plusBtn.addEventListener('click', () => {
            let currentValue = parseInt(quantityInput.value);
            quantityInput.value = currentValue + 1; // 常に増やす
        });
    });
    // 税率情報をサーバーから取得してtaxRateMapに格納
    fetch('/taxrates')
        .then(res => res.json())
        .then(data => {
            data.forEach(rate => {
                // taxRateMapには 10% -> 10 の形式で保存
                taxRateMap[rate.taxRateId] = Math.round(rate.rate * 100); 
            });
        })
        .catch(err => {
            console.error("税率の取得に失敗しました", err);
        });
        
    // メニュータブにクリックイベントリスナーを設定
    document.querySelectorAll('.menu-tab').forEach(tab => {
        tab.addEventListener('click', () => switchTab(tab));
    });

    // 詳細情報ボタンにクリックイベントリスナーを設定
    document.querySelectorAll('.info-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation(); // 親要素へのイベント伝播を停止
            showDescriptionFromData(btn);
        });
    });

    // メニューアイテムのクリックで詳細をトグルするイベントリスナーを設定
    document.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', (e) => {
            const clicked = e.target;

            // クリックされた場所が画像、名前、価格のいずれかであれば詳細をトグル
            const isToggleTarget =
                clicked.closest('.menu-image-wrapper') ||
                clicked.closest('.menu-name') ||
                clicked.closest('.menu-price');

            if (isToggleTarget) {
                toggleDetails(item);
            }
        });
    });

    // カートに追加ボタンにクリックイベントリスナーを設定
    document.querySelectorAll('.add-cart-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation(); // 親要素へのイベント伝播を停止
            addToCart(btn);
        });
    });

    // 最初のタブを自動的にクリックして表示
    // ★修正：飲み放題開始後は、最初の飲み放題メニューグループをアクティブにする処理が必要
    const firstTab = document.querySelector('.menu-tab');
    if (firstTab) firstTab.click(); // 通常表示時の初期タブ選択


    // WebSocket接続の確立と購読
    const socket = new SockJS('/ws-endpoint');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, function () {
        if (typeof seatId !== 'undefined' && seatId !== null) {
            // Cookie整理処理
//            const rawUserId = getCookie("userId");
//            if (rawUserId === "null" || rawUserId === "undefined") {
//              document.cookie = "userId=; Max-Age=0; path=/"; // userIdが不正な値なら削除
//            }

            // 指定された座席のトピックを購読
            stompClient.subscribe(`/topic/seats/${seatId}`, function (message) {
                const body = JSON.parse(message.body);
                console.log("WebSocketメッセージ受信 (seatsトピック):", body);

                if (body.type === 'LEAVE') {
                    document.cookie = 'visitId=; Max-Age=0; path=/';
                    window.location.href = '/visits/orderwait';
                } else if (body.type === 'PLAN_ACTIVATED') {
                    const activatedMenuGroupIds = body.activatedMenuGroupIds;
                    const activatedPlanId = body.planId;
                    
                    console.log(`プラン ${activatedPlanId} がシート ${seatId} でアクティブ化されました。`);
                    console.log("表示されるメニューグループID:", activatedMenuGroupIds);

                    // まず全てのプラン対象タブとアイテムをリセット
                    document.querySelectorAll('.menu-tab[data-is-plan-target="true"]').forEach(tab => {
                        tab.classList.remove('active-plan-group');
                    });

                    // アクティブ化されたメニューグループのタブに active-plan-group を追加
                    // アクティブ化されたメニューアイテムから d-none を削除
                    activatedMenuGroupIds.forEach(groupId => {
                        const menuGroupTab = document.querySelector(`.menu-tab[data-group-id="${groupId}"]`);
                        if (menuGroupTab) {
                            menuGroupTab.classList.add('active-plan-group');
                        }
                        document.querySelectorAll(`.menu-item[data-group-id="${groupId}"]`).forEach(item => {
                            item.classList.remove('d-none');
                        });
                    });
                    
                    const currentUrl = new URL(window.location.href);
                    currentUrl.searchParams.set('toastMessage', '飲み放題が開始されました！メニューが増えました！');
                    window.location.href = currentUrl.toString();
                }
            }, function (error) {
                console.error('STOMP error:', error);
            });

            stompClient.subscribe(`/topic/printer/${seatId}`, function (message) {
                const payload = JSON.parse(message.body);
                console.log("WebSocketメッセージ受信 (printerトピック):", payload);
                if (payload.type === 'PRINT_COMMANDS') {
                    enqueuePrintJob(payload.ip, payload.commands);
                } else if (payload.type === 'PRINT_ERROR') {
                    alert('印刷エラー: ' + payload.message);
                    console.error('印刷エラー:', payload.message);
                    updatePrinterStatus('エラー: ' + payload.message);
                }
            }, function (error) {
                console.error('STOMP error for /topic/printer:', error);
                updatePrinterStatus('WebSocket購読エラー (printer): ' + error);
            });

        }
    });
});

window.addEventListener('click', (e) => {
    const cartPanel = document.getElementById("cartPanel");
    const toggleButton = document.getElementById("cartToggleButton");
    if (cartPanel && toggleButton) {
        const isClickInsideCart =
            cartPanel.contains(e.target) ||
            e.target.closest('.cart-button');

        if (cartPanel.classList.contains('show') && !isClickInsideCart) {
            toggleCart(false);
        }
    }

    const historyModal = document.getElementById('historyModal');
    const historyToggleBtn = document.getElementById("historyToggleButton");
    if (historyModal && historyToggleBtn) {
        if (
            historyModal.classList.contains('show') &&
            !historyModal.contains(e.target) &&
            !e.target.closest('.history-button')
        ) {
            closeHistoryModal();
        }
    }
    
    document.querySelectorAll('.menu-item.expanded').forEach(item => {
        if (!item.contains(e.target)) {
            toggleDetails(item);
        }
    });
});

document.getElementById("backToSeatList").addEventListener("click", function () {
    document.cookie = "visitId=; Max-Age=0; path=/";
});

window.onload = () => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("from") === "seatlist") {
        document.getElementById("backToSeatList").style.display = "block";
    }
    updateMiniCart();
    
    handleUrlToastMessage();

    // data-is-plan-target でないタブを優先的に選択
    const firstNonPlanTargetTab = document.querySelector('.menu-tab:not([data-is-plan-target="true"])');
    if (firstNonPlanTargetTab) {
        switchTab(firstNonPlanTargetTab);
    } else {
        const anyTab = document.querySelector('.menu-tab');
        if (anyTab) {
            switchTab(anyTab);
        }
    }
};

function activatePlanGroups(groupIds) {
    groupIds.forEach(groupId => {
        const menuGroupTab = document.querySelector(`.menu-tab[data-group-id="${groupId}"]`);
        if (menuGroupTab) {
            menuGroupTab.classList.add('active-plan-group');
        }
        document.querySelectorAll(`.menu-item[data-group-id="${groupId}"]`).forEach(item => {
            item.classList.remove('d-none');
        });
    });
}