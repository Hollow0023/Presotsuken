/**
 * スタッフ呼び出しシステム
 * 顧客側とスタッフ側の呼び出し機能を管理
 */

/**
 * スタッフを呼び出す（顧客側）
 */
function sendCallRequest() {
    const seatId = getCookie('seatId');
    if (!seatId) {
        alert('座席情報が見つかりません。');
        return;
    }

    fetch('/callSeat', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
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

/**
 * 呼び出しリスト管理クラス（スタッフ側）
 */
class CallListManager {
    constructor() {
        this.callingSeats = [];
        this.modal = document.getElementById('callListModal');
        this.seatsList = document.getElementById('callingSeatsList');
        this.chime = document.getElementById('chimeSound');
        this.hasInteracted = false;
        
        this.initializeWebSocket();
        this.setupAudioInteraction();
    }

    /**
     * WebSocket接続を初期化
     */
    initializeWebSocket() {
        const socket = new SockJS('/ws-endpoint'); 
        const stompClient = Stomp.over(socket);

        stompClient.connect({}, (frame) => {
            console.log('Connected to WebSocket: ' + frame);

            stompClient.subscribe('/topic/seatCalls', (notification) => {
                const callData = JSON.parse(notification.body);
                console.log('呼び出し通知を受信しました:', callData);

                this.playChimeSound();
                this.addCallToList(callData);
            });
        }, (error) => {
            console.error('WebSocket接続エラー:', error);
        });
    }

    /**
     * 音声再生のためのユーザーインタラクションを設定
     */
    setupAudioInteraction() {
        document.addEventListener('click', () => {
            if (!this.hasInteracted && this.chime) {
                this.chime.muted = true;
                this.chime.play().then(() => {
                    this.chime.pause();
                    this.chime.currentTime = 0;
                    this.chime.muted = false;
                    console.log('チャイム音源の準備ができました！');
                }).catch(error => {
                    console.warn("初期音声再生の試行に失敗しました:", error);
                });
                this.hasInteracted = true;
            }
        }, { once: true });
    }

    /**
     * チャイム音を再生
     */
    playChimeSound() {
        if (this.chime) {
            this.chime.play().catch(error => {
                console.warn("チャイム音の再生に失敗しました: ", error);
            });
        }
    }

    /**
     * 呼び出しリストに新しい呼び出しを追加
     * @param {Object} callData - 呼び出しデータ
     */
    addCallToList(callData) {
        const existingCall = this.callingSeats.find(item => item.seatId === callData.seatId);
        if (!existingCall) {
            this.callingSeats.push(callData);
            this.updateCallListModal();
            this.openCallListModal();
        }
    }

    /**
     * 呼び出しリストモーダルを開く
     */
    openCallListModal() {
        if (this.modal) {
            this.modal.style.display = 'block';
            this.updateCallListModal();
        }
    }

    /**
     * 呼び出しリストモーダルを閉じる
     */
    closeCallListModal() {
        if (this.modal) {
            this.modal.style.display = 'none';
        }
    }

    /**
     * 呼び出しリストモーダルの表示を更新
     */
    updateCallListModal() {
        if (!this.seatsList) return;

        this.seatsList.innerHTML = '';

        if (this.callingSeats.length === 0) {
            this.seatsList.innerHTML = '<li>現在、呼び出し中の座席はありません。</li>';
            return;
        }

        this.callingSeats.forEach(call => {
            const listItem = document.createElement('li');
            const callTime = new Date(call.callTime).toLocaleTimeString('ja-JP', { 
                hour: '2-digit', 
                minute: '2-digit', 
                second: '2-digit' 
            });
            
            listItem.innerHTML = `
                <div class="call-item">
                    <span class="call-item-name">${call.seatName}</span>
                    <span class="call-item-time">(${callTime} 呼び出し)</span>
                    <button class="complete-button" data-seat-id="${call.seatId}">完了</button>
                </div>
            `;
            this.seatsList.appendChild(listItem);
        });

        this.seatsList.querySelectorAll('.complete-button').forEach(button => {
            button.addEventListener('click', () => {
                const seatIdToComplete = button.dataset.seatId;
                this.completeCall(seatIdToComplete);
            });
        });
    }

    /**
     * 呼び出しを完了
     * @param {string} seatId - 座席ID
     */
    completeCall(seatId) {
        this.callingSeats = this.callingSeats.filter(call => call.seatId !== seatId);
        this.updateCallListModal();
    }
}

// グローバルスコープに公開
window.sendCallRequest = sendCallRequest;
window.CallListManager = CallListManager;

// seat-list.htmlで使用される関数をグローバルスコープに公開
window.openCallListModal = function() {
    if (window.callListManager) {
        window.callListManager.openCallListModal();
    }
};

window.closeCallListModal = function() {
    if (window.callListManager) {
        window.callListManager.closeCallListModal();
    }
};

window.completeCall = function(seatId) {
    if (window.callListManager) {
        window.callListManager.completeCall(seatId);
    }
};