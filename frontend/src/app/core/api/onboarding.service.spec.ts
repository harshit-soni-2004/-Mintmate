import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { OnboardingRequest, OnboardingResponse } from './api.types';
import { OnboardingService } from './onboarding.service';

describe('OnboardingService', () => {
  let service: OnboardingService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        OnboardingService,
      ],
    });

    service = TestBed.inject(OnboardingService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('completes onboarding with full payload', () => {
    const request: OnboardingRequest = {
      persona: 'SALARIED',
      monthlySavingsTarget: 20000,
      monthlyInvestmentTarget: 25000,
      incomeSources: [
        {
          sourceName: 'Acme Corp',
          incomeType: 'SALARY',
          frequency: 'MONTHLY',
          declaredAmount: 85000,
          expectedDayOfMonth: 1,
          employerOrPayerKeyword: 'ACMECORP',
          primary: true,
          variable: false,
        },
      ],
      categoryAllocations: [
        {
          category: 'FOOD',
          estimatedAmount: 15000,
        },
      ],
    };

    const mockResponse: OnboardingResponse = {
      profileId: 'prof-123',
      persona: 'SALARIED',
      onboardingCompleted: true,
      monthlySavingsTarget: 20000,
      monthlyInvestmentTarget: 25000,
      incomeSources: request.incomeSources!,
      categoryAllocations: request.categoryAllocations!,
    };

    service.complete(request).subscribe((res) => {
      expect(res.data).toEqual(mockResponse);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/onboarding/complete`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: mockResponse, error: null, meta: null });
  });

  it('fetches existing profile', () => {
    service.getProfile().subscribe((res) => {
      expect(res.data?.persona).toBe('FREELANCER');
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/onboarding/profile`);
    expect(req.request.method).toBe('GET');
    req.flush({
      data: {
        profileId: 'prof-456',
        persona: 'FREELANCER',
        onboardingCompleted: true,
        monthlySavingsTarget: null,
        monthlyInvestmentTarget: null,
        incomeSources: [],
        categoryAllocations: [],
      },
      error: null,
      meta: null,
    });
  });

  it('checks missing salary alert', () => {
    service.checkMissingSalaryAlert().subscribe((res) => {
      expect(res.data?.hasAlert).toBe('true');
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/onboarding/missing-salary-alert`);
    expect(req.request.method).toBe('GET');
    req.flush({
      data: { hasAlert: 'true', message: 'Expected salary credit not detected' },
      error: null,
      meta: null,
    });
  });
});
