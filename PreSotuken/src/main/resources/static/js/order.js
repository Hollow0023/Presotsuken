const cart = [];
const seatId = getCookie("seatId"); // もしくは URL から取得
document.getElementById("seatInfo").innerText = `${seatId}`;

function getCookie(name) {
	const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
	return match ? decodeURIComponent(match[2]) : null;
}

function showToast(message, duration = 2000) {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.style.display = "block";
    toast.style.opacity = "1";

    setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => {
            toast.style.display = "none";
        }, 500); // フェードアウト後に非表示
    }, duration);
}

function toggleCart(show) {
  const cartPanel = document.getElementById("cartPanel");
  if (!cartPanel) return;

  if (show === true) {
    cartPanel.classList.add("show");
  } else if (show === false) {
    cartPanel.classList.remove("show");
  } else {
    cartPanel.classList.toggle("show");
  }
}

// カート以外の部分クリックで閉じる
window.addEventListener('click', (e) => {
  const cartPanel = document.getElementById("cartPanel");
  if (!cartPanel) return;

  const isClickInside = cartPanel.contains(e.target) || e.target.closest('button[onclick="toggleCart()"]');
  if (!isClickInside) {
    cartPanel.classList.remove("show");
  }
});



function updateQuantity(index, newVal) {
    const qty = parseInt(newVal);
    if (!isNaN(qty) && qty > 0) {
        cart[index].quantity = qty;
        updateMiniCart(); // 小計・合計も更新される
    } else {
        alert("数量は1以上を指定してください");
    }
}



function toggleDetails(elem) {
	  const detail = elem.querySelector(".menu-detail");
	  const isExpanded = elem.classList.contains("expanded");

	  // 閉じる処理
	  if (isExpanded) {
	    elem.style.height = elem.offsetHeight + "px"; // 現在の高さを設定
	    elem.classList.remove("expanded");

	    const onTransitionEnd = () => {
	      detail.style.display = 'none';
	      elem.removeEventListener('transitionend', onTransitionEnd);
	      elem.style.height = ''; // アニメーション後に height をクリア
	    };
	    elem.addEventListener('transitionend', onTransitionEnd);

	    requestAnimationFrame(() => {
	      elem.style.height = '180px'; // 閉じた時の初期の高さ
	    });

	  } else {
	    // 開く処理
	    elem.classList.add("expanded");
	    detail.style.display = 'block'; // detail を表示

	    requestAnimationFrame(() => {
	      requestAnimationFrame(() => { // ネストされた RAF で確実性を高める
	        let fullHeight = elem.scrollHeight;

	        elem.style.height = fullHeight + "px";
	      });
	    });
	  }
	}

function showDescriptionFromData(btn) {
    const title = btn.getAttribute('data-name');
    const desc = btn.getAttribute('data-desc');
    alert(`${title}\n\n${desc}`);
}



