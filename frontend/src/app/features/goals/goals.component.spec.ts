import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Goal } from '../../core/api/api.types';
import { GoalService } from './goal.service';
import { GoalsComponent } from './goals.component';

describe('GoalsComponent', () => {
  let component: GoalsComponent;
  let fixture: ComponentFixture<GoalsComponent>;
  let goalServiceSpy: jasmine.SpyObj<GoalService>;

  const mockGoal: Goal = {
    id: 1,
    userId: 1,
    name: 'Emergency Fund',
    targetAmount: 50000,
    currentAmount: 25000,
    targetDate: '2026-12-31',
    progressPercentage: 50,
    remainingAmount: 25000,
    isCompleted: false,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  };

  beforeEach(async () => {
    goalServiceSpy = jasmine.createSpyObj('GoalService', [
      'list',
      'create',
      'update',
      'delete',
      'addContribution',
    ], {
      onGoalMutated$: of(),
    });
    goalServiceSpy.list.and.returnValue(
      of({ data: [mockGoal], error: null, meta: null })
    );

    await TestBed.configureTestingModule({
      imports: [GoalsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GoalService, useValue: goalServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GoalsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads goals and calculates KPI metrics', () => {
    expect(goalServiceSpy.list).toHaveBeenCalled();
    expect(component.goals.length).toBe(1);
    expect(component.totalTarget).toBe(50000);
    expect(component.totalSaved).toBe(25000);
    expect(component.overallProgress).toBe(50);
    expect(component.activeCount).toBe(1);
    expect(component.completedCount).toBe(0);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.goal-name')?.textContent).toContain('Emergency Fund');
    expect(compiled.querySelector('.fig-val')?.textContent).toContain('25,000');
  });

  it('opens contribution modal when add funds clicked', () => {
    component.openContribution(mockGoal);
    expect(component.showContributionModal).toBeTrue();
    expect(component.contributingGoal).toEqual(mockGoal);
  });
});
