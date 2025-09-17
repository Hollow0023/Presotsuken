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

    if (!drawer.contains(event.target) && event.target !== burger) {
        toggleMenu();
    }
}

function toggleMenu() {
    const drawer = document.getElementById("menuDrawer");
    const burger = document.querySelector(".burger");

    drawer.classList.toggle("open");

    if (drawer.classList.contains("open")) {
        document.addEventListener("click", handleOutsideMenuClick, true); 
        burger.style.color = "white";
    } else {
        document.removeEventListener("click", handleOutsideMenuClick, true);
        burger.style.color = "black";
    }
}

function clearUserIdCookie() {
    document.cookie = "userId=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
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
                document.getElementById('activeModal').style.display = 'block';

                document.getElementById('paymentCheckBtn').onclick = () => {
                    window.location.href = `/payments?visitId=${data.visitId}`;
                };
            } else {
                document.getElementById("modalSeatId").value = seatId;
                document.getElementById("modalSeatName").innerText = seatName;
                document.getElementById("peopleCount").value = 1;
                document.getElementById("seatModal").style.display = "block";
            }
        });

    document.getElementById('deleteVisitBtn').onclick = () => {
        const userSelect = document.getElementById('userSelect');
        if (!userSelect || userSelect.value === "") {
            alert("担当者を選択してください。");
            return;
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
    document.getElementById("callListModal").style.display = "none";
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
                if (statusDiv) statusDiv.style.display = 'block';
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
                if (statusDiv) statusDiv.style.display = 'none';
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
function setUserIdCookie(userId) {
    if (userId && userId !== "null" && userId !== "undefined") {
        document.cookie = "userId=" + userId + "; path=/; max-age=" + (60 * 60 * 24 * 30);
        console.log("CookieにuserIdを保存しました: " + userId);
    } else {
        console.log("有効なuserIdではないため、Cookieを更新しませんでした。");
    }
}

window.onload = function() {
    const userIdCookie = document.cookie.split('; ').find(row => row.startsWith('userId='));
    if (userIdCookie) {
        const userId = userIdCookie.split('=')[1];
        const userSelect = document.getElementById('userSelect');
        if (userSelect) {
            userSelect.value = userId;
        }
    }

    fetchVisitInfo();
    setInterval(fetchVisitInfo, 60000);
    
    // 呼び出しシステムを初期化
    if (typeof CallListManager !== 'undefined') {
        window.callListManager = new CallListManager();
    }
};