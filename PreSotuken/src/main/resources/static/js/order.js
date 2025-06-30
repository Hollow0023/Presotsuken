// Epson EPOS SDK関連のグローバル変数
// -----------------------------------------------------------------------------
let printer = null; // Epson Printerオブジェクト
const ePosDev = new epson.ePOSDevice(); // Epson ePOSDeviceオブジェクト
let currentPrinterIp = null; // 現在接続中のプリンターのIPアドレスを保持

// 印刷ジョブのキューと状態
const printJobQueue = [];
let isPrinting = false;

//印刷キュー追加関数
function enqueuePrintJob(ip, commandsJson, retryCount = 0) {
    printJobQueue.push({ ip, commandsJson, retryCount });
    if (!isPrinting) {
        processPrintJobs();
    }
}

//キュー処理
async function processPrintJobs() {
    if (printJobQueue.length === 0) {
        isPrinting = false;
        return;
    }

    isPrinting = true;
    const { ip, commandsJson, retryCount } = printJobQueue.shift();

    try {
        if (!printer || !ePosDev.isConnected || currentPrinterIp !== ip) {
            if (printer) {
                await new Promise(resolve => {
                    ePosDev.deleteDevice(printer, () => {
                        ePosDev.disconnect();
                        printer = null;
                        resolve();
                    });
                });
            }
            await connectAndExecute(ip, commandsJson);
        } else {
            await executeCommands(commandsJson);
        }
    } catch (e) {
        console.error("印刷エラー:", e);
        updateStatus(`印刷エラー: ${e.message}`);

        if (retryCount < 3) {
            console.warn(`リトライ ${retryCount + 1} 回目: 再キューします`);
            enqueuePrintJob(ip, commandsJson, retryCount + 1);
        } else {
            showToast("印刷に3回失敗しました。プリンタの状態を確認してください。", 4000, 'error');
        }
    }

    processPrintJobs(); // 次のジョブを処理
}

// UIステータス表示用の要素
// HTMLのbodyタグ直後などに <p id="statusMessage">プリンタステータス: 初期化中...</p> を追加してください
const statusMessageElement = document.getElementById("statusMessage"); 

function updateStatus(message) {
    if (statusMessageElement) {
        statusMessageElement.textContent = `プリンタステータス: ${message}`;
    }
    console.log(`[Printer Status] ${message}`); // コンソールにも出力
}

// プリンターへの接続とコマンド実行のラッパー関数
async function connectAndExecute(ipAddress, commandsJson) {
    // 既存の接続を切断する前に、現在のプリンターオブジェクトを解放
    if (printer && ePosDev.isConnected) {
        try {
            await new Promise(resolve => {
                ePosDev.deleteDevice(printer, () => {
                    ePosDev.disconnect();
                    printer = null;
                    resolve();
                });
            });
            console.log('既存プリンター接続を安全に切断しました。');
        } catch (error) {
            console.warn('既存プリンター切断中にエラー:', error);
        }
    }

    return new Promise((resolve, reject) => {
        ePosDev.connect(ipAddress, 8008, function(result) { // ポートは固定とするか、設定で取得
            if (result === 'OK' || result === 'SSL_CONNECT_OK') {
                console.log("接続成功。プリンタデバイス作成中...");
                currentPrinterIp = ipAddress; // 接続したIPを記録
                ePosDev.createDevice(
                    'local_printer',
                    ePosDev.DEVICE_TYPE_PRINTER,
                    { crypto: false, buffer: false }, // ポート8008なら crypto: false
                    function(devobj, retcode) {
                        if (retcode === 'OK') {
                            printer = devobj;
                            setupPrinterEvents(printer); // イベントハンドラを設定
                            console.log('プリンタデバイス作成成功。コマンド実行中...');
                            updateStatus('印刷コマンド実行中...');
                            executeCommands(commandsJson).then(resolve).catch(reject); // コマンド実行を待つ
                        } else {
                            console.error("デバイス作成失敗:", retcode);
                            updateStatus('プリンタデバイス作成失敗: ' + retcode);
                            reject(new Error("Device creation failed: " + retcode));
                        }
                    }
                );
            } else {
                console.error("プリンタ接続失敗:", result);
                updateStatus('プリンタ接続失敗: ' + result);
                reject(new Error("Printer connection failed: " + result));
            }
        });
    });
}

