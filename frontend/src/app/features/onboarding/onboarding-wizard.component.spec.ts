import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OnboardingService } from '../../core/api/onboarding.service';
import { OnboardingWizardComponent } from './onboarding-wizard.component';

describe('OnboardingWizardComponent', () => {
  let component: OnboardingWizardComponent;
  let fixture: ComponentFixture<OnboardingWizardComponent>;
  let onboardingServiceSpy: jasmine.SpyObj<OnboardingService>;
  let router: Router;

  beforeEach(async () => {
    onboardingServiceSpy = jasmine.createSpyObj('OnboardingService', [
      'complete',
      'getProfile',
      'checkMissingSalaryAlert',
    ]);
    onboardingServiceSpy.getProfile.and.returnValue(of({ data: null, error: null, meta: null }));
    onboardingServiceSpy.complete.and.returnValue(
      of({
        data: {
          profileId: 'prof-123',
          persona: 'SALARIED',
          onboardingCompleted: true,
          monthlySavingsTarget: 20000,
          monthlyInvestmentTarget: 15000,
          incomeSources: [],
          categoryAllocations: [],
        },
        error: null,
        meta: null,
      })
    );

    await TestBed.configureTestingModule({
      imports: [OnboardingWizardComponent],
      providers: [
        provideRouter([]),
        { provide: OnboardingService, useValue: onboardingServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OnboardingWizardComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
  });

  it('initializes on Step 1 with default Salaried persona', () => {
    expect(component.currentStep).toBe(1);
    expect(component.form.value.persona).toBe('SALARIED');
    expect(component.incomeSourcesArray.length).toBe(1);
  });

  it('selects persona and updates default income type', () => {
    component.selectPersona('FREELANCER');
    expect(component.form.value.persona).toBe('FREELANCER');
    expect(component.incomeSourcesArray.at(0).value.incomeType).toBe('FREELANCE');

    component.selectPersona('BUSINESS_OWNER');
    expect(component.form.value.persona).toBe('BUSINESS_OWNER');
    expect(component.incomeSourcesArray.at(0).value.incomeType).toBe('BUSINESS_REVENUE');
  });

  it('navigates through steps and allows going back', () => {
    expect(component.currentStep).toBe(1);
    component.nextStep();
    expect(component.currentStep).toBe(2);

    // Provide valid primary income to advance past step 2
    component.incomeSourcesArray.at(0).patchValue({
      sourceName: 'My Employer',
      declaredAmount: 80000,
      expectedDayOfMonth: 1,
    });

    component.nextStep();
    expect(component.currentStep).toBe(3);

    component.nextStep();
    expect(component.currentStep).toBe(4);

    component.prevStep();
    expect(component.currentStep).toBe(3);
  });

  it('blocks step advancement from Step 2 if income sources are invalid', () => {
    component.goToStep(2);
    expect(component.currentStep).toBe(2);

    component.incomeSourcesArray.at(0).patchValue({
      sourceName: '',
      declaredAmount: null,
    });

    component.nextStep();
    expect(component.currentStep).toBe(2);
  });

  it('toggles multiple income streams and manages sources array', () => {
    component.goToStep(2);
    expect(component.incomeSourcesArray.length).toBe(1);

    component.toggleMultipleIncome();
    expect(component.form.value.hasMultipleIncome).toBeTrue();
    expect(component.incomeSourcesArray.length).toBe(2);

    component.addIncomeSource(false);
    expect(component.incomeSourcesArray.length).toBe(3);

    component.removeIncomeSource(2);
    expect(component.incomeSourcesArray.length).toBe(2);

    component.toggleMultipleIncome();
    expect(component.form.value.hasMultipleIncome).toBeFalse();
    expect(component.incomeSourcesArray.length).toBe(1);
  });

  it('calculates total declared income across sources', () => {
    component.incomeSourcesArray.at(0).patchValue({ declaredAmount: 50000 });
    expect(component.getTotalDeclaredIncome()).toBe(50000);

    component.addIncomeSource(false);
    component.incomeSourcesArray.at(1).patchValue({ declaredAmount: 25000 });
    expect(component.getTotalDeclaredIncome()).toBe(75000);
  });

  it('auto-fills rough category allocations based on declared income', () => {
    component.incomeSourcesArray.at(0).patchValue({ declaredAmount: 100000 });
    component.autoFillCategorySuggestions();

    const catGroup = component.form.get('categoryAllocations');
    expect(catGroup?.get('FOOD')?.value).toBeGreaterThan(0);
    expect(catGroup?.get('BILLS')?.value).toBeGreaterThan(0);
  });

  it('allows skipping onboarding and routes to dashboard', () => {
    component.skip();
    expect(router.navigate).toHaveBeenCalledWith(['/app/dashboard']);
  });

  it('submits onboarding payload to backend and routes to dashboard on success', () => {
    component.selectPersona('SALARIED');
    component.incomeSourcesArray.at(0).patchValue({
      sourceName: 'Acme Corp',
      incomeType: 'SALARY',
      frequency: 'MONTHLY',
      declaredAmount: 90000,
      expectedDayOfMonth: 1,
      employerOrPayerKeyword: 'ACMECORP',
    });
    component.form.patchValue({
      monthlySavingsTarget: 25000,
      monthlyInvestmentTarget: 20000,
    });

    component.complete();

    expect(onboardingServiceSpy.complete).toHaveBeenCalledWith(
      jasmine.objectContaining({
        persona: 'SALARIED',
        monthlySavingsTarget: 25000,
        monthlyInvestmentTarget: 20000,
        incomeSources: [
          jasmine.objectContaining({
            sourceName: 'Acme Corp',
            declaredAmount: 90000,
            primary: true,
          }),
        ],
      })
    );
    expect(router.navigate).toHaveBeenCalledWith(['/app/dashboard']);
  });

  it('displays error message when completion fails', () => {
    onboardingServiceSpy.complete.and.returnValue(
      throwError(() => ({ error: { message: 'Network error occurred' } }))
    );

    component.incomeSourcesArray.at(0).patchValue({
      sourceName: 'Test Corp',
      declaredAmount: 50000,
    });

    component.complete();
    expect(component.submitting).toBeFalse();
    expect(component.errorMessage).toBe('Network error occurred');
  });
});
