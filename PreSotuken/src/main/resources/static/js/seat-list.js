let activeSeatId = null;

function increase() {
  const input = document.getElementById("peopleCount");
  input.value = parseInt(input.value) + 1;
}

function decrease() {
  const input = document.getElementById("peopleCount");
  if (parseInt(input.value) > 1) {
    input.value = parseInt(input.value) - 1;
  }
}

/**
 * メニュー外のクリックを検知してメニューを閉じる関数
 * @param {Event} event - クリックイベントオブジェクト
 */
function handleOutsideMenuClick(event) {
    const drawer = document.getElementById("menuDrawer");
    const burger = document.querySelector(".burger");

    // クリックされた要素がドロワーメニュー内、またはバーガーボタン自身でない場合
    if (!drawer.contains(event.target) && event.target !== burger) {
        toggleMenu(); // メニューを閉じる
    }
}


function toggleMenu() {
    const drawer = document.getElementById("menuDrawer");
    const burger = document.querySelector(".burger");

    drawer.classList.toggle("open");

    if (drawer.classList.contains("open")) {
        // メニューが開いたら、メニュー以外のクリックを検知するイベントリスナーを追加
        // capture: true にすることで、他のイベントハンドラよりも先にイベントを捕捉する
        document.addEventListener("click", handleOutsideMenuClick, true); 
        burger.style.color = "white"; // ハンバーガーメニューの色を白に
    } else {
        // メニューが閉じたら、イベントリスナーを削除
        document.removeEventListener("click", handleOutsideMenuClick, true);
        burger.style.color = "black"; // ハンバーガーメニューの色を黒に
    }
}

function clearUserIdCookie() {
    document.cookie = "userId=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
}

function getCookie(name) {
	const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
	return match ? decodeURIComponent(match[2]) : null;
}

function openSeat(elem) {
	const seatId = elem.getAttribute('data-seat-id');
	const seatName = elem.getAttribute('data-seat-name');
	activeSeatId = seatId;
	
	const storeId = getCookie("storeId");
	
	
	fetch(`/api/visit-info?seatId=${seatId}&storeId=${storeId}`)
		.then(response => response.json())
		.then(data => {
			if (data.visiting) {
				document.getElementById('activeModal').setAttribute("data-seat-id", seatId);
				document.getElementById('activeModalSeatName').innerText = seatName;
				
//			    const orderLink = document.getElementById("orderLink");
//			    const visitId = data.visitId;
//			    orderLink.href = `/order?seatId=${seatId}&admin=true&visitId=${visitId}&from=seatlist`;

			    
				document.getElementById('activeModal').style.display = 'block';

				document.getElementById('paymentCheckBtn').onclick = () => {
					window.location.href = `/payments?visitId=${data.visitId}`;
				};
			} else {
				document.getElementById("modalSeatId").value = seatId;
				document.getElementById("modalSeatName").innerText = seatName;
                document.getElementById("peopleCount").value = 1; // モーダルを開くときに人数を1にリセット
				document.getElementById("seatModal").style.display = "block";
			}
		});

document.getElementById('deleteVisitBtn').onclick = () => {
        const userSelect = document.getElementById('userSelect'); // プルダウン要素をここで取得
        if (!userSelect || userSelect.value === "") { // userSelectが取得できないか、選択値が空なら
            alert("担当者を選択してください。"); // 警告を表示
            return; // 処理を中断
        }
        
		if (confirm("この入店情報を削除しますか？")) {
			fetch(`/api/delete-visit?seatId=${activeSeatId}`, { method: 'DELETE' })
				.then(res => {
					if (res.ok) {
						closeModal();
						resetSeatTile(activeSeatId);
					} else {
						alert("削除に失敗しました");
					}
				});
		}
	};
}

function closeModal() {
	document.getElementById("seatModal").style.display = "none";
	document.getElementById("activeModal").style.display = "none";
	document.getElementById("callListModal").style.display ="none";
	
}

window.onclick = function (event) {
	const seatModal = document.getElementById("seatModal");
	const activeModal = document.getElementById("activeModal");
	const callListModal = document.getElementById("callListModal");
	if (event.target === seatModal || event.target === activeModal || event.target === callListModal) {
		closeModal();
	}
}

function fetchVisitInfo() {
	document.querySelectorAll('.seat').forEach(seat => {
		const seatId = seat.getAttribute('data-seat-id');
		updateSeatTile(seatId);
	});
}

