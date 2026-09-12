import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { GoalRequest } from '../../core/api/api.types';
import { GoalService } from './goal.service';

describe('GoalService', () => {
  let service: GoalService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GoalService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('fetches goals list from API', () => {
    service.list().subscribe((res) => {
      expect(res.data?.length).toBe(1);
    });

    const req = http.expectOne(`${environment.apiUrl}/goals`);
    expect(req.request.method).toBe('GET');
    req.flush({
      data: [
        {
          id: 1,
          userId: 1,
          name: 'Vacation',
          targetAmount: 30000,
          currentAmount: 10000,
          targetDate: '2026-12-31',
          progressPercentage: 33.3,
          remainingAmount: 20000,
          isCompleted: false,
        },
      ],
      error: null,
      meta: null,
    });
  });

  it('triggers onGoalMutated$ on addContribution', (done) => {
    service.onGoalMutated$.subscribe(() => {
      expect(true).toBeTrue();
      done();
    });

    service.addContribution(1, { amount: 5000 }).subscribe();

    const req = http.expectOne(`${environment.apiUrl}/goals/1/contribution`);
    expect(req.request.method).toBe('PATCH');
    req.flush({ data: { id: 1, currentAmount: 15000 }, error: null, meta: null });
  });
});
