// グローバル変数と初期設定
// -----------------------------------------------------------------------------
const cart = []; // カートの中身を保持する配列
let taxRateMap = {}; // 税率IDと税率をマッピングするオブジェクト

// 座席情報の表示
// CookieからseatIdを取得、なければURLから取得することも想定
const seatId = getCookie("seatId"); 
document.getElementById("seatInfo").innerText = `${seatId}`; // 取得したseatIdを画面に表示

/**
 * 指定された名前のCookieの値を取得する関数
 * @param {string} name - 取得したいCookieの名前
 * @returns {string|null} Cookieの値、またはnull
 */
function getCookie(name) {
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
    return match ? decodeURIComponent(match[2]) : null;
}

/**
 * トーストメッセージを表示する関数
 * @param {string} message - 表示するメッセージ
 * @param {number} [duration=2000] - 表示時間 (ミリ秒)
 */
function showToast(message, duration = 2000) {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.style.display = "block"; // 表示
    toast.style.opacity = "1"; // フェードイン

    setTimeout(() => {
        toast.style.opacity = "0"; // フェードアウト
        setTimeout(() => {
            toast.style.display = "none"; // 非表示
        }, 500); // フェードアウト後に非表示
    }, duration);
}

// モーダル・パネルの開閉処理
// -----------------------------------------------------------------------------

/**
 * 注文履歴モーダルを開閉する関数
 */