function updateSeatTile(seatId) {
	const seat = document.querySelector(`.seat[data-seat-id="${seatId}"]`);
	const peopleSpan = document.getElementById(`people-${seatId}`);
	const totalDiv = document.getElementById(`total-${seatId}`);
	const elapsedDiv = document.getElementById(`elapsed-${seatId}`);
	const statusDiv = document.getElementById(`status-${seatId}`); 
	const storeId = getCookie("storeId");

	seat.classList.remove('elapsed-yellow', 'elapsed-red', 'occupied');

	fetch(`/api/visit-info?seatId=${seatId}&storeId=${storeId}`)
		.then(res => res.json())
		.then(data => {
			if (data.visiting) {
				seat.classList.add('occupied');
				if (statusDiv) statusDiv.style.display = 'block'; // 表示
				const elapsedMinutes = Math.floor(data.elapsedMinutes);

				if (elapsedMinutes >= 60) {
					seat.classList.add('elapsed-red');
				} else if (elapsedMinutes >= 30) {
					seat.classList.add('elapsed-yellow');
				}

				peopleSpan.innerText = `${data.numberOfPeople}名`;
				elapsedDiv.innerText = `${elapsedMinutes}分`;

				fetch(`/api/total-amount?seatId=${seatId}`)
					.then(res => res.json())
					.then(amountData => {
						if (amountData.total && amountData.total > 0) {
							totalDiv.innerHTML = `&yen; ${amountData.total.toLocaleString()}`;
						} else {
							totalDiv.innerText = '';
						}
					})
					.catch(() => {
						totalDiv.innerText = '金額取得失敗';
					});
			} else {
				peopleSpan.innerText = '';
				totalDiv.innerText = '';
				elapsedDiv.innerText = '';
				if (statusDiv) statusDiv.style.display = 'none'; // 非表示

			}
		});
}

function resetSeatTile(seatId) {
	const seat = document.querySelector(`.seat[data-seat-id="${seatId}"]`);
	const peopleSpan = document.getElementById(`people-${seatId}`);
	const totalDiv = document.getElementById(`total-${seatId}`);
	const elapsedDiv = document.getElementById(`elapsed-${seatId}`);
	const statusDiv = document.getElementById(`status-${seatId}`);

	seat.classList.remove('occupied', 'elapsed-yellow', 'elapsed-red');
	peopleSpan.innerText = '';
	totalDiv.innerText = '';
	elapsedDiv.innerText = '';
	if (statusDiv) statusDiv.style.display = 'none';
}


document.getElementById("orderBtn").addEventListener("click", () => {
    const userSelect = document.getElementById('userSelect');
    if (!userSelect || userSelect.value === "") { // 担当者選択チェック
        alert("担当者を選択してください。"); 
        return; // 担当者が選択されていなければ処理を中断
         
    }

    const seatId = document.getElementById("activeModal").getAttribute("data-seat-id");
    const storeId = getCookie("storeId"); 
    
    fetch(`/api/visit-info?seatId=${seatId}&storeId=${storeId}`)
      .then(res => res.json())
      .then(data => {
        if (data.visiting && data.visitId) {
            document.cookie = `visitId=${data.visitId}; path=/; max-age=3600`;
        }

        window.location.href = `/order?seatId=${seatId}&admin=true&from=seatlist`;
      })
      .catch(error => {
        console.error("注文画面への遷移中にエラー:", error);
        alert("注文画面への遷移中にエラーが発生しました。");
      });
});
      
// ユーザーIDをCookieに保存する関数
function setUserIdCookie(userId) {
    // userIdが有効な値（空文字列ではない）の場合のみCookieをセットまたは更新
    if (userId && userId !== "null" && userId !== "undefined") { // "null"や"undefined"という文字列も除外
        document.cookie = "userId=" + userId + "; path=/; max-age=" + (60 * 60 * 24 * 30); // 30日間有効
        console.log("CookieにuserIdを保存しました: " + userId);
    } else {
        // userIdが有効な値ではない場合、Cookieは削除せず何もしない（あるいは、明示的に削除したいならMax-Age=0を設定）
        // ここでCookieを削除しないことで、ユーザーが誤って「ユーザーを選択」に戻してもuserIdが消えないようにする
        console.log("有効なuserIdではないため、Cookieを更新しませんでした。");
    }
}
// ページロード時に実行する処理を全てこの中にまとめる
window.onload = function() {
    // ユーザーIDをCookieから読み込んでプルダウンに反映する処理
    const userIdCookie = document.cookie.split('; ').find(row => row.startsWith('userId='));
    if (userIdCookie) {
        const userId = userIdCookie.split('=')[1];
        const userSelect = document.getElementById('userSelect');
        if (userSelect) {
            userSelect.value = userId;
        }
    }

    // fetchVisitInfo の実行
    fetchVisitInfo();

    // setInterval の設定
    setInterval(fetchVisitInfo, 60000);
};