function updateMiniCart() {
    const list = document.getElementById('cartMiniList');
    const totalEl = document.getElementById('cartMiniTotal');
    list.innerHTML = '';
    let total = 0;

    // 表のヘッダー行
    const header = document.createElement('tr');
    header.innerHTML = `
        <th style="text-align: left;">商品名</th>
        <th style="text-align: center;">数量</th>
        <th style="text-align: right;">小計</th>
        <th></th>
    `;
    list.appendChild(header);

    cart.forEach((item, index) => {
        const subtotal = item.price * item.quantity;
        total += subtotal;

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${item.name}</td>
            <td style="text-align: center;">
                <input type="number" min="1" value="${item.quantity}" 
                       onchange="updateQuantity(${index}, this.value)" 
                       style="width: 50px;" />
            </td>
            <td style="text-align: right;">${subtotal}円</td>
            <td><button onclick="removeFromCart(${index})">削除</button></td>
        `;
        list.appendChild(row);
    });

    totalEl.textContent = `合計：${total}円`;
}



function switchTab(tabElement) {
    document.querySelectorAll('.menu-tab').forEach(t => t.classList.remove('active'));
    tabElement.classList.add('active');
    const groupId = tabElement.getAttribute('data-group-id');
    document.querySelectorAll('.menu-item').forEach(item => {
        item.style.display = (item.getAttribute('data-group-id') === groupId) ? 'block' : 'none';
    });
}

function addToCart(button) {
    const menuId = button.getAttribute('data-menu-id');
    const taxRateId = button.getAttribute('data-tax-rate-id');
    const price = parseFloat(button.getAttribute('data-price'));
    const name = button.getAttribute('data-name');
    const quantityInput = button.previousElementSibling;
    const quantity = parseInt(quantityInput.value);

    if (isNaN(quantity) || quantity <= 0) {
        alert('数量は1以上を入力してください。');
        return;
    }

    const existing = cart.find(item => item.menuId === menuId);
    if (existing) {
        existing.quantity += quantity;
    } else {
        cart.push({ menuId, taxRateId, price, quantity, name });
    }
    showToast("カートに追加しました");

    const menuItem = button.closest('.menu-item');
    if (menuItem && menuItem.classList.contains('expanded')) {
        toggleDetails(menuItem);
    }

    updateMiniCart();
}

function removeFromCart(index) {
    cart.splice(index, 1);
    updateMiniCart();
}

function openCartModal() {
    const cartList = document.getElementById('cartList');
    const cartTotal = document.getElementById('cartTotal');
    cartList.innerHTML = '';
    let total = 0;

    cart.forEach((item, index) => {
        const subtotal = item.price * item.quantity;
        total += subtotal;
        const li = document.createElement('li');
        li.innerHTML = `${item.name} x ${item.quantity}：${subtotal}円 <button onclick="removeFromCart(${index})">削除</button>`;
        cartList.appendChild(li);
    });

    cartTotal.textContent = `合計：${total}円`;
    document.getElementById('cartModal').style.display = 'block';
}

function closeCartModal() {
    document.getElementById('cartModal').style.display = 'none';
}

function openHistoryModal() {
    fetch('/order/history')
        .then(res => res.json())
        .then(data => {
            const tbody = document.querySelector('#historyTable tbody');
            const totalEl = document.getElementById('historyTotal');
            tbody.innerHTML = '';
            let total = 0;

            data.forEach(item => {
                const subtotal = parseInt(item.subtotal) || 0;
                total += subtotal;
                const row = document.createElement('tr');
                row.innerHTML = `<td>${item.menuName}</td><td>${item.quantity}</td><td>${subtotal}円</td>`;
                tbody.appendChild(row);
            });

            totalEl.textContent = `合計金額：${total}円`;
            document.getElementById('historyModal').style.display = 'block';
        });
}

function closeHistoryModal() {
    document.getElementById('historyModal').style.display = 'none';
}

function submitOrder() {
    const orderItems = cart.map(item => ({
        menuId: parseInt(item.menuId),
        taxRateId: parseInt(item.taxRateId),
        quantity: parseInt(item.quantity)
    }));
    cartPanel.classList.remove("show")

    fetch('/order/submit', {

        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderItems)
    }).then(res => {
        if (res.ok) {
            alert('注文を確定しました');
            cart.length = 0;
            updateMiniCart();
            closeCartModal();
        } else {
            alert('注文に失敗しました');
        }
    });
}

window.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.menu-tab').forEach(tab => {
        tab.addEventListener('click', () => switchTab(tab));
    });

    document.querySelectorAll('.info-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            showDescriptionFromData(btn);
        });
    });

    document.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', (e) => {
            const clicked = e.target;

            const isToggleTarget =
                clicked.closest('.menu-image-wrapper') ||
                clicked.closest('.menu-name') ||
                clicked.closest('.menu-price');

            if (isToggleTarget) {
                toggleDetails(item);
            }
        });
    });

    document.querySelectorAll('.add-cart-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            addToCart(btn);
            
        });
    });

    const firstTab = document.querySelector('.menu-tab');
    if (firstTab) firstTab.click();

    const socket = new SockJS('/ws-endpoint');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, function () {
        if (typeof seatId !== 'undefined' && seatId !== null) {
			// Cookie整理処理をここに追加する
			const rawUserId = getCookie("userId");
			if (rawUserId === "null" || rawUserId === "undefined") {
			  document.cookie = "userId=; Max-Age=0; path=/";
			}

            stompClient.subscribe(`/topic/seats/${seatId}`, function (message) {
                const body = message.body;
                if (body === 'LEAVE') {
                    document.cookie = 'visitId=; Max-Age=0; path=/';

//                    document.cookie = 'userId=; Max-Age=0; path=/';
                    window.location.href = '/visits/orderwait';
                }
            });
        }
    });
});

window.addEventListener('click', (e) => {
  const historyModal = document.getElementById('historyModal');
  // 履歴モーダルの外側クリックで閉じる
  if (
    historyModal &&
    historyModal.style.display === 'block' &&
    !e.target.closest('.cart-modal-content') &&
    historyModal.contains(e.target)
  ) {
    closeHistoryModal();
  }
  document.querySelectorAll('.menu-item.expanded').forEach(item => {
    if (!item.contains(e.target)) {
      toggleDetails(item); // 閉じる
    }
  });
});

window.onload = () => {
	  const params = new URLSearchParams(window.location.search);
	  if (params.get("from") === "seatlist") {
	    document.getElementById("backToSeatList").style.display = "block";
	  }
	};
