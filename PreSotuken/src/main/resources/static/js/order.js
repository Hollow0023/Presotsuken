// 注文画面メイン処理

(() => {
    const cart = [];
    let taxRateMap = {};

    let seatId = getCookie("seatId");
    if (!seatId || seatId === "null" || seatId === "undefined") {
        seatId = window.seatIdFromModel;
    }

    const renderOrderHistory = (data = []) => {
        const tbody = document.querySelector('#historyTable tbody');
        const totalEl = document.getElementById('historyTotal');
        const countEl = document.getElementById('historyCount');
        const taxEl = document.getElementById('historyTax');

        if (!tbody || !totalEl || !countEl || !taxEl) {
            return;
        }

        tbody.innerHTML = '';
        taxEl.innerHTML = '';

        let total = 0;
        let count = 0;
        const rateTotals = {};

        data.forEach(item => {
            const subtotal = parseInt(item.subtotal, 10) || 0;
            const quantity = parseInt(item.quantity, 10) || 0;
            const rate = parseFloat(item.taxRate) || 0;
            const basePrice = parseInt(item.price, 10) || 0;

            total += subtotal;
            count += quantity;

            if (!rateTotals[rate]) {
                rateTotals[rate] = 0;
            }
            rateTotals[rate] += basePrice * quantity;

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
                line.textContent = `${percent}%対象：¥${Math.round(amount)}(税別)`;
                line.style.textAlign = 'right';
                taxEl.appendChild(line);
            });
    };

    const fetchOrderHistoryForHistoryModal = () => {
        return fetch('/order/history')
            .then(res => res.json())
            .then(data => {
                renderOrderHistory(data);
                return data;
            })
            .catch(error => {
                console.error("注文履歴の取得に失敗しました:", error);
                throw error;
            });
    };

    const closeHistoryModal = () => {
        const historyModal = document.getElementById('historyModal');
        const toggleBtn = document.getElementById('historyToggleButton');
        if (historyModal) {
            historyModal.classList.remove('show');
        }
        if (toggleBtn) {
            toggleBtn.textContent = "注文履歴";
        }
    };

    const toggleHistory = () => {
        const historyModal = document.getElementById('historyModal');
        const toggleBtn = document.getElementById('historyToggleButton');
        if (!historyModal || !toggleBtn) {
            return;
        }

        document.cookie = `seatId=${seatId};`;

        if (historyModal.classList.contains('show')) {
            closeHistoryModal();
            return;
        }

        fetchOrderHistoryForHistoryModal()
            .then(() => {
                historyModal.classList.add('show');
                toggleBtn.textContent = "✕ 注文履歴を閉じる";
            })
            .catch(() => {
                const tbody = document.querySelector('#historyTable tbody');
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="3">注文履歴の読み込み中にエラーが発生しました。</td></tr>';
                }
            });
    };

    const toggleCart = (show) => {
        const cartPanel = document.getElementById('cartPanel');
        const toggleButton = document.getElementById('cartToggleButton');
        if (!cartPanel || !toggleButton) {
            return;
        }

        let isOpening;

        if (show === true) {
            cartPanel.classList.add('show');
            isOpening = true;
        } else if (show === false) {
            cartPanel.classList.remove('show');
            isOpening = false;
        } else {
            cartPanel.classList.toggle('show');
            isOpening = cartPanel.classList.contains('show');
        }

        toggleButton.textContent = isOpening ? '✕ カートを閉じる' : '🛒 カートを見る';
    };

    const toggleDetails = (elem) => {
        const detail = elem.querySelector('.menu-detail');
        const isExpanded = elem.classList.contains('expanded');

        if (isExpanded) {
            elem.style.height = elem.offsetHeight + 'px';
            elem.classList.remove('expanded');

            const onTransitionEnd = () => {
                detail.style.display = 'none';
                elem.removeEventListener('transitionend', onTransitionEnd);
                elem.style.height = '';
            };
            elem.addEventListener('transitionend', onTransitionEnd);

            requestAnimationFrame(() => {
                elem.style.height = '200px';
            });
        } else {
            elem.classList.add('expanded');
            detail.style.display = 'block';

            requestAnimationFrame(() => {
                requestAnimationFrame(() => {
                    const fullHeight = elem.scrollHeight;
                    elem.style.height = `${fullHeight}px`;
                    setTimeout(() => {
                        elem.scrollIntoView({ behavior: 'smooth', block: 'end' });
                    }, 300);
                });
            });
        }
    };

    const updateQuantity = (index, newVal) => {
        const qty = parseInt(newVal, 10);
        if (!Number.isNaN(qty) && qty > 0) {
            cart[index].quantity = qty;
            updateMiniCart();
        } else {
            showToast('数量は1以上を指定してください');
        }
    };

    const updateMiniCart = () => {
        const list = document.getElementById('cartMiniList');
        const totalEl = document.getElementById('cartMiniTotal');
        const countEl = document.getElementById('cartMiniCount');
        const taxEl = document.getElementById('cartMiniTax');

        if (!list || !totalEl || !countEl || !taxEl) {
            return;
        }

        list.innerHTML = '';
        let total = 0;
        let totalCount = 0;
        const rateTotals = {};

        const header = document.createElement('tr');
        header.innerHTML = `
            <th style="text-align: left;">商品名</th>
            <th style="text-align: center;">数量</th>
            <th style="text-align: right;">小計</th>
            <th></th>
        `;
        list.appendChild(header);

        cart.forEach((item, index) => {
            const taxRateValue = parseFloat(taxRateMap[item.taxRateId]) / 100;
            const subtotal = item.price * item.quantity * (1 + taxRateValue);
            const subtotalRounded = Math.round(subtotal);

            total += subtotalRounded;
            totalCount += item.quantity;

            if (!rateTotals[taxRateValue]) {
                rateTotals[taxRateValue] = 0;
            }
            rateTotals[taxRateValue] += item.price * item.quantity;

            let optionsText = '';
            if (item.selectedOptionNames && item.selectedOptionNames.length > 0) {
                optionsText = ` (${item.selectedOptionNames.join(', ')})`;
            }

            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${item.name}${optionsText}</td>
                <td style="text-align: center;">
                    <input type="number" min="1" value="${item.quantity}"
                           onchange="updateQuantity(${index}, this.value)"
                           style="width: 50px;" />
                </td>
                <td style="text-align: right;">${subtotalRounded}円</td>
                <td><button onclick="removeFromCart(event, ${index})">削除</button></td>
            `;
            list.appendChild(row);
        });

        totalEl.textContent = `${total}円`;
        countEl.textContent = `${totalCount}点`;

        taxEl.innerHTML = '';

        Object.entries(rateTotals)
            .sort((a, b) => a[0] - b[0])
            .forEach(([rate, amount]) => {
                const line = document.createElement('div');
                line.textContent = `${(parseFloat(rate) * 100).toFixed(0)}%対象：¥${Math.round(amount)}(税別)`;
                taxEl.appendChild(line);
            });

        if (cart.length === 0) {
            list.innerHTML = '<tr><td colspan="4" style="text-align: center; padding: 10px;">カートは空です</td></tr>';
            totalEl.textContent = '0円';
            countEl.textContent = '0点';
            taxEl.innerHTML = '';
        }
    };

    const addToCart = (button) => {
        const menuId = button.getAttribute('data-menu-id');
        const taxRateId = button.getAttribute('data-tax-rate-id');
        const price = parseFloat(button.getAttribute('data-price'));
        const name = button.getAttribute('data-name');

        const menuDetail = button.closest('.menu-detail');
        const quantityInput = menuDetail ? menuDetail.querySelector('.quantity-input') : null;
        const quantity = quantityInput ? parseInt(quantityInput.value, 10) : NaN;

        if (Number.isNaN(quantity) || quantity <= 0) {
            showToast('数量は1以上を入力してください。');
            return;
        }

        const menuItem = button.closest('.menu-item');
        const optionSelects = menuItem ? menuItem.querySelectorAll('.option-select') : [];

        const selectedOptions = [];
        const selectedOptionNames = [];
        let optionsAllSelected = true;

        optionSelects.forEach(select => {
            if (select.value === '') {
                optionsAllSelected = false;
                return;
            }
            selectedOptions.push(parseInt(select.value, 10));
            const selectedText = select.options[select.selectedIndex].text;
            selectedOptionNames.push(selectedText);
        });

        if (!optionsAllSelected) {
            showToast('全てのオプションを選択してください。', 4000, 'error');
            return;
        }

        const taxRateValue = parseFloat(taxRateMap[taxRateId]) / 100;
        const priceWithTax = Math.round(price * (1 + taxRateValue));

        const existing = cart.find(item =>
            item.menuId === menuId &&
            JSON.stringify(item.selectedOptions.slice().sort()) === JSON.stringify(selectedOptions.slice().sort())
        );

        if (existing) {
            existing.quantity += quantity;
        } else {
            cart.push({ menuId, taxRateId, price, priceWithTax, quantity, name, selectedOptions, selectedOptionNames });
        }

        showToast('カートに追加しました');

        if (menuItem && menuItem.classList.contains('expanded')) {
            toggleDetails(menuItem);
        }

        updateMiniCart();
    };

    const removeFromCart = (event, index) => {
        if (event) {
            event.stopPropagation();
        }
        cart.splice(index, 1);
        showToast('カートから削除しました');
        updateMiniCart();
    };

    const submitOrder = () => {
        if (cart.length === 0) {
            showToast('カートに商品がありません。');
            return;
        }

        const orderItems = cart.map(item => ({
            menuId: parseInt(item.menuId, 10),
            taxRateId: parseInt(item.taxRateId, 10),
            quantity: parseInt(item.quantity, 10),
            optionItemIds: item.selectedOptions || []
        }));

        toggleCart(false);

        fetch('/order/submit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(orderItems)
        }).then(res => {
            if (res.ok) {
                cart.length = 0;
                updateMiniCart();
                showToast('注文を確定しました', 3000);
            } else {
                cart.splice(index, 1);
            }
        }).catch(error => {
            console.error('注文送信中にエラーが発生しました:', error);
            showToast('注文送信中にエラーが発生しました。ネットワーク接続を確認してください。');
        });
    };

    const showDescriptionFromData = (btn) => {
        const title = btn.getAttribute('data-name');
        const desc = btn.getAttribute('data-desc');
        showToast(`${title}\n\n${desc}`, 5000);
    };

    const switchTab = (tabElement) => {
        document.querySelectorAll('.menu-tab').forEach(t => t.classList.remove('active'));
        tabElement.classList.add('active');

        const groupId = tabElement.getAttribute('data-group-id');

        document.querySelectorAll('.menu-item').forEach(item => {
            const itemGroupId = item.getAttribute('data-group-id');
            item.style.display = itemGroupId === groupId ? 'block' : 'none';
        });
    };

    const handleGlobalClick = (e) => {
        const cartPanel = document.getElementById('cartPanel');
        const toggleButton = document.getElementById('cartToggleButton');
        if (cartPanel && toggleButton) {
            const isClickInsideCart =
                cartPanel.contains(e.target) ||
                !!e.target.closest('.cart-button');

            if (cartPanel.classList.contains('show') && !isClickInsideCart) {
                toggleCart(false);
            }
        }

        const historyModal = document.getElementById('historyModal');
        const historyToggleBtn = document.getElementById('historyToggleButton');
        if (historyModal && historyToggleBtn) {
            const isHistoryVisible = historyModal.classList.contains('show');
            const isClickInsideHistory =
                historyModal.contains(e.target) ||
                !!e.target.closest('.history-button');

            if (isHistoryVisible && !isClickInsideHistory) {
                closeHistoryModal();
            }
        }

        document.querySelectorAll('.menu-item.expanded').forEach(item => {
            if (!item.contains(e.target)) {
                toggleDetails(item);
            }
        });
    };

    const setupQuantityControls = () => {
        document.querySelectorAll('.menu-item').forEach(menuItem => {
            const quantityInput = menuItem.querySelector('.quantity-input');
            const minusBtn = menuItem.querySelector('.minus-btn');
            const plusBtn = menuItem.querySelector('.plus-btn');

            if (!quantityInput || !minusBtn || !plusBtn) {
                return;
            }

            minusBtn.addEventListener('click', () => {
                const currentValue = parseInt(quantityInput.value, 10);
                const minValue = parseInt(quantityInput.min, 10) || 1;
                if (currentValue > minValue) {
                    quantityInput.value = currentValue - 1;
                }
            });

            plusBtn.addEventListener('click', () => {
                const currentValue = parseInt(quantityInput.value, 10) || 0;
                quantityInput.value = currentValue + 1;
            });
        });
    };

    const fetchTaxRates = () => {
        fetch('/taxrates')
            .then(res => res.json())
            .then(data => {
                data.forEach(rate => {
                    taxRateMap[rate.taxRateId] = Math.round(rate.rate * 100);
                });
            })
            .catch(err => {
                console.error('税率の取得に失敗しました', err);
            });
    };

    const setupMenuTabSwitching = () => {
        document.querySelectorAll('.menu-tab').forEach(tab => {
            tab.addEventListener('click', () => switchTab(tab));
        });
    };

    const setupInfoButtons = () => {
        document.querySelectorAll('.info-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                showDescriptionFromData(btn);
            });
        });
    };

    const setupMenuItemToggle = () => {
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
    };

    const setupAddCartButtons = () => {
        document.querySelectorAll('.add-cart-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                addToCart(btn);
            });
        });
    };

    const applyInitialTabSelection = () => {
        const firstNonPlanTargetTab = document.querySelector('.menu-tab:not([data-is-plan-target="true"])');
        if (firstNonPlanTargetTab) {
            switchTab(firstNonPlanTargetTab);
            return;
        }

        const anyTab = document.querySelector('.menu-tab');
        if (anyTab) {
            switchTab(anyTab);
        }
    };

    const setupBackToSeatList = () => {
        const container = document.querySelector('div#backToSeatList');
        const link = document.querySelector('a#backToSeatList');
        const params = new URLSearchParams(window.location.search);

        const handleBackClick = () => {
            document.cookie = 'visitId=; Max-Age=0; path=/';
        };

        if (container) {
            container.addEventListener('click', handleBackClick);
        }
        if (link) {
            link.addEventListener('click', handleBackClick);
        }

        if (params.get('from') === 'seatlist' && container) {
            container.style.display = 'block';
        }
    };

    const showSeatInfo = () => {
        const seatInfoEl = document.getElementById('seatInfo');
        if (seatInfoEl) {
            seatInfoEl.innerText = `${seatId}`;
        }
    };

    const setupWebSocket = () => {
        const socket = new SockJS('/ws-endpoint');
        const stompClient = Stomp.over(socket);

        stompClient.connect({}, () => {
            if (typeof seatId === 'undefined' || seatId === null) {
                return;
            }

            stompClient.subscribe(`/topic/seats/${seatId}`, (message) => {
                const body = JSON.parse(message.body);
                console.log('WebSocketメッセージ受信 (seatsトピック):', body);

                if (body.type === 'LEAVE') {
                    document.cookie = 'visitId=; Max-Age=0; path=/';
                    window.location.href = '/visits/orderwait';
                } else if (body.type === 'PLAN_ACTIVATED') {
                    const activatedMenuGroupIds = body.activatedMenuGroupIds;
                    const activatedPlanId = body.planId;

                    console.log(`プラン ${activatedPlanId} がシート ${seatId} でアクティブ化されました。`);
                    console.log('表示されるメニューグループID:', activatedMenuGroupIds);

                    document.querySelectorAll('.menu-tab[data-is-plan-target="true"]').forEach(tab => {
                        tab.classList.remove('active-plan-group');
                    });
                    document.querySelectorAll('.menu-item[data-is-plan-target="true"]').forEach(item => {
                        item.classList.remove('active-plan-menu');
                    });

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
                    window.location.href = currentUrl.toString();
                }
            }, (error) => {
                console.error('STOMP error:', error);
            });

            stompClient.subscribe(`/topic/printer/${seatId}`, (message) => {
                const payload = JSON.parse(message.body);
                console.log('WebSocketメッセージ受信 (printerトピック):', payload);
                if (payload.type === 'PRINT_COMMANDS') {
                    enqueuePrintJob(payload.ip, payload.commands);
                } else if (payload.type === 'PRINT_ERROR') {
                    alert('印刷エラー: ' + payload.message);
                    console.error('印刷エラー:', payload.message);
                    updatePrinterStatus('エラー: ' + payload.message);
                }
            }, (error) => {
                console.error('STOMP error for /topic/printer:', error);
                updatePrinterStatus('WebSocket購読エラー (printer): ' + error);
            });
        });
    };

    const initOrderPage = () => {
        showSeatInfo();
        setupQuantityControls();
        fetchTaxRates();
        setupMenuTabSwitching();
        setupInfoButtons();
        setupMenuItemToggle();
        setupAddCartButtons();
        setupBackToSeatList();
        window.addEventListener('click', handleGlobalClick);
        applyInitialTabSelection();
        updateMiniCart();
        handleUrlToastMessage();
        setupWebSocket();
    };

    document.addEventListener('DOMContentLoaded', initOrderPage);

    Object.assign(window, {
        toggleHistory,
        closeHistoryModal,
        toggleCart,
        updateQuantity,
        removeFromCart,
        submitOrder
    });
})();