function toggleHistory() {
    const historyModal = document.getElementById("historyModal");
    const toggleBtn = document.getElementById("historyToggleButton");

    // モーダルが表示されている場合は閉じる
    if (historyModal.classList.contains("show")) {
        historyModal.classList.remove("show");
        toggleBtn.textContent = "注文履歴"; // ボタンのテキストを「注文履歴」に戻す
    } else {
        // モーダルが閉じている場合は、履歴を取得して表示
        fetch('/order/history')
            .then(res => res.json())
            .then(data => {
                const tbody = document.querySelector('#historyTable tbody');
                const totalEl = document.getElementById('historyTotal');
                const countEl = document.getElementById('historyCount');
                const taxEl = document.getElementById('historyTax'); // 税率ごとの合計表示エリア
                tbody.innerHTML = ''; // テーブルの中身をクリア
                taxEl.innerHTML = ''; // 税率ごとの合計表示エリアをクリア

                let total = 0; // 合計金額
                let count = 0; // 合計点数
                const rateTotals = {}; // 税率ごとの合計金額を保持 { 10: 1000, 8: 500 } の形式

                // 取得した履歴データをループして表示を生成
                data.forEach(item => {
                    const subtotal = parseInt(item.subtotal) || 0; // 小計
                    const quantity = parseInt(item.quantity) || 0; // 数量
                    // バックエンドから返される税率は0.1や0.08の形なので、そのまま使う
                    const rate = parseFloat(item.taxRate) || 0; 

                    total += subtotal; // 合計金額に加算
                    count += quantity; // 合計点数に加算

                    // 税率ごとの合計を計算 (税抜きの価格で計算し直す)
                    // item.price は税抜きの単価としてバックエンドから返される前提
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
                        <td style="text-align: center;">${quantity}</td>
                        <td style="text-align: right;">${subtotal}円</td>
                    `;
                    tbody.appendChild(row);
                });

                totalEl.textContent = `${total}円`; // 合計金額を表示
                countEl.textContent = `${count}点`; // 合計点数を表示

                // 税率ごとの合計を表示
                Object.entries(rateTotals)
                    .sort((a, b) => a[0] - b[0]) // 税率でソート
                    .forEach(([rate, amount]) => {
                        const line = document.createElement('div');
                        // 税率をパーセンテージに変換 (例: 0.1 -> 10%)
                        const percent = (parseFloat(rate) * 100).toFixed(0); 
                        line.textContent = `${percent}%対象：¥${amount}(税別)`;
                        line.style.textAlign = "right";
                        taxEl.appendChild(line);
                    });

                historyModal.classList.add('show'); // モーダルを表示
                toggleBtn.textContent = "✕ 注文履歴を閉じる"; // ボタンのテキストを「閉じる」に変更
            })
            .catch(error => {
                console.error("注文履歴の取得に失敗しました:", error);
                const tbody = document.querySelector('#historyTable tbody');
                tbody.innerHTML = '<tr><td colspan="3">注文履歴の読み込み中にエラーが発生しました。</td></tr>';
            });
    }
}
window.toggleHistory = toggleHistory; // グローバルに公開（外部からの呼び出し用）

/**
 * 注文履歴モーダルを閉じる関数
 */
function closeHistoryModal() {
    document.getElementById('historyModal').classList.remove('show');
    document.getElementById("historyToggleButton").textContent = "注文履歴"; // ボタンのテキストを戻す
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
            elem.style.height = '180px'; // 閉じた時の初期の高さに戻す
        });

    } else {
        // 開く処理
        elem.classList.add("expanded");
        detail.style.display = 'block'; // 詳細を表示

        requestAnimationFrame(() => {
            requestAnimationFrame(() => { // ネストされたRAFで確実性を高める
                let fullHeight = elem.scrollHeight; // コンテンツ全体の高さを取得

                elem.style.height = fullHeight + "px"; // 全体の高さに設定して展開
            });
        });
    }
}

// カート関連処理
// -----------------------------------------------------------------------------

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
        <th style="text-align: left;">商品名</th>
        <th style="text-align: center;">数量</th>
        <th style="text-align: right;">小計</th>
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
            <td style="text-align: center;">
                <input type="number" min="1" value="${item.quantity}" 
                        onchange="updateQuantity(${index}, this.value)" 
                        style="width: 50px;" />
            </td>
            <td style="text-align: right;">${subtotalRounded}円</td>
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
        list.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 10px;">カートは空です</td></tr>`;
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
    const quantityInput = button.previousElementSibling; // 数量入力欄はボタンの直前にある
    const quantity = parseInt(quantityInput.value);

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
        showToast('全てのオプションを選択してください。');
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
function removeFromCart(index) {
    // 削除ボタンのクリックイベントがメニューアイテムのクリックイベントに伝播しないように
    event.stopPropagation(); 
    cart.splice(index, 1); // 指定されたインデックスの要素を削除
    showToast("カートから削除しました");
    updateMiniCart(); // ミニカートの表示を更新
}

/**
 * 注文を確定する関数
 */
function submitOrder() {
    if (cart.length === 0) {
        showToast('カートに商品がありません。');
        return;
    }
    
    // カートの内容を注文データとして整形
    const orderItems = cart.map(item => ({
        menuId: parseInt(item.menuId),
        taxRateId: parseInt(item.taxRateId),
        quantity: parseInt(item.quantity),
        // ここで選択されたオプションを追加する
        optionItemIds: item.selectedOptions || [] // オプションがない場合は空の配列
    }));
    
    // toggleCart(false) を呼び出してカートパネルを閉じ、ボタンテキストを戻す
    toggleCart(false); 

    // 注文データをサーバーにPOST送信
    fetch('/order/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderItems)
    }).then(res => {
        if (res.ok) {
            const currentUrl = new URL(window.location.href);
            currentUrl.searchParams.set('toastMessage', '注文を確定しました');
            window.location.href = currentUrl.toString(); // クエリパラメータ付きでリロード
        } else {
            showToast('注文に失敗しました');
        }
    }).catch(error => {
        console.error('注文送信中にエラーが発生しました:', error);
        showToast('注文送信中にエラーが発生しました。ネットワーク接続を確認してください。');
    });
}


/**
 * 注文履歴モーダル内の表示を更新するためのフェッチ関数
 * toggleHistory とは独立させて、自動で開閉しないようにする
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
                    <td style="text-align: center;">${quantity}</td>
                    <td style="text-align: right;">${subtotal}円</td>
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
            
            // カート確定後の履歴更新なので、モーダルが開いていなければ閉じっぱなし
            // 開いている場合はそのまま更新される
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

/**
 * タブを切り替える関数
 * @param {HTMLElement} tabElement - クリックされたタブ要素
 */
function switchTab(tabElement) {
    // 全てのタブからactiveクラスを削除
    document.querySelectorAll('.menu-tab').forEach(t => t.classList.remove('active'));
    tabElement.classList.add('active'); // クリックされたタブにactiveクラスを追加
    
    const groupId = tabElement.getAttribute('data-group-id'); // タブのgroup-idを取得
    
    // 関連するメニューアイテムのみ表示し、他は非表示にする
    document.querySelectorAll('.menu-item').forEach(item => {
        const itemGroupId = item.getAttribute('data-group-id');
        const isPlanTargetMenu = item.getAttribute('data-is-plan-target') === 'true';
        const isActivePlanMenu = item.classList.contains('active-plan-menu'); // WebSocketで追加されるクラス

        if (isActivePlanMenu) { // ★修正点1: 飲み放題でアクティブなら常に表示を優先
            item.style.display = 'block'; 
        } else if (isPlanTargetMenu) { // ★修正点2: 飲み放題対象で、かつアクティブでないものは非表示
            item.style.display = 'none';
        } else { // ★修正点3: 通常メニューは選択されたタブのグループIDに一致するものだけ表示
            item.style.display = (itemGroupId === groupId) ? 'block' : 'none';
        }
    });
}


// イベントリスナーと初期化
// -----------------------------------------------------------------------------

// DOMコンテンツが完全にロードされた後に実行される処理
window.addEventListener('DOMContentLoaded', () => {
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
            const rawUserId = getCookie("userId");
            if (rawUserId === "null" || rawUserId === "undefined") {
              document.cookie = "userId=; Max-Age=0; path=/"; // userIdが不正な値なら削除
            }

            // 指定された座席のトピックを購読
           stompClient.subscribe(`/topic/seats/${seatId}`, function (message) {
                const body = JSON.parse(message.body);
                console.log("WebSocketメッセージ受信:", body);

                if (body.type === 'LEAVE') {
                    // ... 離席処理 ...
                } else if (body.type === 'PLAN_ACTIVATED') {
                    const activatedMenuGroupIds = body.activatedMenuGroupIds;
                    const activatedPlanId = body.planId;
                    
                    console.log(`プラン ${activatedPlanId} がシート ${seatId} でアクティブ化されました。`);
                    console.log("表示されるメニューグループID:", activatedMenuGroupIds);

                    // Step 1: 全てのタブとメニューアイテムを初期状態（非表示）に戻す
                    // （isPlanTargetでないものは、後でswitchTabで表示されるため、ここでは触らない）
                    document.querySelectorAll('.menu-tab[data-is-plan-target="true"]').forEach(tab => {
                        tab.classList.remove('active-plan-group');
                        // ★修正: ここでstyle.display = 'none'; はCSSに任せる
                    });
                    document.querySelectorAll('.menu-item[data-is-plan-target="true"]').forEach(item => {
                        item.classList.remove('active-plan-menu');
                        // ★修正: ここでstyle.display = 'none'; はCSSに任せる
                    });
                    // もし、現在表示されているメニュー（activeクラスがついてるタブのメニュー）を一旦全部隠したいなら、
                    // document.querySelectorAll('.menu-item').forEach(item => item.style.display = 'none');
                    // のような処理も検討する。ただし、switchTabで表示されるはずなので、このままでOKの可能性が高い。


                    // Step 2: 活性化されたメニューグループのタブとメニューアイテムを表示する
                    activatedMenuGroupIds.forEach(groupId => {
                        // メニューグループのタブを表示
                        const menuGroupTab = document.querySelector(`.menu-tab[data-group-id="${groupId}"]`);
                        if (menuGroupTab) {
                            menuGroupTab.classList.add('active-plan-group'); // CSSで表示
                            // ★修正: ここでstyle.display = 'block'; はCSSに任せる
                        }
                        // そのグループに属するメニューアイテムを表示
                        document.querySelectorAll(`.menu-item[data-group-id="${groupId}"]`).forEach(item => {
                            item.classList.add('active-plan-menu'); // CSSで表示
                            // ★修正: ここでstyle.display = 'block'; はCSSに任せる
                        });
                    });

                    showToast("飲み放題が開始されました！メニューが増えました！", 3000);

                    // Step 3: 最初の飲み放題対象グループのタブを自動でクリックする
                    if (activatedMenuGroupIds && activatedMenuGroupIds.length > 0) {
                        const firstActivatedTab = document.querySelector(`.menu-tab[data-group-id="${activatedMenuGroupIds[0]}"]`);
                        if (firstActivatedTab) {
                            switchTab(firstActivatedTab); 
                        }
                    }
                }
            }, function (error) {
                console.error('STOMP error:', error);
                // エラー処理、再接続の試行など
            });
        }
    });
});

// ウィンドウ全体のクリックイベントリスナー
window.addEventListener('click', (e) => {
    // カートパネル以外の部分をクリックしたら閉じる
    const cartPanel = document.getElementById("cartPanel");
    const toggleButton = document.getElementById("cartToggleButton");
    if (cartPanel && toggleButton) { // 要素が存在するかチェック
        const isClickInsideCart =
            cartPanel.contains(e.target) ||
            e.target.closest('.cart-button');

        if (cartPanel.classList.contains('show') && !isClickInsideCart) {
            // toggleCart(false) を呼び出してカートパネルを閉じ、ボタンテキストを戻す
            toggleCart(false);
        }
    }

    // 履歴モーダル以外の部分をクリックしたら閉じる
    const historyModal = document.getElementById('historyModal');
    const historyToggleBtn = document.getElementById("historyToggleButton");
    if (historyModal && historyToggleBtn) { // 要素が存在するかチェック
        if (
            historyModal.classList.contains('show') &&
            !historyModal.contains(e.target) &&
            !e.target.closest('.history-button')
        ) {
            closeHistoryModal(); // 履歴モーダルを閉じる
        }
    }
    
    // 展開されているメニューアイテムがクリックされた場所以外なら閉じる
    document.querySelectorAll('.menu-item.expanded').forEach(item => {
        if (!item.contains(e.target)) {
            toggleDetails(item); // 閉じる
        }
    });
});

// 「座席選択に戻る」ボタンのイベントリスナー
document.getElementById("backToSeatList").addEventListener("click", function () {
    document.cookie = "visitId=; Max-Age=0; path=/"; // visitIdを削除
});

// ページロード時の処理
window.onload = () => {
    const params = new URLSearchParams(window.location.search);
    // URLパラメータに "from=seatlist" があれば、「座席選択に戻る」ボタンを表示
    if (params.get("from") === "seatlist") {
        document.getElementById("backToSeatList").style.display = "block";
    }
    // ページロード時にミニカートを初期化表示
    updateMiniCart();

    // ★URLパラメータにtoastMessageがあれば表示
    const urlParams = new URLSearchParams(window.location.search);
    const successMessage = urlParams.get('toastMessage');
    if (successMessage) {
        showToast(successMessage, 3000); // 3秒間表示
        // 表示後、クエリパラメータを削除してURLをクリーンにする (History APIを使用)
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // ★重要: ページロード時に現在の飲み放題状態に基づいてメニューグループの表示を調整
    // WebSocketからの通知だけでなく、初期表示でも正しい状態にする必要がある
    // サーバサイドから渡されたmenuGroupsの情報を使って処理する
    const allMenuTabs = document.querySelectorAll('.menu-tab');
    const allMenuItems = document.querySelectorAll('.menu-item');

    // 初期表示時に、isPlanTarget="true" のものを非表示にする
    // ★修正: ここでstyle.displayを直接操作するのをやめる。CSSに任せる
    // allMenuTabs.forEach(tab => {
    //     if (tab.getAttribute('data-is-plan-target') === 'true') {
    //         tab.style.display = 'none'; // この行を削除
    //     }
    // });
    // allMenuItems.forEach(item => {
    //     if (item.getAttribute('data-is-plan-target') === 'true') {
    //         item.style.display = 'none'; // この行を削除
    //     }
    // });

    // まずは、初回表示時に最初のタブをアクティブにする処理
    const firstNonPlanTargetTab = document.querySelector('.menu-tab:not([data-is-plan-target="true"])');
    if (firstNonPlanTargetTab) {
        // ★修正: ここでstyle.displayを直接操作するのをやめる。CSSに任せる
        // firstNonPlanTargetTab.style.display = 'block'; // この行を削除
        switchTab(firstNonPlanTargetTab);
    } else {
        // 全てがisPlanTarget=trueの場合（＝何も表示されない場合）
        // 最初のタブ（どれでもいい）をアクティブにする
        const anyTab = document.querySelector('.menu-tab');
        if (anyTab) {
            // ★修正: ここでstyle.displayを直接操作するのをやめる。CSSに任せる
            // anyTab.style.display = 'block'; // この行を削除
            switchTab(anyTab);
        }
    }
};

/**
 * 指定されたメニューグループIDのタブとメニューアイテムを表示状態にする関数
 * @param {Array<Number>} groupIds - 表示するメニューグループIDのリスト
 */
function activatePlanGroups(groupIds) {
    groupIds.forEach(groupId => {
        // メニューグループのタブを表示
        const menuGroupTab = document.querySelector(`.menu-tab[data-group-id="${groupId}"]`);
        if (menuGroupTab) {
            menuGroupTab.classList.add('active-plan-group'); // CSSで表示
            // ★修正: ここでstyle.display = 'block'; はCSSに任せる
        }
        // そのグループに属するメニューアイテムを表示
        document.querySelectorAll(`.menu-item[data-group-id="${groupId}"]`).forEach(item => {
            item.classList.add('active-plan-menu'); // CSSで表示
            // ★修正: ここでstyle.display = 'block'; はCSSに任せる
        });
    });
}