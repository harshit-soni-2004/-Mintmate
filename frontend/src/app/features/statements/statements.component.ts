import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import {
  DocumentAccountType,
  DocumentFileType,
  DocumentStatus,
  FinancialDocument,
  SettlementStatus,
  SourceTransactionClassification,
  StatementImportResult,
  StatementSettlement,
  StatementUploadResponse,
} from '../../core/api/api.types';
import { StatementService, StatementUploadOptions } from './statement.service';

const MAX_FILE_SIZE = 15 * 1024 * 1024; // 15 MB
const ALLOWED_EXTENSIONS = ['.pdf', '.csv', '.xlsx', '.xls'];

@Component({
  selector: 'app-statements',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, RouterModule],
  templateUrl: './statements.component.html',
  styleUrl: './statements.component.scss',
})
export class StatementsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly statementService = inject(StatementService);
  private readonly router = inject(Router);

  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  // State
  statements: FinancialDocument[] = [];
  loading = true;
  uploading = false;
  reingestingId: string | null = null;
  deletingId: string | null = null;
  errorMessage = '';
  uploadError = '';
  isDragOver = false;

  // Selected file
  selectedFile: File | null = null;

  // Recent import result
  latestImportResult: StatementImportResult | null = null;
  latestImportDocument: FinancialDocument | null = null;

  // Detail Modal / Drawer
  activeDocument: FinancialDocument | null = null;
  activeSettlements: StatementSettlement[] = [];
  loadingSettlements = false;
  settlementsError = '';
  activeDetailTab: 'summary' | 'settlements' = 'summary';

  // Form
  readonly uploadForm = this.fb.nonNullable.group({
    accountType: ['SAVINGS' as DocumentAccountType, [Validators.required]],
    institutionName: ['', [Validators.maxLength(120)]],
    accountNumberLast4: ['', [Validators.pattern(/^[0-9A-Za-z]{4}$/)]],
    statementStartDate: [''],
    statementEndDate: [''],
  });

  ngOnInit(): void {
    this.loadStatements();
  }

  loadStatements(): void {
    this.loading = true;
    this.errorMessage = '';
    this.statementService.list().subscribe({
      next: (res) => {
        this.statements = res.data ?? [];
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Unable to load statement history. Please try again.';
        this.loading = false;
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.validateAndSetFile(input.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.validateAndSetFile(event.dataTransfer.files[0]);
    }
  }

  validateAndSetFile(file: File): void {
    this.uploadError = '';

    const name = file.name.toLowerCase();
    const hasValidExt = ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext));

    if (!hasValidExt) {
      this.uploadError =
        'Unsupported file type. Please upload a PDF, CSV, or Excel (.xlsx, .xls) statement.';
      this.selectedFile = null;
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      this.uploadError = 'File exceeds the 15 MB maximum size limit.';
      this.selectedFile = null;
      return;
    }

    this.selectedFile = file;
  }

  clearSelectedFile(): void {
    this.selectedFile = null;
    this.uploadError = '';
    if (this.fileInputRef?.nativeElement) {
      this.fileInputRef.nativeElement.value = '';
    }
  }

  submitUpload(): void {
    if (!this.selectedFile) {
      this.uploadError = 'Please choose a statement file to upload.';
      return;
    }

    if (this.uploadForm.invalid) {
      this.uploadError = 'Please check the form inputs for errors.';
      return;
    }

    this.uploading = true;
    this.uploadError = '';

    const val = this.uploadForm.getRawValue();
    const options: StatementUploadOptions = {
      file: this.selectedFile,
      accountType: val.accountType,
      institutionName: val.institutionName.trim() || undefined,
      accountNumberLast4: val.accountNumberLast4.trim() || undefined,
      statementStartDate: val.statementStartDate || undefined,
      statementEndDate: val.statementEndDate || undefined,
    };

    this.statementService.uploadAndIngest(options).subscribe({
      next: (res) => {
        this.uploading = false;
        const uploadResponse = res.data;
        if (uploadResponse) {
          this.latestImportResult = uploadResponse.importResult;
          this.latestImportDocument = uploadResponse.document;
        }
        this.clearSelectedFile();
        this.uploadForm.reset({
          accountType: 'SAVINGS',
          institutionName: '',
          accountNumberLast4: '',
          statementStartDate: '',
          statementEndDate: '',
        });
        this.loadStatements();
      },
      error: (err) => {
        this.uploading = false;
        const apiError = err?.error?.error?.message;
        if (apiError) {
          this.uploadError = apiError;
        } else {
          this.uploadError =
            'Failed to upload and process the statement. Please verify the document format and try again.';
        }
      },
    });
  }

  reingestStatement(doc: FinancialDocument): void {
    this.reingestingId = doc.id;
    this.errorMessage = '';

    this.statementService.reingest(doc.id).subscribe({
      next: (res) => {
        this.reingestingId = null;
        if (res.data) {
          this.latestImportResult = res.data.importResult;
          this.latestImportDocument = res.data.document;
        }
        this.loadStatements();
      },
      error: (err) => {
        this.reingestingId = null;
        const apiError = err?.error?.error?.message;
        this.errorMessage = apiError || `Failed to re-process statement "${doc.originalFilename}".`;
      },
    });
  }

  deleteStatement(doc: FinancialDocument): void {
    const confirmDelete = window.confirm(
      `Are you sure you want to delete "${doc.originalFilename}"? All imported transactions and statement records will also be deleted.`,
    );
    if (!confirmDelete) return;

    this.deletingId = doc.id;
    this.statementService.delete(doc.id).subscribe({
      next: () => {
        this.deletingId = null;
        if (this.activeDocument?.id === doc.id) {
          this.closeDetails();
        }
        if (this.latestImportDocument?.id === doc.id) {
          this.latestImportResult = null;
          this.latestImportDocument = null;
        }
        this.loadStatements();
      },
      error: () => {
        this.deletingId = null;
        this.errorMessage = `Could not delete "${doc.originalFilename}". Please try again.`;
      },
    });
  }

  openDetails(doc: FinancialDocument, tab: 'summary' | 'settlements' = 'summary'): void {
    this.activeDocument = doc;
    this.activeDetailTab = tab;
    this.activeSettlements = [];
    this.settlementsError = '';

    if (tab === 'settlements') {
      this.loadSettlements(doc.id);
    }
  }

  switchDetailTab(tab: 'summary' | 'settlements'): void {
    this.activeDetailTab = tab;
    if (tab === 'settlements' && this.activeDocument && this.activeSettlements.length === 0) {
      this.loadSettlements(this.activeDocument.id);
    }
  }

  loadSettlements(docId: string): void {
    this.loadingSettlements = true;
    this.settlementsError = '';
    this.statementService.listSettlements(docId).subscribe({
      next: (res) => {
        this.activeSettlements = res.data ?? [];
        this.loadingSettlements = false;
      },
      error: () => {
        this.settlementsError = 'Unable to load settlement and transfer records.';
        this.loadingSettlements = false;
      },
    });
  }

  closeDetails(): void {
    this.activeDocument = null;
    this.activeSettlements = [];
  }

  navigateToTransactions(docId: string, needsReviewOnly = false): void {
    this.router.navigate(['/app/transactions'], {
      queryParams: {
        sourceDocumentId: docId,
        ...(needsReviewOnly ? { needsReview: 'true' } : {}),
      },
    });
  }

  // Helpers
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  getStatusBadgeClass(status: DocumentStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'badge-success';
      case 'PARTIALLY_PROCESSED':
        return 'badge-warning';
      case 'FAILED':
        return 'badge-danger';
      case 'PARSING':
      case 'UPLOADED':
        return 'badge-info';
      default:
        return 'badge-neutral';
    }
  }

  getStatusText(status: DocumentStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'Completed';
      case 'PARTIALLY_PROCESSED':
        return 'Partially Processed';
      case 'FAILED':
        return 'Failed';
      case 'PARSING':
        return 'Parsing Statement';
      case 'UPLOADED':
        return 'Uploaded';
      default:
        return status;
    }
  }

  getAccountTypeLabel(type: DocumentAccountType): string {
    switch (type) {
      case 'SAVINGS':
        return 'Savings Account';
      case 'CURRENT':
        return 'Current / Checking';
      case 'CREDIT_CARD':
        return 'Credit Card';
      default:
        return type;
    }
  }

  getSettlementClassificationLabel(classification: SourceTransactionClassification): string {
    switch (classification) {
      case 'PAYMENT':
        return 'Credit Card Payment (Internal settlement)';
      case 'TRANSFER':
        return 'Account Transfer (Internal transfer)';
      case 'REFUND':
        return 'Merchant Refund (Expense offset)';
      case 'REVERSAL':
        return 'Transaction Reversal (Expense offset)';
      default:
        return classification;
    }
  }

  getSettlementStatusLabel(status: SettlementStatus): string {
    switch (status) {
      case 'RESOLVED':
        return 'Settled & Matched';
      case 'UNRESOLVED':
        return 'Unresolved / Pending Match';
      case 'DISCARDED':
        return 'Excluded';
      default:
        return status;
    }
  }
}