// プリンターイベントハンドラのセットアップ
function setupPrinterEvents(printerObj) {
    printerObj.timeout = 60000; // タイムアウト設定 
    printerObj.onreceive = function(response) { 
        if (response.success) { 
            console.log('印刷成功！');
            updateStatus('印刷完了');
        } else { 
            console.error('印刷失敗:', response.code); 
            updateStatus('印刷失敗: ' + response.code);
        }
 	};
    printerObj.onstatuschange = function(status) { 
        console.log('プリンタステータス変更:', status);
        // ASB_RECEIPT_NEAR_END などで用紙補充を促すなど
    };
    printerObj.onpaperend = function() { 
        console.warn('用紙切れ！');
        updateStatus('警告: 用紙切れ！');
    };
    printerObj.oncoveropen = function() {
        console.warn('カバーオープン！');
        updateStatus('警告: カバーオープン！');
    };
    // ondisconnectイベントでネットワーク切断の検知も重要 
    ePosDev.ondisconnect = function() { 
        console.warn('プリンタとの接続が切断されました！');
        updateStatus('警告: プリンタ切断！');
        printer = null; // プリンターオブジェクトをリセット
        currentPrinterIp = null;
    };
    // onreconnecting / onreconnect も設定しておくとより堅牢
    ePosDev.onreconnecting = function() { 
        console.log('プリンタに再接続中...');
        updateStatus('プリンタに再接続中...');
    };
    ePosDev.onreconnect = function() {
        console.log('プリンタに再接続しました！');
        updateStatus('プリンタ再接続完了');
    };
}

// 印刷命令リストを実行するコア関数
async function executeCommands(commandsJson) {
    let commands = JSON.parse(commandsJson);
    commands = commands.flat();

    // 印刷開始前にプリンターの状態を初期化する
    // 必要に応じて printer.reset() を呼び出すが、各コマンドで設定を上書きするなら不要な場合も
    // printer.addReset(); // またはprinter.reset()

    for (const command of commands) {
        try {
            switch (command.api) {
                case "addSound": // 
                    printer.addSound(
                        command.pattern,
                        command.repeat,
                        command.cycle // cycleはオプション、存在しない場合はundefined
                    );
                    break;
                case "addTextLang": // 
                    printer.addTextLang(command.lang);
                    break;
                case "addFeedUnit": // 
                    printer.addFeedUnit(command.unit);
                    break;
                case "addText": // 
                    // アラインメント設定
                    if (command.align) { 
                        printer.addTextAlign(printer[`ALIGN_${command.align.toUpperCase()}`]); // 
                    } else {
                        printer.addTextAlign(printer.ALIGN_LEFT); // デフォルト 
                    }
                    // 倍角設定
                    if (command.dw !== undefined && command.dh !== undefined) {
                       printer.addTextDouble(command.dw, command.dh); // 
                    } else {
//                       printer.addTextDouble(false, false); // デフォルトに戻す 
                    }

                    // テキストサイズ設定
                    if (command.width !== undefined && command.height !== undefined) {
                        printer.addTextSize(command.width, command.height); // 
                    } else {
//                        printer.addTextSize(1, 1); // デフォルトに戻す 
                    }
                    // テキストスタイル設定
                    if (command.reverse !== undefined && command.ul !== undefined && command.em !== undefined && command.color) {
                        printer.addTextStyle(command.reverse, command.ul, command.em, printer[command.color]); // 
                    } else {
                        printer.addTextStyle(false, false, false, printer.COLOR_1); // デフォルトスタイルに戻す 
                    }
                    printer.addText(command.content + '\n'); // 改行はJava側で含めていても良いし、JSで追加しても良い 
                    break;
                case "addTextAlign": // 
                    printer.addTextAlign(printer[`ALIGN_${command.align.toUpperCase()}`]);
                    break;
                case "addTextDouble": // 
                    printer.addTextDouble(command.dw, command.dh);
                    console.log("倍角");
                    break;
                case "addTextSize": // 
                    printer.addTextSize(command.width, command.height);
                    break;
                case "addTextStyle": // 
                    printer.addTextStyle(command.reverse, command.ul, command.em, printer[command.color]);
                    break;
                case "addFeed": // 
                    printer.addFeed();
                    break;
                case "addCut": // 
                    printer.addCut(printer[`CUT_${command.type.toUpperCase()}`]);
                    break;
                case "addImage": // 
                    // 画像のBase64データ処理は非同期なので、Promiseで完了を待つ
                    await new Promise((resolve, reject) => {
                        const img = new Image();
                        img.onload = function() {
                            const canvas = document.createElement('canvas');
                            canvas.width = command.width;
                            canvas.height = command.height;
                            const ctx = canvas.getContext('2d');
                            ctx.drawImage(img, 0, 0, command.width, command.height);

                            printer.addImage(
                                ctx,
                                command.x,
                                command.y,
                                command.width,
                                command.height,
                                printer[command.color], // COLOR_1 など 
                                printer['MODE_' + command.mode.toUpperCase()] // MODE_MONO など 
                            );
                            resolve(); // 描画完了を通知
                        };
                        img.onerror = function() {
                            console.error("画像読み込みエラー:", command.base64Content.substring(0, 50) + '...');
                            reject(new Error("Image load error"));
                        };
                        img.src = "data:image/png;base64," + command.base64Content;
                    });
                    break;
                case "addTextFont": 
                    printer.addTextFont(printer[command.font]);
                    break;
                // 必要に応じて他のAPI（addBarcode, addSymbolなど）も追加
                default:
                  console.warn("未対応のAPIコマンド:", command.api, command);
            }
        } catch (e) {
            console.error(`コマンド実行中にエラーが発生しました: ${command.api}`, e);
            updateStatus(`コマンド実行エラー: ${command.api}`);
            // エラーが発生したら、それ以上コマンドを送らないようにする
            break;
        }
    }
    
    let logOutput = commands.map(cmd => {
    return `[${cmd.api}] ${JSON.stringify(cmd)}`;
    }).join('\n');

    console.log("=== 印刷コマンド変換ログ ===\n" + logOutput);
    console.log(commands);
	console.log(printer);
	console.log("印刷はコメントアウト中 224行")
    printer.send(); // 
}

