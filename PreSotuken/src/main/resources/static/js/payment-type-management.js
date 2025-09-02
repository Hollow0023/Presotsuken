async function createPaymentType() {
    const storeId = parseInt(document.getElementById('currentStoreId').value, 10);
    const typeName = document.getElementById('newPaymentTypeName').value;
    const isInspectionTarget = document.getElementById('newIsInspectionTarget').checked;
    await fetch('/payment-types', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ storeId, typeName, isInspectionTarget })
    });
    location.reload();
}

async function updatePaymentType(li) {
    const typeId = parseInt(li.dataset.typeId, 10);
    const storeId = parseInt(document.getElementById('currentStoreId').value, 10);
    const typeName = li.querySelector('.type-name-input').value;
    const isInspectionTarget = li.querySelector('.inspection-checkbox').checked;
    await fetch(`/payment-types/${typeId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ typeId, storeId, typeName, isInspectionTarget })
    });
    location.reload();
}

async function deletePaymentType(typeId) {
    await fetch(`/payment-types/${typeId}`, { method: 'DELETE' });
    location.reload();
}
