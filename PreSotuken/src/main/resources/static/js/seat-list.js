let activeSeatId = null;

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
    const seatId = document.getElementById("activeModal").getAttribute("data-seat-id");
	const storeId = getCookie("storeId"); 
	
    // visitId取得
    fetch(`/api/visit-info?seatId=${seatId}&storeId=${storeId}`)
      .then(res => res.json())
      .then(data => {
        if (data.visiting && data.visitId) {
            // 正しい visitId を cookie に保存
            document.cookie = `visitId=${data.visitId}; path=/; max-age=3600`;
        }

        // 遷移（visitId はURLに含めなくてOK）
        window.location.href = `/order?seatId=${seatId}&admin=true&from=seatlist`;
      });
  });
      
// ユーザーIDをCookieに保存する関数
function setUserIdCookie(userId) {
    if (userId) {
        document.cookie = "userId=" + userId + "; path=/; max-age=" + (60 * 60 * 24 * 30); // 30日間有効
        console.log("CookieにuserIdを保存しました: " + userId);
    } else {
        // 未選択の場合はCookieを削除する、など
        // `currentUserId`を`userId`に修正
        document.cookie = "userId=; path=/; max-age=0"; // Cookieを即座に期限切れにする
        console.log("CookieからuserIdを削除しました。");
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