// ここまで↑、Epson EPOS SDK関連の関数群をグローバルスコープに配置

//---
//## WebSocket再接続ロジック用のグローバル変数
//---
let stompClient = null; // STOMPクライアントインスタンスを保持
let reconnectAttempts = 0; // 現在の再接続試行回数
const maxReconnectAttempts = 5; // 最大再接続回数を5回に設定
const reconnectInterval = 5000; // 再接続間隔を5秒に設定 (5000ミリ秒)
let isManualReloadPrompted = false; // 手動リロードのアラートがすでに表示されたかどうかのフラグ

// WebSocket接続とSTOMPクライアントの接続を試みる関数
function tryConnectStomp() {
    // すでに最大試行回数に達してアラートを出している場合は、それ以上何もしない
    if (isManualReloadPrompted) {
        return;
    }

    // 最大再接続回数を超えたら、手動リロードを促す
    if (reconnectAttempts >= maxReconnectAttempts) {
        console.log("最大再接続回数に達しました。手動リロードを促します。");
        // ここでUIに「接続失敗」などのメッセージを表示する場所があれば更新
        if (document.getElementById('statusMessage')) {
            document.getElementById('statusMessage').textContent = "WebSocket接続に失敗しました。ページをリロードしてください。";
        }
        showManualReloadPrompt(); // 手動リロードのアラートを出す
        return; // これ以上再接続は試みない
    }

    console.log(`STOMP接続試行中... (${reconnectAttempts + 1}/${maxReconnectAttempts}回目)`);
    // UIに現在の試行回数を表示する場所があれば更新
    if (document.getElementById('statusMessage')) {
        document.getElementById('statusMessage').textContent = `STOMP接続試行中... (${reconnectAttempts + 1}/${maxReconnectAttempts}回目)`;
    }

    const socket = new SockJS('/ws-endpoint');
    stompClient = Stomp.over(socket);

    // STOMPのデバッグログを抑制したい場合はコメント解除（本番環境では推奨）
    // stompClient.debug = null;

    // STOMPクライアントの接続
    stompClient.connect({}, function (frame) {
        // 接続成功時の処理
        console.log('STOMP Connection established: ' + frame);
        if (document.getElementById('statusMessage')) {
            document.getElementById('statusMessage').textContent = "WebSocket接続中...";
        }
        reconnectAttempts = 0; // 接続成功したらリトライ回数をリセット
        isManualReloadPrompted = false; // 接続成功したらアラートフラグもリセット

        // ここから既存の購読処理
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
                    document.cookie = 'visitId=; Max-Age=0; path=/'; // visitIdを削除（セッション切れ用）
                    window.location.href = '/visits/orderwait';
                } else if (body.type === 'PLAN_ACTIVATED') {
                    const activatedMenuGroupIds = body.activatedMenuGroupIds;
                    const activatedPlanId = body.planId;
                    
                    console.log(`プラン ${activatedPlanId} がシート ${seatId} でアクティブ化されました。`);
                    console.log("表示されるメニューグループID:", activatedMenuGroupIds);
                    
                    // Step 1: 全てのタブとメニューアイテムを初期状態（非表示）に戻す
                    document.querySelectorAll('.menu-tab[data-is-plan-target="true"]').forEach(tab => {
                        tab.classList.remove('active-plan-group');
                    });
                    document.querySelectorAll('.menu-item[data-is-plan-target="true"]').forEach(item => {
                        item.classList.remove('active-plan-menu');
                    });

                    // Step 2: 活性化されたメニューグループのタブとメニューアイテムを表示する
                    activatedMenuGroupIds.forEach(groupId => {
                        const menuGroupTab = document.querySelector(`.menu-tab[data-group-id="${groupId}"]`);
                        if (menuGroupTab) {
                            menuGroupTab.classList.add('active-plan-group');
                        }
                        document.querySelectorAll(`.menu-item[data-group-id="${groupId}"]`).forEach(item => {
                            item.classList.add('active-plan-menu');
                        });
                    });
                    
                    const currentUrl = new URL(window.location.href);
		            currentUrl.searchParams.set('toastMessage', '飲み放題が開始されました！メニューが増えました！');
		            window.location.href = currentUrl.toString(); // クエリパラメータ付きでリロード
                }
            }, function (error) { // seatsトピックの購読エラーハンドラ
                console.error('STOMP subscribe error for /topic/seats:', error);
            });

            // printerトピックの購読
            stompClient.subscribe(`/topic/printer/${seatId}`, function (message) {
                const payload = JSON.parse(message.body);
                console.log("WebSocketメッセージ受信 (printerトピック):", payload);
                if (payload.type === 'PRINT_COMMANDS') {
                    if (typeof enqueuePrintJob === 'function') {
                        enqueuePrintJob(payload.ip, payload.commands);
                    } else {
                        console.warn("enqueuePrintJob関数が定義されていません。");
                    }
                } else if (payload.type === 'PRINT_ERROR') {
                    alert('印刷エラー: ' + payload.message);
                    console.error('印刷エラー:', payload.message);
                    // if (typeof updateStatus === 'function') {
                    //     updateStatus('エラー: ' + payload.message);
                    // }
                }
            }, function (error) { // printerトピックの購読エラーハンドラ
                console.error('STOMP error for /topic/printer:', error);
                // updateStatus('WebSocket購読エラー (printer): ' + error);
            });
        }
    }, function (error) {
        // 接続失敗時、または接続が切れた時のエラーハンドラ
        console.error('STOMP Connection error or disconnected:', error);
        reconnectAttempts++; // 試行回数をインクリメント
        console.log(`STOMP接続が切断されました。${reconnectInterval / 1000}秒後に再接続を試みます...`);
        // 次の再接続をスケジュール
        setTimeout(tryConnectStomp, reconnectInterval);
    });
}

