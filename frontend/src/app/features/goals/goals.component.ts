import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { Goal, GoalContributionRequest, GoalRequest } from '../../core/api/api.types';
import { GoalService } from './goal.service';

@Component({
  selector: 'app-goals',
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './goals.component.html',
  styleUrl: './goals.component.scss',
})
export class GoalsComponent implements OnInit, OnDestroy {
  private readonly goalService = inject(GoalService);
  private readonly fb = inject(FormBuilder);

  private sub = new Subscription();

  goals: Goal[] = [];
  loading = true;
  error = '';
  submitting = false;

  // Modals
  showGoalModal = false;
  editingGoal: Goal | null = null;
  goalFormError = '';

  showContributionModal = false;
  contributingGoal: Goal | null = null;
  contributionFormError = '';

  // KPI Metrics
  totalTarget = 0;
  totalSaved = 0;
  overallProgress = 0;
  completedCount = 0;
  activeCount = 0;

  readonly goalForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    targetAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    currentAmount: [0 as number | null, [Validators.min(0)]],
    targetDate: ['', Validators.required],
  });

  readonly contributionForm = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
  });

  ngOnInit(): void {
    this.loadGoals();

    this.sub.add(
      this.goalService.onGoalMutated$.subscribe(() => {
        this.loadGoals(false);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadGoals(setLoading = true): void {
    if (setLoading) this.loading = true;
    this.error = '';

    this.goalService.list().subscribe({
      next: (res) => {
        this.goals = res.data || [];
        this.computeMetrics();
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load financial goals. Please try again.';
        this.loading = false;
      },
    });
  }

  computeMetrics(): void {
    this.totalTarget = this.goals.reduce((acc, g) => acc + g.targetAmount, 0);
    this.totalSaved = this.goals.reduce((acc, g) => acc + g.currentAmount, 0);
    this.completedCount = this.goals.filter((g) => g.isCompleted).length;
    this.activeCount = this.goals.length - this.completedCount;
    this.overallProgress = this.totalTarget > 0
      ? Math.min(100, Math.round((this.totalSaved / this.totalTarget) * 100))
      : 0;
  }

  openCreate(): void {
    this.editingGoal = null;
    this.goalFormError = '';
    const futureDate = new Date();
    futureDate.setMonth(futureDate.getMonth() + 6);

    this.goalForm.reset({
      name: '',
      targetAmount: null,
      currentAmount: 0,
      targetDate: futureDate.toISOString().slice(0, 10),
    });
    this.showGoalModal = true;
  }

  openEdit(g: Goal): void {
    this.editingGoal = g;
    this.goalFormError = '';
    this.goalForm.patchValue({
      name: g.name,
      targetAmount: g.targetAmount,
      currentAmount: g.currentAmount,
      targetDate: g.targetDate,
    });
    this.showGoalModal = true;
  }

  closeGoalModal(): void {
    this.showGoalModal = false;
    this.editingGoal = null;
    this.goalFormError = '';
  }

  saveGoal(): void {
    if (this.goalForm.invalid) {
      this.goalForm.markAllAsTouched();
      return;
    }

    const val = this.goalForm.getRawValue();
    const payload: GoalRequest = {
      name: val.name.trim(),
      targetAmount: Number(val.targetAmount),
      currentAmount: val.currentAmount != null ? Number(val.currentAmount) : 0,
      targetDate: val.targetDate,
    };

    this.submitting = true;
    this.goalFormError = '';

    const req$ = this.editingGoal
      ? this.goalService.update(this.editingGoal.id, payload)
      : this.goalService.create(payload);

    req$.subscribe({
      next: () => {
        this.submitting = false;
        this.closeGoalModal();
        this.loadGoals(false);
      },
      error: (err) => {
        this.submitting = false;
        if (err?.error?.message) {
          this.goalFormError = err.error.message;
        } else {
          this.goalFormError = 'Failed to save financial goal.';
        }
      },
    });
  }

  deleteGoal(g: Goal): void {
    if (confirm(`Are you sure you want to delete "${g.name}"?`)) {
      this.goalService.delete(g.id).subscribe({
        next: () => this.loadGoals(false),
        error: () => {
          this.error = 'Failed to delete goal.';
        },
      });
    }
  }

  openContribution(g: Goal): void {
    this.contributingGoal = g;
    this.contributionFormError = '';
    this.contributionForm.reset({ amount: null });
    this.showContributionModal = true;
  }

  closeContributionModal(): void {
    this.showContributionModal = false;
    this.contributingGoal = null;
    this.contributionFormError = '';
  }

  submitContribution(): void {
    if (this.contributionForm.invalid || !this.contributingGoal) {
      this.contributionForm.markAllAsTouched();
      return;
    }

    const amount = Number(this.contributionForm.getRawValue().amount);
    const payload: GoalContributionRequest = { amount };

    this.submitting = true;
    this.contributionFormError = '';

    this.goalService.addContribution(this.contributingGoal.id, payload).subscribe({
      next: () => {
        this.submitting = false;
        this.closeContributionModal();
        this.loadGoals(false);
      },
      error: (err) => {
        this.submitting = false;
        if (err?.error?.message) {
          this.contributionFormError = err.error.message;
        } else {
          this.contributionFormError = 'Failed to record contribution.';
        }
      },
    });
  }
}
