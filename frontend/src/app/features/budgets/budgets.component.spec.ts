import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Budget, BudgetSummary } from '../../core/api/api.types';
import { BudgetService } from './budget.service';
import { BudgetsComponent } from './budgets.component';

describe('BudgetsComponent', () => {
  let component: BudgetsComponent;
  let fixture: ComponentFixture<BudgetsComponent>;
  let budgetServiceSpy: jasmine.SpyObj<BudgetService>;

  const mockBudget: Budget = {
    id: 1,
    userId: 1,
    category: 'FOOD',
    limitAmount: 10000,
    period: 'MONTHLY',
    startDate: '2026-09-01',
    endDate: '2026-09-30',
    spentAmount: 4500,
    remainingAmount: 5500,
    percentageUsed: 45,
    status: 'ON_TRACK',
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  };

  const mockSummary: BudgetSummary = {
    totalBudget: 10000,
    totalSpent: 4500,
    totalRemaining: 5500,
    percentageUsed: 45,
    onTrackCount: 1,
    warningCount: 0,
    exceededCount: 0,
    totalBudgets: 1,
    budgets: [mockBudget],
  };

  beforeEach(async () => {
    budgetServiceSpy = jasmine.createSpyObj('BudgetService', [
      'getSummary',
      'create',
      'update',
      'delete',
    ], {
      onBudgetMutated$: of(),
    });
    budgetServiceSpy.getSummary.and.returnValue(
      of({ data: mockSummary, error: null, meta: null })
    );

    await TestBed.configureTestingModule({
      imports: [BudgetsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BudgetService, useValue: budgetServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BudgetsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and renders budget summary and budget cards', () => {
    expect(budgetServiceSpy.getSummary).toHaveBeenCalled();
    expect(component.summary).toEqual(mockSummary);
    expect(component.budgets.length).toBe(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.metric-value')?.textContent).toContain('10,000');
    expect(compiled.querySelector('.spent-val')?.textContent).toContain('4,500');
    expect(compiled.querySelector('.cat-pill')?.textContent).toContain('Food & Dining');
  });

  it('opens and pre-fills form on openEdit', () => {
    component.openEdit(mockBudget);
    expect(component.showModal).toBeTrue();
    expect(component.editingBudget).toEqual(mockBudget);
    expect(component.form.controls.category.value).toBe('FOOD');
    expect(component.form.controls.limitAmount.value).toBe(10000);
  });

  it('calculates status labels and styling correctly', () => {
    expect(component.statusLabel('ON_TRACK')).toBe('On Track');
    expect(component.statusLabel('WARNING')).toBe('Warning (75%+)');
    expect(component.statusLabel('EXCEEDED')).toBe('Exceeded');

    expect(component.getStatusClass('ON_TRACK')).toBe('status-on-track');
    expect(component.getStatusClass('WARNING')).toBe('status-warning');
    expect(component.getStatusClass('EXCEEDED')).toBe('status-exceeded');
  });
});
