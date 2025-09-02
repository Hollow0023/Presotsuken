package com.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.entity.PaymentType;
import com.order.repository.PaymentTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentTypeService {

    private final PaymentTypeRepository paymentTypeRepository;

    public List<PaymentType> getPaymentTypesByStoreId(int storeId) {
        return paymentTypeRepository.findByStoreId(storeId);
    }

    @Transactional
    public PaymentType createPaymentType(PaymentType paymentType) {
        return paymentTypeRepository.save(paymentType);
    }

    @Transactional
    public PaymentType updatePaymentType(PaymentType updatedPaymentType) {
        return paymentTypeRepository.findById(updatedPaymentType.getTypeId())
                .map(existing -> {
                    existing.setStoreId(updatedPaymentType.getStoreId());
                    existing.setTypeName(updatedPaymentType.getTypeName());
                    existing.setIsInspectionTarget(updatedPaymentType.getIsInspectionTarget());
                    return paymentTypeRepository.save(existing);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "PaymentType with ID " + updatedPaymentType.getTypeId() + " not found."));
    }

    @Transactional
    public void deletePaymentType(int typeId) {
        if (!paymentTypeRepository.existsById(typeId)) {
            throw new IllegalArgumentException("PaymentType with ID " + typeId + " not found.");
        }
        paymentTypeRepository.deleteById(typeId);
    }
}

