import { CommonModule, CurrencyPipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
  CategoryAllocationDto,
  IncomeFrequency,
  IncomeSourceDto,
  IncomeType,
  OnboardingRequest,
  TransactionCategory,
  UserPersona,
} from '../../core/api/api.types';
import { OnboardingService } from '../../core/api/onboarding.service';

interface PersonaOption {
  key: UserPersona;
  title: string;
  subtitle: string;
  icon: string;
  defaultIncomeType: IncomeType;
}

@Component({
  selector: 'app-onboarding-wizard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe],
  templateUrl: './onboarding-wizard.component.html',
  styleUrl: './onboarding-wizard.component.scss',
})
export class OnboardingWizardComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly onboardingService = inject(OnboardingService);
  private readonly router = inject(Router);

  currentStep = 1;
  readonly totalSteps = 4;
  submitting = false;
  errorMessage = '';

  readonly personas: PersonaOption[] = [
    {
      key: 'SALARIED',
      title: 'Salaried Professional',
      subtitle: 'Fixed monthly paycheck, employer credits, and recurring deductions',
      icon: '💼',
      defaultIncomeType: 'SALARY',
    },
    {
      key: 'FREELANCER',
      title: 'Freelancer / Contractor',
      subtitle: 'Variable invoices, multiple clients, and irregular payouts',
      icon: '💻',
      defaultIncomeType: 'FREELANCE',
    },
    {
      key: 'BUSINESS_OWNER',
      title: 'Business Owner',
      subtitle: 'Commercial revenues, operational expenses, and seasonal flows',
      icon: '🏢',
      defaultIncomeType: 'BUSINESS_REVENUE',
    },
    {
      key: 'STUDENT',
      title: 'Student / Learner',
      subtitle: 'Stipends, allowances, tuition budgeting, and savings foundations',
      icon: '🎓',
      defaultIncomeType: 'STIPEND',
    },
  ];

  readonly incomeTypes: { key: IncomeType; label: string }[] = [
    { key: 'SALARY', label: 'Salary' },
    { key: 'BUSINESS_REVENUE', label: 'Business Revenue' },
    { key: 'FREELANCE', label: 'Freelance Payout' },
    { key: 'STIPEND', label: 'Stipend / Allowance' },
    { key: 'PENSION', label: 'Pension' },
    { key: 'INVESTMENT_RETURN', label: 'Investment Returns' },
  ];

  readonly incomeFrequencies: { key: IncomeFrequency; label: string }[] = [
    { key: 'MONTHLY', label: 'Monthly' },
    { key: 'BIWEEKLY', label: 'Bi-Weekly' },
    { key: 'IRREGULAR', label: 'Irregular / Milestone' },
  ];

  readonly categories: { key: TransactionCategory; label: string; icon: string; defaultPct: number }[] = [
    { key: 'FOOD', label: 'Food & Dining', icon: '🍽️', defaultPct: 20 },
    { key: 'BILLS', label: 'Bills & Utilities', icon: '⚡', defaultPct: 15 },
    { key: 'TRAVEL', label: 'Travel & Commute', icon: '🚗', defaultPct: 10 },
    { key: 'SHOPPING', label: 'Shopping', icon: '🛍️', defaultPct: 10 },
    { key: 'ENTERTAINMENT', label: 'Entertainment', icon: '🎬', defaultPct: 5 },
    { key: 'HEALTH', label: 'Health & Wellness', icon: '💊', defaultPct: 5 },
    { key: 'EDUCATION', label: 'Education & Growth', icon: '📚', defaultPct: 5 },
    { key: 'PERSONAL', label: 'Personal Care', icon: '✨', defaultPct: 5 },
    { key: 'OTHER', label: 'Other Discretionary', icon: '📦', defaultPct: 5 },
  ];

  // Main Form Definition
  readonly form = this.fb.group({
    persona: ['SALARIED' as UserPersona, [Validators.required]],
    hasMultipleIncome: [false],
    incomeSources: this.fb.array<FormGroup>([]),
    monthlySavingsTarget: [null as number | null, [Validators.min(0)]],
    monthlyInvestmentTarget: [null as number | null, [Validators.min(0)]],
    categoryAllocations: this.fb.group({}),
  });

  get incomeSourcesArray(): FormArray<FormGroup> {
    return this.form.get('incomeSources') as FormArray<FormGroup>;
  }

  get categoryAllocationsGroup(): FormGroup {
    return this.form.get('categoryAllocations') as FormGroup;
  }

  ngOnInit(): void {
    // Initialize with 1 primary income source
    this.addIncomeSource(true);

    // Initialize category allocations
    const catGroup = this.form.get('categoryAllocations') as FormGroup;
    this.categories.forEach((cat) => {
      catGroup.addControl(cat.key, this.fb.control<number | null>(null, [Validators.min(0)]));
    });

    // Check if user already has profile to prefill
    this.onboardingService.getProfile().subscribe({
      next: (res) => {
        if (res.data) {
          const profile = res.data;
          this.form.patchValue({
            persona: profile.persona,
            monthlySavingsTarget: profile.monthlySavingsTarget ?? null,
            monthlyInvestmentTarget: profile.monthlyInvestmentTarget ?? null,
          });

          if (profile.incomeSources && profile.incomeSources.length > 0) {
            this.incomeSourcesArray.clear();
            profile.incomeSources.forEach((src, idx) => {
              this.incomeSourcesArray.push(this.createIncomeSourceGroup(src, idx === 0));
            });
            if (profile.incomeSources.length > 1) {
              this.form.patchValue({ hasMultipleIncome: true });
            }
          }

          if (profile.categoryAllocations && profile.categoryAllocations.length > 0) {
            profile.categoryAllocations.forEach((alloc) => {
              catGroup.get(alloc.category)?.setValue(alloc.estimatedAmount);
            });
          }
        }
      },
      error: () => {},
    });
  }

  selectPersona(persona: UserPersona): void {
    this.form.patchValue({ persona });
    const selected = this.personas.find((p) => p.key === persona);
    if (selected && this.incomeSourcesArray.length > 0) {
      const primaryGroup = this.incomeSourcesArray.at(0);
      primaryGroup.patchValue({ incomeType: selected.defaultIncomeType });
    }
  }

  createIncomeSourceGroup(src?: Partial<IncomeSourceDto>, isPrimary = false): FormGroup {
    const selectedPersona = this.personas.find((p) => p.key === this.form.value.persona);
    return this.fb.group({
      sourceName: [src?.sourceName || (isPrimary ? 'Primary Income' : 'Secondary Income'), [Validators.required, Validators.maxLength(100)]],
      incomeType: [src?.incomeType || selectedPersona?.defaultIncomeType || 'SALARY', [Validators.required]],
      frequency: [src?.frequency || 'MONTHLY', [Validators.required]],
      declaredAmount: [src?.declaredAmount ?? null, [Validators.required, Validators.min(1)]],
      expectedDayOfMonth: [src?.expectedDayOfMonth ?? 1, [Validators.min(1), Validators.max(31)]],
      employerOrPayerKeyword: [src?.employerOrPayerKeyword || '', [Validators.maxLength(100)]],
      primary: [isPrimary],
      variable: [src?.variable ?? false],
    });
  }

  addIncomeSource(isPrimary = false): void {
    this.incomeSourcesArray.push(this.createIncomeSourceGroup(undefined, isPrimary));
  }

  removeIncomeSource(index: number): void {
    if (this.incomeSourcesArray.length > 1) {
      this.incomeSourcesArray.removeAt(index);
    }
  }

  toggleMultipleIncome(): void {
    const current = this.form.value.hasMultipleIncome;
    this.form.patchValue({ hasMultipleIncome: !current });
    if (!current && this.incomeSourcesArray.length === 1) {
      this.addIncomeSource(false);
    } else if (current && this.incomeSourcesArray.length > 1) {
      while (this.incomeSourcesArray.length > 1) {
        this.incomeSourcesArray.removeAt(1);
      }
    }
  }

  getTotalDeclaredIncome(): number {
    return this.incomeSourcesArray.controls.reduce((sum, ctrl) => {
      const amt = Number(ctrl.get('declaredAmount')?.value) || 0;
      return sum + amt;
    }, 0);
  }

  autoFillCategorySuggestions(): void {
    const totalIncome = this.getTotalDeclaredIncome();
    const base = totalIncome > 0 ? totalIncome * 0.5 : 50000;
    const catGroup = this.form.get('categoryAllocations') as FormGroup;
    this.categories.forEach((cat) => {
      const suggested = Math.round((base * (cat.defaultPct / 100)) / 100) * 100;
      catGroup.get(cat.key)?.setValue(suggested);
    });
  }

  goToStep(step: number): void {
    if (step < 1 || step > this.totalSteps) return;
    this.errorMessage = '';

    // If advancing from step 2, validate income sources
    if (this.currentStep === 2 && step > 2) {
      if (this.incomeSourcesArray.invalid) {
        this.incomeSourcesArray.markAllAsTouched();
        return;
      }
    }

    this.currentStep = step;
  }

  nextStep(): void {
    this.goToStep(this.currentStep + 1);
  }

  prevStep(): void {
    this.goToStep(this.currentStep - 1);
  }

  skip(): void {
    void this.router.navigate(['/app/dashboard']);
  }

  complete(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMessage = '';

    const formVal = this.form.getRawValue();

    // Map income sources
    const incomeSources: IncomeSourceDto[] = (formVal.incomeSources || []).map((src: any, idx: number) => ({
      sourceName: src.sourceName || (idx === 0 ? 'Primary Income' : 'Additional Income'),
      incomeType: src.incomeType as IncomeType,
      frequency: src.frequency as IncomeFrequency,
      declaredAmount: Number(src.declaredAmount) || 0,
      expectedDayOfMonth: src.expectedDayOfMonth ? Number(src.expectedDayOfMonth) : null,
      employerOrPayerKeyword: src.employerOrPayerKeyword?.trim() || null,
      primary: idx === 0,
      variable: !!src.variable,
    }));

    // Map category allocations
    const catAllocMap = formVal.categoryAllocations as Record<string, number | null>;
    const categoryAllocations: CategoryAllocationDto[] = [];
    if (catAllocMap) {
      Object.entries(catAllocMap).forEach(([cat, amt]) => {
        if (amt !== null && amt !== undefined && Number(amt) > 0) {
          categoryAllocations.push({
            category: cat as TransactionCategory,
            estimatedAmount: Number(amt),
          });
        }
      });
    }

    const payload: OnboardingRequest = {
      persona: formVal.persona as UserPersona,
      monthlySavingsTarget: formVal.monthlySavingsTarget ? Number(formVal.monthlySavingsTarget) : null,
      monthlyInvestmentTarget: formVal.monthlyInvestmentTarget ? Number(formVal.monthlyInvestmentTarget) : null,
      incomeSources,
      categoryAllocations,
    };

    this.onboardingService.complete(payload).subscribe({
      next: () => {
        this.submitting = false;
        void this.router.navigate(['/app/dashboard']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage =
          err?.error?.message || err?.message || 'Failed to complete onboarding. Please try again.';
      },
    });
  }
}
