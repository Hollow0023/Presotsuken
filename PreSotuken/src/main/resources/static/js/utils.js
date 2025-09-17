/**
 * 共通ユーティリティ関数
 * 複数のファイルで使用される共通機能を提供
 */

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
 * @param {string} [type='success'] - メッセージタイプ ('success', 'error', 'info')
 */
function showToast(message, duration = 2000, type = 'success') {
    const toast = document.getElementById("toast");
    if (!toast) return;
    
    toast.textContent = message;
    
    // スタイルをリセット
    toast.style.backgroundColor = '';
    toast.style.color = '';

    // タイプに応じたスタイルを設定
    if (type === 'error') {
        toast.style.backgroundColor = '#dc3545';
        toast.style.color = '#fff';
    } else if (type === 'success') {
        toast.style.backgroundColor = '#28a745';
        toast.style.color = '#fff';
    } else if (type === 'info') {
        toast.style.backgroundColor = '#007bff';
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

/**
 * ページロード時にURLパラメータからトーストメッセージを表示
 */
function handleUrlToastMessage() {
    const urlParams = new URLSearchParams(window.location.search);
    const message = urlParams.get('toastMessage');
    const type = urlParams.get('toastType') || 'success';
    if (message) {
        showToast(message, 3000, type);
        window.history.replaceState({}, document.title, window.location.pathname);
    }
}