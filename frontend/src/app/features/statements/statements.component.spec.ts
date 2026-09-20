import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FinancialDocument, StatementImportResult, StatementSettlement } from '../../core/api/api.types';
import { StatementService } from './statement.service';
import { StatementsComponent } from './statements.component';

describe('StatementsComponent', () => {
  let component: StatementsComponent;
  let fixture: ComponentFixture<StatementsComponent>;
  let statementServiceSpy: jasmine.SpyObj<StatementService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockDocument: FinancialDocument = {
    id: 'doc-abc-123',
    originalFilename: 'hdfc_statement_aug.pdf',
    fileType: 'PDF',
    fileSizeBytes: 245000,
    contentHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    institutionName: 'HDFC Bank',
    accountType: 'SAVINGS',
    accountNumberLast4: '5678',
    statementStartDate: '2026-08-01',
    statementEndDate: '2026-08-31',
    status: 'COMPLETED',
    totalRowsDetected: 42,
    importedCount: 38,
    duplicateCount: 2,
    errorMessage: null,
    createdAt: '2026-09-15T10:00:00Z',
    updatedAt: '2026-09-15T10:01:00Z',
  };

  const mockImportResult: StatementImportResult = {
    documentId: 'doc-abc-123',
    totalRows: 42,
    validRows: 40,
    invalidRows: 2,
    importedTransactions: 38,
    exactDuplicates: 2,
    probableDuplicates: 1,
    unresolvedCandidates: 1,
    settlementsResolved: 2,
    skippedRows: 1,
    warnings: ['Row 15: Low confidence parsing for UPI merchant name.'],
  };

  const mockSettlement: StatementSettlement = {
    id: 'set-999',
    documentId: 'doc-abc-123',
    rowIndex: 8,
    transactionDate: '2026-08-10',
    amount: 25000.0,
    rawMerchant: 'CREDIT CARD PAYMENT HDFC',
    rawDescription: 'AUTOPAY CC BILL PAYMENT',
    classification: 'PAYMENT',
    accountType: 'SAVINGS',
    externalReference: 'HDFCCC9988',
    status: 'RESOLVED',
    matchedSettlementId: 'set-card-111',
  };

  beforeEach(async () => {
    statementServiceSpy = jasmine.createSpyObj('StatementService', [
      'list',
      'get',
      'uploadAndIngest',
      'reingest',
      'listSettlements',
      'delete',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    statementServiceSpy.list.and.returnValue(
      of({ data: [mockDocument], error: null, meta: null }),
    );
    statementServiceSpy.listSettlements.and.returnValue(
      of({ data: [mockSettlement], error: null, meta: null }),
    );

    await TestBed.configureTestingModule({
      imports: [StatementsComponent],
      providers: [
        { provide: StatementService, useValue: statementServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StatementsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // 1. Initial Loading
  it('1. loads and displays statement history on init', () => {
    expect(statementServiceSpy.list).toHaveBeenCalled();
    expect(component.statements.length).toBe(1);
    expect(component.statements[0].originalFilename).toBe('hdfc_statement_aug.pdf');
    expect(component.loading).toBeFalse();
  });

  // 2. File type validation: valid extensions
  it('2. accepts valid statement extensions (pdf, csv, xlsx, xls)', () => {
    const pdfFile = new File(['dummy'], 'statement.pdf', { type: 'application/pdf' });
    component.validateAndSetFile(pdfFile);
    expect(component.selectedFile).toBe(pdfFile);
    expect(component.uploadError).toBe('');

    const csvFile = new File(['dummy'], 'statement.csv', { type: 'text/csv' });
    component.validateAndSetFile(csvFile);
    expect(component.selectedFile).toBe(csvFile);

    const xlsxFile = new File(['dummy'], 'statement.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    component.validateAndSetFile(xlsxFile);
    expect(component.selectedFile).toBe(xlsxFile);
  });

  // 3. File type validation: invalid extension
  it('3. rejects unsupported file formats with error message', () => {
    const txtFile = new File(['dummy'], 'statement.txt', { type: 'text/plain' });
    component.validateAndSetFile(txtFile);
    expect(component.selectedFile).toBeNull();
    expect(component.uploadError).toContain('Unsupported file type');
  });

  // 4. File size validation: exceeds 15MB
  it('4. rejects files exceeding 15MB size limit', () => {
    const largeFile = new File([''], 'huge.pdf', { type: 'application/pdf' });
    Object.defineProperty(largeFile, 'size', { value: 16 * 1024 * 1024 });

    component.validateAndSetFile(largeFile);
    expect(component.selectedFile).toBeNull();
    expect(component.uploadError).toContain('15 MB maximum size limit');
  });

  // 5. Clear selected file
  it('5. clears selected file when remove button is clicked', () => {
    const file = new File(['dummy'], 'statement.csv', { type: 'text/csv' });
    component.validateAndSetFile(file);
    expect(component.selectedFile).toBe(file);

    component.clearSelectedFile();
    expect(component.selectedFile).toBeNull();
    expect(component.uploadError).toBe('');
  });

  // 6. Drag & drop handlers
  it('6. handles drag over, drag leave and drop events', () => {
    const dragEvent = new DragEvent('dragover');
    spyOn(dragEvent, 'preventDefault');
    spyOn(dragEvent, 'stopPropagation');
    component.onDragOver(dragEvent);
    expect(component.isDragOver).toBeTrue();

    const leaveEvent = new DragEvent('dragleave');
    component.onDragLeave(leaveEvent);
    expect(component.isDragOver).toBeFalse();

    const testFile = new File(['test'], 'bank.csv', { type: 'text/csv' });
    const dataTransfer = new DataTransfer();
    dataTransfer.items.add(testFile);
    const dropEvent = new DragEvent('drop', { dataTransfer });
    component.onDrop(dropEvent);

    expect(component.isDragOver).toBeFalse();
    expect(component.selectedFile?.name).toBe('bank.csv');
  });

  // 7. Form default values and validation
  it('7. initializes metadata form with SAVINGS default and validates last4', () => {
    expect(component.uploadForm.controls.accountType.value).toBe('SAVINGS');

    component.uploadForm.controls.accountNumberLast4.setValue('12345'); // >4 chars
    expect(component.uploadForm.controls.accountNumberLast4.valid).toBeFalse();

    component.uploadForm.controls.accountNumberLast4.setValue('1234'); // 4 chars
    expect(component.uploadForm.controls.accountNumberLast4.valid).toBeTrue();
  });

  // 8. Upload submission success
  it('8. submits upload and captures import result and document', () => {
    const file = new File(['data'], 'test.csv', { type: 'text/csv' });
    component.validateAndSetFile(file);
    component.uploadForm.patchValue({
      accountType: 'SAVINGS',
      institutionName: 'HDFC',
      accountNumberLast4: '5678',
    });

    statementServiceSpy.uploadAndIngest.and.returnValue(
      of({
        data: {
          document: mockDocument,
          importResult: mockImportResult,
        },
        error: null,
        meta: null,
      }),
    );

    component.submitUpload();

    expect(statementServiceSpy.uploadAndIngest).toHaveBeenCalled();
    expect(component.latestImportResult).toEqual(mockImportResult);
    expect(component.latestImportDocument).toEqual(mockDocument);
    expect(component.selectedFile).toBeNull();
  });

  // 9. Upload failure handling
  it('9. handles upload errors gracefully with backend error message', () => {
    const file = new File(['data'], 'test.csv', { type: 'text/csv' });
    component.validateAndSetFile(file);

    statementServiceSpy.uploadAndIngest.and.returnValue(
      throwError(() => ({
        error: { error: { message: 'Duplicate document: Identical statement content already exists.' } },
      })),
    );

    component.submitUpload();

    expect(component.uploading).toBeFalse();
    expect(component.uploadError).toBe('Duplicate document: Identical statement content already exists.');
  });

  // 10. Re-ingest statement
  it('10. re-processes a statement and updates latest import summary', () => {
    statementServiceSpy.reingest.and.returnValue(
      of({
        data: {
          document: mockDocument,
          importResult: mockImportResult,
        },
        error: null,
        meta: null,
      }),
    );

    component.reingestStatement(mockDocument);

    expect(statementServiceSpy.reingest).toHaveBeenCalledWith(mockDocument.id);
    expect(component.latestImportResult).toEqual(mockImportResult);
  });

  // 11. Delete statement with confirmation
  it('11. deletes a statement when confirmed', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    statementServiceSpy.delete.and.returnValue(of({ data: null, error: null, meta: null }));

    component.deleteStatement(mockDocument);

    expect(statementServiceSpy.delete).toHaveBeenCalledWith(mockDocument.id);
    expect(statementServiceSpy.list).toHaveBeenCalled();
  });

  // 12. Delete statement canceled
  it('12. does not delete statement when user cancels confirmation', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.deleteStatement(mockDocument);

    expect(statementServiceSpy.delete).not.toHaveBeenCalled();
  });

  // 13. Open Details modal - Summary tab
  it('13. opens document details modal in summary tab', () => {
    component.openDetails(mockDocument);
    expect(component.activeDocument).toBe(mockDocument);
    expect(component.activeDetailTab).toBe('summary');
  });

  // 14. Switch to Settlements tab and load settlements
  it('14. switches to settlements tab and fetches settlements', () => {
    component.openDetails(mockDocument, 'settlements');
    expect(component.activeDetailTab).toBe('settlements');
    expect(statementServiceSpy.listSettlements).toHaveBeenCalledWith(mockDocument.id);
    expect(component.activeSettlements.length).toBe(1);
    expect(component.activeSettlements[0].id).toBe('set-999');
  });

  // 15. Financial Semantics Guarantee - Neutral settlement representation
  it('15. verifies settlement classification label indicates internal settlement/transfer', () => {
    const paymentLabel = component.getSettlementClassificationLabel('PAYMENT');
    expect(paymentLabel).toContain('Internal settlement');

    const transferLabel = component.getSettlementClassificationLabel('TRANSFER');
    expect(transferLabel).toContain('Internal transfer');

    const refundLabel = component.getSettlementClassificationLabel('REFUND');
    expect(refundLabel).toContain('Expense offset');
  });

  // 16. Financial Semantics Guarantee - Template settlement rendering has no + or - sign
  it('16. renders settlement amounts neutrally without + or - sign', () => {
    component.openDetails(mockDocument, 'settlements');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const neutralAmountElem = compiled.querySelector('.settlement-neutral-amount');
    expect(neutralAmountElem).toBeTruthy();
    const text = neutralAmountElem?.textContent?.trim() || '';

    expect(text).not.toContain('+');
    expect(text).not.toContain('-');
    expect(text).toContain('25,000');
  });

  // 17. Financial Semantics Guarantee - Callout explains net effect
  it('17. renders the financial semantics guarantee callout in settlements tab', () => {
    component.openDetails(mockDocument, 'settlements');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const callout = compiled.querySelector('.settlement-info-callout');
    expect(callout).toBeTruthy();
    expect(callout?.textContent).toContain('Financial Semantics Guarantee');
    expect(callout?.textContent).toContain('neither spending nor income');
  });

  // 18. Status badge classes
  it('18. returns appropriate badge CSS classes for document status', () => {
    expect(component.getStatusBadgeClass('COMPLETED')).toBe('badge-success');
    expect(component.getStatusBadgeClass('PARTIALLY_PROCESSED')).toBe('badge-warning');
    expect(component.getStatusBadgeClass('FAILED')).toBe('badge-danger');
    expect(component.getStatusBadgeClass('PARSING')).toBe('badge-info');
    expect(component.getStatusBadgeClass('UPLOADED')).toBe('badge-info');
  });

  // 19. File size formatter
  it('19. formats file size correctly in B, KB, MB', () => {
    expect(component.formatFileSize(0)).toBe('0 B');
    expect(component.formatFileSize(1024)).toBe('1 KB');
    expect(component.formatFileSize(1048576)).toBe('1 MB');
  });

  // 20. Deep link navigation to transactions view
  it('20. navigates to transactions with sourceDocumentId query parameter', () => {
    component.navigateToTransactions('doc-abc-123');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/app/transactions'], {
      queryParams: { sourceDocumentId: 'doc-abc-123' },
    });
  });

  // 21. Deep link navigation with needsReview flag
  it('21. navigates to transactions with needsReview=true filter', () => {
    component.navigateToTransactions('doc-abc-123', true);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/app/transactions'], {
      queryParams: { sourceDocumentId: 'doc-abc-123', needsReview: 'true' },
    });
  });

  // 22. Close details modal
  it('22. closes details modal and resets active document state', () => {
    component.openDetails(mockDocument);
    expect(component.activeDocument).toBeTruthy();

    component.closeDetails();
    expect(component.activeDocument).toBeNull();
    expect(component.activeSettlements.length).toBe(0);
  });
});
