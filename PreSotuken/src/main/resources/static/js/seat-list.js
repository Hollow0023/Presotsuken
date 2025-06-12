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
}

window.onclick = function (event) {
	const seatModal = document.getElementById("seatModal");
	const activeModal = document.getElementById("activeModal");
	if (event.target === seatModal || event.target === activeModal) {
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
							totalDiv.innerText = `\\ ${amountData.total}`;
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

function toggleMenu() {
	const drawer = document.getElementById("menuDrawer");
	const burger = document.querySelector(".burger");

	drawer.classList.toggle("open");

	burger.style.color = drawer.classList.contains("open") ? "white" : "black";
}




	document.getElementById("orderBtn").addEventListener("click", () => {
        if  (!userSelect || userSelect.value === ""){ // 担当者選択チェック
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