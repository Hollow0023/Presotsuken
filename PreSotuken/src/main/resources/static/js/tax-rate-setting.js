/**
 * 税率設定ページのJavaScript
 */

/**
 * 新しい税率を作成
 */
async function createTaxRate() {
    const rateValue = document.getElementById('newTaxRate').value;
    
    if (!rateValue || isNaN(rateValue)) {
        alert('税率を入力してください');
        return;
    }
    
    const rate = parseFloat(rateValue);
    if (rate < 0 || rate > 100) {
        alert('税率は0から100の間で入力してください');
        return;
    }
    
    try {
        const response = await fetch('/taxrates', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rate })
        });
        
        if (response.ok) {
            location.reload();
        } else {
            alert('税率の追加に失敗しました');
        }
    } catch (error) {
        console.error('Error creating tax rate:', error);
        alert('税率の追加に失敗しました');
    }
}

/**
 * 税率を更新
 */
async function updateTaxRate(li) {
    const taxRateId = parseInt(li.dataset.rateId, 10);
    const rateInput = li.querySelector('.rate-input');
    const rateValue = rateInput.value;
    
    if (!rateValue || isNaN(rateValue)) {
        alert('税率を入力してください');
        return;
    }
    
    const rate = parseFloat(rateValue);
    if (rate < 0 || rate > 100) {
        alert('税率は0から100の間で入力してください');
        return;
    }
    
    try {
        const response = await fetch(`/taxrates/${taxRateId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ taxRateId, rate })
        });
        
        if (response.ok) {
            li.classList.add('highlight-success');
            setTimeout(() => {
                li.classList.remove('highlight-success');
            }, 1500);
        } else {
            alert('税率の更新に失敗しました');
        }
    } catch (error) {
        console.error('Error updating tax rate:', error);
        alert('税率の更新に失敗しました');
    }
}

/**
 * 税率を削除
 */
async function deleteTaxRate(taxRateId) {
    if (!confirm('この税率を削除してもよろしいですか？')) {
        return;
    }
    
    try {
        const response = await fetch(`/taxrates/${taxRateId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            location.reload();
        } else {
            alert('税率の削除に失敗しました');
        }
    } catch (error) {
        console.error('Error deleting tax rate:', error);
        alert('税率の削除に失敗しました');
    }
}