// 手動リロードを促すアラートを表示する関数
function showManualReloadPrompt() {
    // すでにアラートが表示されている場合は何もしない
    if (isManualReloadPrompted) {
        return;
    }

    isManualReloadPrompted = true; // アラート表示フラグを立てる

    const warningMessage = "WebSocket接続が切断され、自動再接続に失敗しました。OKを押すとページをリロードします。";

    // confirmダイアログでユーザーの操作を待つ
    if (confirm(warningMessage)) {
        // OKが押されたらページをリロード
        location.reload();
    } else {
        // キャンセルが押された場合の処理（今回はリロードさせたいので基本的にはここには来ない想定）
        console.log("ユーザーはリロードをキャンセルしました。");
        // キャンセルの場合でも、このフラグはtrueのままにして、自動再接続が再開しないようにする
    }
}

//---
//## 既存のグローバル変数と初期設定
//---
const cart = []; // カートの中身を保持する配列
let taxRateMap = {}; // 税率IDと税率をマッピングするオブジェクト

// 座席情報の表示
let seatId = getCookie("seatId");
if (!seatId || seatId === "null" || seatId === "undefined") {
    seatId = window.seatIdFromModel;
}
document.getElementById("seatInfo").innerText = `${seatId}`;

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
function showToast(message, duration = 2000, type = 'success') {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    
    // スタイルをリセット
    toast.style.backgroundColor = '';
    toast.style.color = '';

    // タイプに応じたスタイルを設定
    if (type === 'error') {
        toast.style.backgroundColor = '#dc3545'; // 赤色
        toast.style.color = '#fff'; // 白文字
    } else if (type === 'success') {
        toast.style.backgroundColor = '#28a745'; // 緑色
        toast.style.color = '#fff';
    } else if (type === 'info') {
        toast.style.backgroundColor = '#007bff'; // 青色
        toast.style.color = '#fff';
    }

    toast.style.display = "block";
    toast.style.opacity = "1";

    setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => {
            toast.style.display = "none";
        }, 500);
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
    document.cookie = `seatId=${seatId};`; 

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
//            const currentUrl = new URL(window.location.href);
//            currentUrl.searchParams.set('toastMessage', '注文を確定しました');
//            window.location.href = currentUrl.toString(); // クエリパラメータ付きでリロード
            cart.length = 0; // カートの配列を空にする
            updateMiniCart();
			showToast("注文を確定しました", 3000);
        } else {
			return res.json().then(errorBody => {
                const errorMessage = errorBody.message || '不明なエラーが発生しました。';
                // エラーメッセージとタイプをクエリパラメータにセットしてリロード
                const currentUrl = new URL(window.location.href);
                currentUrl.searchParams.set('toastMessage', errorMessage);
                currentUrl.searchParams.set('toastType', 'error'); // エラータイプも渡す
                window.location.href = currentUrl.toString(); // リロード
            });
//            showToast('注文に失敗しました');
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

        // ★変更点: 現在選択されているタブのグループIDに一致するメニューアイテムのみを表示
        // 飲み放題のアクティブ状況によるメニューアイテム個別の表示制御は、
        // そもそもバックエンドが送ってこない or CSSでタブが非表示になることで間接的に制御される
        if (itemGroupId === groupId) {
            item.style.display = 'block';
        } else {
            item.style.display = 'none';
        }
    });
}


