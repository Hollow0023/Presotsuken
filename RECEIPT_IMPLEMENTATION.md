# Receipt Printing Feature - Implementation Summary

## Overview

This document provides a technical summary of the receipt printing feature implementation for the POS system.

## Key Features Implemented

### 1. Immediate Receipt Printing (Flow ①)
- Checkbox on payment screen to enable receipt printing
- Modal dialog after payment completion with two modes:
  - **Full Amount**: Issue receipt for entire payment
  - **Specified Amount**: Issue receipt for partial amount with proportional tax allocation

### 2. Receipt Management from History (Flow ②)
- Receipt management section in payment history detail modal
- Display of:
  - Remaining balance by tax rate (10% and 8%)
  - List of issued receipts with details
  - Receipt status (valid/voided)
- Operations:
  - Issue new receipts
  - Reprint existing receipts
  - Void receipts

### 3. Multi-Receipt Support
- Track remaining amounts per payment
- Proportional allocation for subsequent receipts
- Automatic tax breakdown calculation

## Technical Architecture

### Backend Components

#### Entities
- **Receipt**: Core entity with all receipt fields
- Fields include tax breakdowns, issue info, void status, and idempotency key

#### Services
- **ReceiptService**: Business logic for receipt operations
  - Issue receipts with allocation algorithm
  - Reprint with counter increment
  - Void functionality
  - Remaining amount calculation
- **PrintService**: Printer integration
  - Official receipt printing method
  - ePOS-Print command generation
  - QR code integration

#### Controllers
- **ReceiptController**: REST API endpoints
  - POST /receipts/issue
  - POST /receipts/{id}/reprint
  - POST /receipts/{id}/void
  - GET /receipts/payment/{id}
  - GET /receipts/payment/{id}/remaining

### Frontend Components

#### Payment Screen (payment.html)
- Receipt checkbox control
- Receipt issuance modal with tabs
- Integration with payment finalization flow

#### Payment History (paymentHistory.html)
- Receipt management section
- Receipt list display
- Reprint and void buttons
- New receipt issuance functionality

## Algorithm Implementation

### Proportional Allocation Algorithm

For specified amount mode:

```
1. Calculate proportions:
   p10 = remaining10 / (remaining10 + remaining8)
   p08 = remaining8 / (remaining10 + remaining8)

2. Allocate amount:
   amount10 = issueAmount × p10 (rounded HALF_UP)
   amount8 = issueAmount - amount10

3. Calculate tax breakdown (reverse calculation):
   For 10%: net10 = round(amount10 / 1.10)
           tax10 = amount10 - net10
   For 8%:  net8 = round(amount8 / 1.08)
           tax8 = amount8 - net8

4. Adjust for rounding errors:
   If difference ≤ ±1円, adjust tax amounts
   Priority: tax10 → tax8
```

### Rounding Rules
- **Method**: HALF_UP (standard rounding)
- **Applied to**:
  - Amount allocation
  - Net amount calculation
  - Final tax adjustment

## Database Schema

### receipt Table

```sql
CREATE TABLE receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    payment_id INT NOT NULL,
    net_amount_10 DECIMAL(10,2) NOT NULL DEFAULT 0,
    net_amount_8 DECIMAL(10,2) NOT NULL DEFAULT 0,
    tax_amount_10 DECIMAL(10,2) NOT NULL DEFAULT 0,
    tax_amount_8 DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL,
    issued_by INT NOT NULL,
    issued_at DATETIME NOT NULL,
    receipt_no VARCHAR(50) NOT NULL UNIQUE,
    reprint_count INT NOT NULL DEFAULT 0,
    voided BOOLEAN NOT NULL DEFAULT FALSE,
    voided_at DATETIME,
    voided_by INT,
    idempotency_key VARCHAR(100),
    FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    FOREIGN KEY (issued_by) REFERENCES user(user_id),
    FOREIGN KEY (voided_by) REFERENCES user(user_id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_receipt_no (receipt_no),
    INDEX idx_idempotency_key (idempotency_key)
);
```

## Print Specifications

### Receipt Format