//呼び出し処理
// チャイム音のDOM要素を取得
const chime = document.getElementById('chimeSound');

// 呼び出し中の座席データを保持する配列 (レジ端末のメモリ上で管理)
let callingSeats = [];

// --- WebSocket接続と購読 ---
// SockJSとStomp.jsを使ってWebSocketサーバーに接続
// WebSocketConfigで設定したエンドポイントに合わせてね
const socket = new SockJS('/ws-endpoint'); 
const stompClient = Stomp.over(socket);

stompClient.connect({}, function (frame) {
    console.log('Connected to WebSocket: ' + frame);

    // ★ サーバーからの呼び出し通知を購読する処理 ★
    // /topic/seatCalls からメッセージが来たら、第2引数の関数が実行される
    stompClient.subscribe('/topic/seatCalls', function (notification) {
        // メッセージボディ（JSON文字列）をJavaScriptオブジェクトに変換
        const callData = JSON.parse(notification.body);
        console.log('呼び出し通知を受信しました:', callData);

        // 1. チャイムを鳴らす
        playChimeSound(); // 後述の関数

        // 2. 呼び出しリストに追加（重複は避ける）
        const existingCall = callingSeats.find(item => item.seatId === callData.seatId);
        if (!existingCall) {
            // 新しい呼び出しの場合のみ追加
            callingSeats.push(callData);
            updateCallListModal(); // モーダル表示を更新
            openCallListModal();   // 新規呼び出しがあればモーダルを開く
        }
    });
}, function(error) {
    console.error('WebSocket接続エラー:', error);
});



// ユーザーがどこかをクリックしたときに一度だけ実行されるようにする
let hasInteracted = false;

document.addEventListener('click', function setupAudio() {
    if (!hasInteracted) {
        // 音源をロードして、ミュートで一度再生を試みる（Safariに再生を許可してもらうため）
        chime.muted = true;
        chime.play().then(() => {
            chime.pause();
            chime.currentTime = 0;
            chime.muted = false; // 再生準備ができたらミュートを解除
            console.log('チャイム音源の準備ができました！');
        }).catch(error => {
            console.warn("初期音声再生の試行に失敗しました:", error);
            // ここでユーザーに「音を出すには、一度クリックしてください」とか促すのもアリかも
        });
        hasInteracted = true;
        document.removeEventListener('click', setupAudio); // 一度実行したらイベントリスナーは不要
    }
});

// --- チャイム音の再生関数 ---
function playChimeSound() {
    // ブラウザの自動再生ポリシーを考慮する必要がある
    // ユーザーインタラクションがないと再生できない場合があるため、注意
    chime.play().catch(function(error) {
        console.warn("チャイム音の再生に失敗しました: ", error);
        // エラーハンドリング（例: ユーザーに音が出ないことを通知など）
    });
}

// --- 呼び出しリストモーダルの管理関数 ---
const callListModal = document.getElementById('callListModal');
const callingSeatsList = document.getElementById('callingSeatsList');

function openCallListModal() {
    callListModal.style.display = 'block';
    updateCallListModal(); // モーダルを開くたびに最新の状態に更新
}

function closeCallListModal() {
    callListModal.style.display = 'none';
}

function updateCallListModal() {
    callingSeatsList.innerHTML = ''; // 一度リストを空にする

    if (callingSeats.length === 0) {
        callingSeatsList.innerHTML = '<li>現在、呼び出し中の座席はありません。</li>';
        return;
    }

    callingSeats.forEach(call => {
        const listItem = document.createElement('li');
        // 日時フォーマット (必要ならもっと詳細に)
        const callTime = new Date(call.callTime).toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        
        listItem.innerHTML = `
            <div class="call-item">
                <span class="call-item-name">${call.seatName}</span>
                <span class="call-item-time">(${callTime} 呼び出し)</span>
                <button class="complete-button" data-seat-id="${call.seatId}">完了</button>
            </div>
        `;
        callingSeatsList.appendChild(listItem);
    });

    // 動的に生成した完了ボタンにイベントリスナーを設定
    callingSeatsList.querySelectorAll('.complete-button').forEach(button => {
        button.addEventListener('click', function() {
            const seatIdToComplete = this.dataset.seatId;
            completeCall(seatIdToComplete);
        });
    });
}

// --- 完了ボタンが押された時の処理 ---
function completeCall(seatId) {
    // callingSeats 配列から該当する座席を削除
    callingSeats = callingSeats.filter(call => call.seatId !== seatId);
    updateCallListModal(); // リストを更新
}