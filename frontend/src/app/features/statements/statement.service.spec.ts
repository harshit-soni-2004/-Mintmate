import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { StatementService, StatementUploadOptions } from './statement.service';
import { environment } from '../../../environments/environment';

describe('StatementService', () => {
  let service: StatementService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        StatementService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(StatementService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('uploads and ingests statement with metadata FormData', () => {
    const fakeFile = new File(['header\n1,2,3'], 'sample.csv', { type: 'text/csv' });
    const options: StatementUploadOptions = {
      file: fakeFile,
      accountType: 'CREDIT_CARD',
      institutionName: 'HDFC Bank',
      accountNumberLast4: '4321',
      statementStartDate: '2026-08-01',
      statementEndDate: '2026-08-31',
    };

    service.uploadAndIngest(options).subscribe((res) => {
      expect(res.data).toBeTruthy();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/statements/upload`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();

    const formData = req.request.body as FormData;
    expect(formData.get('accountType')).toBe('CREDIT_CARD');
    expect(formData.get('institutionName')).toBe('HDFC Bank');
    expect(formData.get('accountNumberLast4')).toBe('4321');
    expect(formData.get('statementStartDate')).toBe('2026-08-01');
    expect(formData.get('statementEndDate')).toBe('2026-08-31');

    req.flush({ data: { document: { id: 'doc-123' }, importResult: { importedTransactions: 5 } }, error: null, meta: null });
  });

  it('lists statements for the current user', () => {
    service.list().subscribe((res) => {
      expect(res.data?.length).toBe(1);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/statements`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: [{ id: 'doc-123', originalFilename: 'test.csv' }], error: null, meta: null });
  });

  it('fetches a single statement by id', () => {
    service.get('doc-123').subscribe((res) => {
      expect(res.data?.id).toBe('doc-123');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/statements/doc-123`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: { id: 'doc-123', originalFilename: 'test.csv' }, error: null, meta: null });
  });

  it('re-ingests statement by id', () => {
    service.reingest('doc-123').subscribe((res) => {
      expect(res.data).toBeTruthy();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/statements/doc-123/ingest`);
    expect(req.request.method).toBe('POST');
    req.flush({ data: { document: { id: 'doc-123' }, importResult: { importedTransactions: 3 } }, error: null, meta: null });
  });

  it('fetches settlements for a statement', () => {
    service.listSettlements('doc-123').subscribe((res) => {
      expect(res.data?.length).toBe(1);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/statements/doc-123/settlements`);
    expect(req.request.method).toBe('GET');
    req.flush({
      data: [{ id: 'set-1', amount: 20000, classification: 'PAYMENT', status: 'UNRESOLVED' }],
      error: null,
      meta: null,
    });
  });

  it('deletes a statement by id', () => {
    service.delete('doc-123').subscribe((res) => {
      expect(res.data).toBeNull();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/statements/doc-123`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ data: null, error: null, meta: null });
  });
});