The official receipt includes:

1. **Header**:
   - Title "領収書" (Receipt) - large, centered
   - Store name
   - Issue datetime
   - Receipt number (format: YYYYMMDD-NNNN)
   - Reprint indicator (if applicable)

2. **Body**:
   - Total amount (large, centered)
   - Tax rate breakdown section
   - For each tax rate (10% and 8%):
     - Net amount (税抜)
     - Tax amount (税額)
     - Total with tax (税込)
   - Verification total

3. **Footer**:
   - Payment ID
   - Receipt ID
   - Issuer name
   - QR code (PDF417) containing receipt number

### Printer Integration

- Uses existing ePOS-Print infrastructure
- Sends JSON commands via WebSocket to frontend
- Frontend handles actual printer communication
- Error-tolerant: Receipt is saved even if printing fails

## Security Features

### Idempotency
- Prevents duplicate issuance with same key
- Key format: `{paymentId}-{timestamp}-{random}`
- Returns existing receipt if duplicate detected

### Audit Trail
- All operations logged:
  - Issue: user, datetime, amounts
  - Reprint: user, datetime, count
  - Void: user, datetime, reason
- Log level: INFO

### Authorization
- Issue: Requires cashier permissions
- Reprint: Requires cashier permissions
- Void: Should require manager permissions (implement in frontend)

## Error Handling

### Service Layer
- Validates payment existence
- Validates user permissions
- Checks remaining amounts
- Handles idempotency
- Logs all errors

### Print Layer
- Catches print exceptions
- Logs errors but doesn't fail transaction
- Allows manual reprint if needed

### Frontend
- Validates input amounts
- Displays user-friendly error messages
- Handles network errors gracefully

## Testing Status

### Compilation
- ✅ All Java code compiles successfully
- ✅ No syntax errors
- ✅ All dependencies resolved

### Components
- ✅ Entity structure validated
- ✅ Repository interfaces defined
- ✅ Service logic implemented
- ✅ Controller endpoints defined
- ✅ Frontend integration complete

### Required Manual Testing
- Database schema creation
- Payment flow with receipt checkbox
- Receipt modal functionality
- History screen receipt management
- Printer output verification
- Multi-receipt scenarios
- Void and reprint operations

## Deployment Steps

1. **Database Migration**:
   ```sql
   Execute: sql/receipt_table_creation.sql
   ```

2. **Build**:
   ```bash
   ./gradlew build
   ```

3. **Deploy**:
   - Deploy application
   - Verify printer configuration
   - Test receipt printing

4. **Verification**:
   - Create test payment
   - Issue receipt with checkbox
   - Verify print output
   - Test history screen functions
   - Test multi-receipt issuance

## Configuration Requirements

### Printer Setup
- Ensure receipt printer is configured in printer management
- Set "Receipt Output" flag for designated printer
- Verify printer IP address and connectivity

### Store Setup
- Each store must have a receipt printer configured
- System uses `receipt_output` flag to identify receipt printer

## Known Limitations

1. **Tax Rates**: Currently hardcoded for 10% and 8% rates
2. **Printer Dependency**: Requires ePOS-compatible printer
3. **QR Code**: Uses PDF417 format (may not be readable by all scanners)
4. **Currency**: Assumes JPY with no decimal places in display

## Future Improvements

1. **Dynamic Tax Rates**: Support configurable tax rates
2. **Receipt Templates**: Customizable receipt layouts
3. **Digital Receipts**: Email/PDF export options
4. **Bulk Operations**: Bulk reprint or void functionality
5. **Receipt Preview**: Preview before printing
6. **Enhanced QR**: Support QR Code format alongside PDF417

## Documentation

- **User Guide**: RECEIPT_FEATURE.md (Japanese)
- **API Documentation**: See ReceiptController comments
- **Database Schema**: sql/receipt_table_creation.sql
- **Algorithm Details**: ReceiptService class comments

## Support

For issues or questions:
1. Check logs for error messages
2. Verify printer configuration
3. Confirm database schema is created
4. Review API endpoint responses
5. Check browser console for frontend errors
