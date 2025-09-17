/**
 * プリンター関連機能
 * Epson EPOS SDK を使用した印刷処理
 */

// Epson EPOS SDK関連のグローバル変数
let printer = null;
const ePosDev = new epson.ePOSDevice();
let currentPrinterIp = null;

// 印刷ジョブのキューと状態
const printJobQueue = [];
let isPrinting = false;

/**
 * 印刷キューに印刷ジョブを追加
 * @param {string} ip - プリンターのIPアドレス
 * @param {string} commandsJson - 印刷コマンドのJSON文字列
 * @param {number} retryCount - リトライ回数
 */
function enqueuePrintJob(ip, commandsJson, retryCount = 0) {
    printJobQueue.push({ ip, commandsJson, retryCount });
    if (!isPrinting) {
        processPrintJobs();
    }
}

/**
 * 印刷キューを順次処理
 */
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
        updatePrinterStatus(`印刷エラー: ${e.message}`);

        if (retryCount < 3) {
            console.warn(`リトライ ${retryCount + 1} 回目: 再キューします`);
            enqueuePrintJob(ip, commandsJson, retryCount + 1);
        } else {
            showToast("印刷に3回失敗しました。プリンタの状態を確認してください。", 4000, 'error');
        }
    }

    processPrintJobs();
}

/**
 * プリンターステータスを更新
 * @param {string} message - ステータスメッセージ
 */
function updatePrinterStatus(message) {
    const statusElement = document.getElementById("statusMessage");
    if (statusElement) {
        statusElement.textContent = `プリンタステータス: ${message}`;
    }
    console.log(`[Printer Status] ${message}`);
}

/**
 * プリンターへの接続とコマンド実行
 * @param {string} ipAddress - プリンターのIPアドレス
 * @param {string} commandsJson - 印刷コマンドのJSON文字列
 */
async function connectAndExecute(ipAddress, commandsJson) {
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
        ePosDev.connect(ipAddress, 8008, function(result) {
            if (result === 'OK' || result === 'SSL_CONNECT_OK') {
                console.log("接続成功。プリンタデバイス作成中...");
                currentPrinterIp = ipAddress;
                ePosDev.createDevice(
                    'local_printer',
                    ePosDev.DEVICE_TYPE_PRINTER,
                    { crypto: false, buffer: false },
                    function(devobj, retcode) {
                        if (retcode === 'OK') {
                            printer = devobj;
                            setupPrinterEvents(printer);
                            console.log('プリンタデバイス作成成功。コマンド実行中...');
                            updatePrinterStatus('印刷コマンド実行中...');
                            executeCommands(commandsJson).then(resolve).catch(reject);
                        } else {
                            console.error("デバイス作成失敗:", retcode);
                            updatePrinterStatus('プリンタデバイス作成失敗: ' + retcode);
                            reject(new Error("Device creation failed: " + retcode));
                        }
                    }
                );
            } else {
                console.error("プリンタ接続失敗:", result);
                updatePrinterStatus('プリンタ接続失敗: ' + result);
                reject(new Error("Printer connection failed: " + result));
            }
        });
    });
}

/**
 * プリンターイベントハンドラのセットアップ
 * @param {Object} printerObj - プリンターオブジェクト
 */
function setupPrinterEvents(printerObj) {
    printerObj.timeout = 60000;
    printerObj.onreceive = function(response) { 
        if (response.success) { 
            console.log('印刷成功！');
            updatePrinterStatus('印刷完了');
        } else { 
            console.error('印刷失敗:', response.code); 
            updatePrinterStatus('印刷失敗: ' + response.code);
        }
    };
    printerObj.onstatuschange = function(status) { 
        console.log('プリンタステータス変更:', status);
    };
    printerObj.onpaperend = function() { 
        console.warn('用紙切れ！');
        updatePrinterStatus('警告: 用紙切れ！');
    };
    printerObj.oncoveropen = function() {
        console.warn('カバーオープン！');
        updatePrinterStatus('警告: カバーオープン！');
    };
    
    ePosDev.ondisconnect = function() { 
        console.warn('プリンタとの接続が切断されました！');
        updatePrinterStatus('警告: プリンタ切断！');
        printer = null;
        currentPrinterIp = null;
    };
    ePosDev.onreconnecting = function() { 
        console.log('プリンタに再接続中...');
        updatePrinterStatus('プリンタに再接続中...');
    };
    ePosDev.onreconnect = function() {
        console.log('プリンタに再接続しました！');
        updatePrinterStatus('プリンタ再接続完了');
    };
}

/**
 * 印刷コマンドを実行
 * @param {string} commandsJson - 印刷コマンドのJSON文字列
 */
async function executeCommands(commandsJson) {
    let commands = JSON.parse(commandsJson);
    commands = commands.flat();

    for (const command of commands) {
        try {
            switch (command.api) {
                case "addSound":
                    printer.addSound(
                        command.pattern,
                        command.repeat,
                        command.cycle
                    );
                    break;
                case "addTextLang":
                    printer.addTextLang(command.lang);
                    break;
                case "addFeedUnit":
                    printer.addFeedUnit(command.unit);
                    break;
                case "addText":
                    if (command.align) { 
                        printer.addTextAlign(printer[`ALIGN_${command.align.toUpperCase()}`]);
                    } else {
                        printer.addTextAlign(printer.ALIGN_LEFT);
                    }
                    if (command.dw !== undefined && command.dh !== undefined) {
                       printer.addTextDouble(command.dw, command.dh);
                    }
                    if (command.width !== undefined && command.height !== undefined) {
                        printer.addTextSize(command.width, command.height);
                    }
                    if (command.reverse !== undefined && command.ul !== undefined && command.em !== undefined && command.color) {
                        printer.addTextStyle(command.reverse, command.ul, command.em, printer[command.color]);
                    } else {
                        printer.addTextStyle(false, false, false, printer.COLOR_1);
                    }
                    printer.addText(command.content + '\n');
                    break;
                case "addTextAlign":
                    printer.addTextAlign(printer[`ALIGN_${command.align.toUpperCase()}`]);
                    break;
                case "addTextDouble":
                    printer.addTextDouble(command.dw, command.dh);
                    break;
                case "addTextSize":
                    printer.addTextSize(command.width, command.height);
                    break;
                case "addTextStyle":
                    printer.addTextStyle(command.reverse, command.ul, command.em, printer[command.color]);
                    break;
                case "addFeed":
                    printer.addFeed();
                    break;
                case "addCut":
                    printer.addCut(printer[`CUT_${command.type.toUpperCase()}`]);
                    break;
                case "addImage":
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
                                printer[command.color],
                                printer['MODE_' + command.mode.toUpperCase()]
                            );
                            resolve();
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
                default:
                    console.warn("未対応のAPIコマンド:", command.api, command);
            }
        } catch (e) {
            console.error(`コマンド実行中にエラーが発生しました: ${command.api}`, e);
            updatePrinterStatus(`コマンド実行エラー: ${command.api}`);
            break;
        }
    }
    
    let logOutput = commands.map(cmd => {
        return `[${cmd.api}] ${JSON.stringify(cmd)}`;
    }).join('\n');

    console.log("=== 印刷コマンド変換ログ ===\n" + logOutput);
    console.log(commands);
    console.log(printer);
    printer.send();
}

// グローバルスコープに公開
window.enqueuePrintJob = enqueuePrintJob;
window.updatePrinterStatus = updatePrinterStatus;