// イベントリスナーと初期化
// -----------------------------------------------------------------------------

// DOMコンテンツが完全にロードされた後に実行される処理
window.addEventListener('DOMContentLoaded', () => {
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
    const firstTab = document.querySelector('.menu-tab');
    if (firstTab) firstTab.click(); // 通常表示時の初期タブ選択


    // WebSocket接続の確立と購読部分をtryConnectStomp()に置き換え
    tryConnectStomp(); 
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
    
    // URLパラメータにtoastMessageがあれば表示
    const urlParams = new URLSearchParams(window.location.search);
    const message = urlParams.get('toastMessage');
    const type = urlParams.get('toastType') || 'success'; // デフォルトはsuccess
    if (message) {
        showToast(message, 3000, type); // タイプを渡す
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // 重要: ページロード時に現在の飲み放題状態に基づいてメニューグループの表示を調整
    // WebSocketからの通知だけでなく、初期表示でも正しい状態にする必要がある
    // サーバサイドから渡されたmenuGroupsの情報を使って処理する
    const allMenuTabs = document.querySelectorAll('.menu-tab');
    const allMenuItems = document.querySelectorAll('.menu-item');

    // まずは、初回表示時に最初のタブをアクティブにする処理
    const firstNonPlanTargetTab = document.querySelector('.menu-tab:not([data-is-plan-target="true"])');
    if (firstNonPlanTargetTab) {
        switchTab(firstNonPlanTargetTab);
    } else {
        // 全てがisPlanTarget=trueの場合（＝何も表示されない場合）
        // 最初のタブ（どれでもいい）をアクティブにする
        const anyTab = document.querySelector('.menu-tab');
        if (anyTab) {
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
        }
        // そのグループに属するメニューアイテムを表示
        document.querySelectorAll(`.menu-item[data-group-id="${groupId}"]`).forEach(item => {
            item.classList.add('active-plan-menu'); // CSSで表示
        });
    });
}

//スタッフ呼び出し
function sendCallRequest() {
    const seatId = getCookie('seatId'); // Cookieから座席IDを取得
    if (!seatId) {
        alert('座席情報が見つかりません。');
        return;
    }

    fetch('/callSeat', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        // 送信したい座席IDをリクエストボディに含める
        body: JSON.stringify({ seatId: seatId }) 
    })
    .then(response => {
        if (response.ok) {
            alert('店員を呼び出しました。少々お待ちください。');
        } else {
            alert('呼び出しに失敗しました。');
        }
    })
    .catch(error => {
        console.error('呼び出しエラー:', error);
        alert('呼び出し中にエラーが発生しました。');
    });
}