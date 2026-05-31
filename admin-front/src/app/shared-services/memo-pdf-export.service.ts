import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class MemoPdfExportService {

  exportMemo(memo: any, approvalHistory: any[]): void {
    try {
      const amountFormatted = memo.amount
        ? 'NPR ' + Number(memo.amount).toLocaleString('en-IN')
        : '-';

      const statusLabels: Record<string, string> = {
        DRAFT: 'Draft',
        PENDING_APPROVAL: 'Pending Approval',
        APPROVED: 'Approved',
        REJECTED: 'Rejected',
        REVISION_REQUESTED: 'Returned for Revision',
        CANCELLED: 'Cancelled',
        SUBMITTED: 'Submitted',
        FORWARDED: 'Forwarded'
      };
      const statusLabel = statusLabels[memo.status] || memo.status || '-';

      const priorityLabel = memo.priority
        ? memo.priority.charAt(0) + memo.priority.slice(1).toLowerCase()
        : '-';

      const safeVal = (val: any, fallback = '-') =>
        val != null && val !== '' ? val : fallback;

      const historyRows = (approvalHistory || []).map(h => `
        <tr>
          <td>${safeVal(h.action)}</td>
          <td>${h.approver?.name || h.approverName || '-'}</td>
          <td>${h.actionDate ? new Date(h.actionDate).toLocaleString('en-IN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'}</td>
          <td>${safeVal(h.remarks, '')}</td>
        </tr>
      `).join('');

      const printContent = `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Memo - ${safeVal(memo.memoNo)}</title>
<style>
  @page { margin: 18mm 15mm; size: A4; }
  * { box-sizing: border-box; margin: 0; padding: 0; }

  body {
    font-family: 'Segoe UI', Roboto, -apple-system, BlinkMacSystemFont, sans-serif;
    font-size: 11pt;
    line-height: 1.55;
    color: #1e293b;
    background: #fff;
    padding: 0;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .print-wrapper {
    max-width: 210mm;
    margin: 0 auto;
    position: relative;
  }

  .watermark {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%) rotate(-30deg);
    font-size: 80pt;
    font-weight: 900;
    color: rgba(79, 70, 229, 0.035);
    letter-spacing: 12px;
    text-transform: uppercase;
    pointer-events: none;
    z-index: 0;
    white-space: nowrap;
    font-family: 'Segoe UI', sans-serif;
  }

  .border-frame {
    border: 2px solid #e2e8f0;
    border-radius: 8px;
    padding: 32px 36px;
    position: relative;
    z-index: 1;
    background: #fff;
  }

  .header {
    text-align: center;
    border-bottom: 3px solid #4f46e5;
    padding-bottom: 16px;
    margin-bottom: 24px;
  }

  .header h1 {
    font-size: 22pt;
    font-weight: 700;
    color: #1e293b;
    letter-spacing: 1px;
    margin-bottom: 2px;
  }

  .header .company {
    font-size: 10pt;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 2px;
  }

  .header .memo-no {
    font-size: 9pt;
    color: #4f46e5;
    font-weight: 600;
    margin-top: 4px;
  }

  .status-priority {
    display: flex;
    justify-content: center;
    gap: 12px;
    margin-top: 8px;
    font-size: 9pt;
  }

  .status-priority .tag {
    display: inline-block;
    padding: 2px 14px;
    border-radius: 20px;
    font-weight: 600;
    font-size: 8pt;
    letter-spacing: 0.5px;
  }

  .tag-status { background: #eef2ff; color: #4f46e5; }
  .tag-priority-low { background: #f1f5f9; color: #475569; }
  .tag-priority-medium { background: #eef2ff; color: #4f46e5; }
  .tag-priority-high { background: #fef3c7; color: #92400e; }
  .tag-priority-urgent { background: #fee2e2; color: #991b1b; }
  .tag-urgent-badge { background: #fef2f2; color: #dc2626; font-weight: 700; }

  .section-title {
    font-size: 10pt;
    font-weight: 700;
    color: #334155;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    border-bottom: 1px solid #e2e8f0;
    padding-bottom: 4px;
    margin-bottom: 10px;
  }

  .info-grid {
    display: grid;
    grid-template-columns: 1fr 1fr 1fr;
    gap: 10px 20px;
    margin-bottom: 18px;
  }

  .info-grid .field {
    break-inside: avoid;
  }

  .info-grid .field .label {
    font-size: 7.5pt;
    font-weight: 600;
    color: #94a3b8;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 1px;
  }

  .info-grid .field .value {
    font-size: 10pt;
    color: #1e293b;
    word-break: break-word;
  }

  .info-grid .field .value.mono {
    font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
    font-size: 9.5pt;
  }

  .description-box {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 14px 16px;
    margin-bottom: 20px;
    white-space: pre-wrap;
    font-size: 10pt;
    line-height: 1.65;
    color: #334155;
  }

  .approval-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 9.5pt;
    margin-bottom: 20px;
  }

  .approval-table th {
    background: #f1f5f9;
    color: #475569;
    font-weight: 600;
    font-size: 7.5pt;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    padding: 8px 10px;
    text-align: left;
    border-bottom: 2px solid #cbd5e1;
  }

  .approval-table td {
    padding: 7px 10px;
    border-bottom: 1px solid #e2e8f0;
    color: #1e293b;
  }

  .approval-table tr:last-child td {
    border-bottom: none;
  }

  .footer {
    text-align: center;
    font-size: 8pt;
    color: #94a3b8;
    border-top: 1px solid #e2e8f0;
    padding-top: 14px;
    margin-top: 8px;
  }

  .footer .generated-stamp {
    font-style: italic;
    margin-top: 2px;
    font-size: 7.5pt;
  }

  @media print {
    body { background: #fff; }
    .watermark { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .border-frame { border-color: #cbd5e1; }
  }
</style>
</head>
<body>
<div class="print-wrapper">
  <div class="watermark">NEXMEMO</div>
  <div class="border-frame">

    <div class="header">
      <div class="company">${safeVal(memo.companyName || 'NexMemo')}</div>
      <h1>MEMO</h1>
      <div class="memo-no">${safeVal(memo.memoNo)}</div>
      <div class="status-priority">
        <span class="tag tag-status">${statusLabel}</span>
        <span class="tag tag-priority-${(memo.priority || 'medium').toLowerCase()}">${priorityLabel}</span>
        ${memo.isUrgent ? '<span class="tag tag-urgent-badge">URGENT</span>' : ''}
      </div>
    </div>

    <div class="info-grid">
      <div class="field">
        <div class="label">Memo Title</div>
        <div class="value"><strong>${safeVal(memo.title)}</strong></div>
      </div>
      <div class="field">
        <div class="label">Memo Type</div>
        <div class="value">${memo.memoType?.name || safeVal(memo.memoTypeName)}</div>
      </div>
      <div class="field">
        <div class="label">Department</div>
        <div class="value">${memo.department?.name || safeVal(memo.departmentName)}</div>
      </div>
      <div class="field">
        <div class="label">Branch</div>
        <div class="value">${memo.branch?.name || safeVal(memo.branchName)}</div>
      </div>
      <div class="field">
        <div class="label">Amount</div>
        <div class="value mono">${amountFormatted}</div>
      </div>
      <div class="field">
        <div class="label">Fiscal Year</div>
        <div class="value">${memo.fiscalYear?.name || memo.fiscalYear?.fiscalYearName || '-'}</div>
      </div>
    </div>

    ${(memo.expenseCategory || memo.budgetCode || memo.paymentMethod) ? `
    <div class="section-title">Expense Details</div>
    <div class="info-grid">
      ${memo.expenseCategory ? `
      <div class="field">
        <div class="label">Expense Category</div>
        <div class="value">${memo.expenseCategory?.categoryName || memo.expenseCategory?.name || '-'}</div>
      </div>` : ''}
      ${memo.budgetCode ? `
      <div class="field">
        <div class="label">Budget Code</div>
        <div class="value mono">${safeVal(memo.budgetCode)}</div>
      </div>` : ''}
      ${memo.paymentMethod ? `
      <div class="field">
        <div class="label">Payment Method</div>
        <div class="value">${String(memo.paymentMethod).replace(/_/g, ' ')}</div>
      </div>` : ''}
      ${memo.vendorName ? `
      <div class="field">
        <div class="label">Vendor / Payee</div>
        <div class="value">${safeVal(memo.vendorName)}</div>
      </div>` : ''}
      ${memo.vendorAccount ? `
      <div class="field">
        <div class="label">Vendor Account</div>
        <div class="value mono">${safeVal(memo.vendorAccount)}</div>
      </div>` : ''}
      ${memo.expectedDate ? `
      <div class="field">
        <div class="label">Expected Date</div>
        <div class="value">${new Date(memo.expectedDate).toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' })}</div>
      </div>` : ''}
    </div>` : ''}

    <div class="section-title">Approval Info</div>
    <div class="info-grid">
      <div class="field">
        <div class="label">Created By</div>
        <div class="value">${memo.createdByUser?.name || safeVal(memo.createdByName)}</div>
      </div>
      <div class="field">
        <div class="label">Created Date</div>
        <div class="value">${memo.createdDate ? new Date(memo.createdDate).toLocaleString('en-IN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'}</div>
      </div>
      <div class="field">
        <div class="label">Current Approver</div>
        <div class="value">${memo.currentApprover?.name || safeVal(memo.currentApproverName)}</div>
      </div>
      <div class="field">
        <div class="label">Approval Level</div>
        <div class="value">Level ${safeVal(memo.approvalLevel)} / ${safeVal(memo.totalApprovalLevels)}</div>
      </div>
    </div>

    <div class="section-title">Description</div>
    <div class="description-box">${safeVal(memo.description, 'No description provided.')}</div>

    ${historyRows ? `
    <div class="section-title">Approval Timeline</div>
    <table class="approval-table">
      <thead>
        <tr>
          <th style="width:18%">Action</th>
          <th style="width:22%">Approver</th>
          <th style="width:28%">Date</th>
          <th style="width:32%">Remarks</th>
        </tr>
      </thead>
      <tbody>
        ${historyRows}
      </tbody>
    </table>` : ''}

    <div class="footer">
      <div>This is a system-generated document</div>
      <div class="generated-stamp">Generated on ${new Date().toLocaleString('en-IN', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
    </div>

  </div>
</div>
</body>
</html>`;

      const printWindow = window.open('', '_blank', 'width=1024,height=768,scrollbars=yes,resizable=yes');
      if (!printWindow) {
        console.error('MemoPdfExportService: Unable to open print window. Check pop-up blocker.');
        return;
      }
      printWindow.document.write(printContent);
      printWindow.document.close();

      printWindow.onafterprint = () => {
        printWindow.close();
      };

      setTimeout(() => {
        try {
          printWindow.focus();
          printWindow.print();
        } catch (e) {
          console.error('MemoPdfExportService: Print failed', e);
          printWindow.close();
        }
      }, 350);
    } catch (err) {
      console.error('MemoPdfExportService: Export failed', err);
    }
  }
}
