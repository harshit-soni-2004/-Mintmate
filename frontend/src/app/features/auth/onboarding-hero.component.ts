import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, signal } from '@angular/core';

export interface OnboardingSlide {
  id: number;
  badge: string;
  title: string;
  description: string;
  highlightMetric: string;
  highlightLabel: string;
}

@Component({
  selector: 'app-onboarding-hero',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="hero-container"
      (mouseenter)="pause()"
      (mouseleave)="resume()"
      role="region"
      aria-label="SpendSense product highlights"
    >
      <!-- Background Ambient Glows -->
      <div class="glow glow-1" aria-hidden="true"></div>
      <div class="glow glow-2" aria-hidden="true"></div>

      <!-- Brand Header -->
      <div class="brand-badge">
        <span class="brand-dot" aria-hidden="true"></span>
        <span class="brand-name">SpendSense</span>
        <span class="brand-tag">Intelligent Finance</span>
      </div>

      <!-- Dynamic Visual Stage -->
      <div class="stage-wrapper" [attr.data-slide]="currentSlide()">
        @switch (currentSlide()) {
          @case (0) {
            <!-- Slide 1: Balance & Transaction Flow -->
            <div class="stage-art slide-enter" aria-label="Live spending stream preview">
              <div class="glass-card balance-card float-slow">
                <div class="card-header">
                  <span class="card-label">Total Balance</span>
                  <span class="trend-pill trend-up">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/></svg>
                    +18.4%
                  </span>
                </div>
                <div class="balance-figure">$4,850.00</div>
                <div class="cash-flow-chips">
                  <span class="chip chip-income">+$3,400 Income</span>
                  <span class="chip chip-expense">-$1,250 Spent</span>
                </div>
              </div>

              <!-- Floating Transaction Pill 1 -->
              <div class="floating-pill tx-pill-1 float-reverse">
                <div class="pill-icon food-bg">🍔</div>
                <div class="pill-info">
                  <span class="pill-title">Artisan Bakery</span>
                  <span class="pill-cat">Dining & Food</span>
                </div>
                <span class="pill-amount expense">-$14.50</span>
              </div>

              <!-- Floating Transaction Pill 2 -->
              <div class="floating-pill tx-pill-2 float-delayed">
                <div class="pill-icon travel-bg">✈️</div>
                <div class="pill-info">
                  <span class="pill-title">Metro Transport</span>
                  <span class="pill-cat">Commute</span>
                </div>
                <span class="pill-amount expense">-$4.20</span>
              </div>

              <!-- Floating Transaction Pill 3 (Income) -->
              <div class="floating-pill tx-pill-3 float-slow">
                <div class="pill-icon income-bg">💼</div>
                <div class="pill-info">
                  <span class="pill-title">Design Retainer</span>
                  <span class="pill-cat">Direct Deposit</span>
                </div>
                <span class="pill-amount income">+$1,200.00</span>
              </div>
            </div>
          }

          @case (1) {
            <!-- Slide 2: Category Intelligence & Donut Breakdown -->
            <div class="stage-art slide-enter" aria-label="Category spending breakdown preview">
              <div class="glass-card categories-card float-slow">
                <div class="card-header">
                  <span class="card-label">Monthly Breakdown</span>
                  <span class="active-badge">AI Categorized</span>
                </div>

                <div class="donut-display">
                  <svg class="donut-svg" viewBox="0 0 120 120" width="120" height="120">
                    <circle cx="60" cy="60" r="46" fill="transparent" stroke="#e8efe9" stroke-width="14" />
                    <!-- Food segment: 38% -->
                    <circle cx="60" cy="60" r="46" fill="transparent" stroke="#e67e3d" stroke-width="14"
                      stroke-dasharray="110 290" stroke-dashoffset="0" class="donut-arc arc-1" />
                    <!-- Shopping segment: 28% -->
                    <circle cx="60" cy="60" r="46" fill="transparent" stroke="#9c59b6" stroke-width="14"
                      stroke-dasharray="81 290" stroke-dashoffset="-110" class="donut-arc arc-2" />
                    <!-- Travel segment: 20% -->
                    <circle cx="60" cy="60" r="46" fill="transparent" stroke="#4a77e5" stroke-width="14"
                      stroke-dasharray="58 290" stroke-dashoffset="-191" class="donut-arc arc-3" />
                    <!-- Bills segment: 14% -->
                    <circle cx="60" cy="60" r="46" fill="transparent" stroke="#2e9f8a" stroke-width="14"
                      stroke-dasharray="41 290" stroke-dashoffset="-249" class="donut-arc arc-4" />
                  </svg>
                  <div class="donut-center-text">
                    <span class="center-total">$1,250</span>
                    <span class="center-sub">Total</span>
                  </div>
                </div>

                <div class="category-bars">
                  <div class="cat-bar-item">
                    <div class="bar-meta">
                      <span class="dot-indicator" style="background: #e67e3d;"></span>
                      <span class="bar-name">Food & Dining</span>
                      <span class="bar-val">$475 (38%)</span>
                    </div>
                    <div class="progress-track"><div class="progress-fill" style="width: 38%; background: #e67e3d;"></div></div>
                  </div>
                  <div class="cat-bar-item">
                    <div class="bar-meta">
                      <span class="dot-indicator" style="background: #9c59b6;"></span>
                      <span class="bar-name">Shopping</span>
                      <span class="bar-val">$350 (28%)</span>
                    </div>
                    <div class="progress-track"><div class="progress-fill" style="width: 28%; background: #9c59b6;"></div></div>
                  </div>
                  <div class="cat-bar-item">
                    <div class="bar-meta">
                      <span class="dot-indicator" style="background: #4a77e5;"></span>
                      <span class="bar-name">Travel</span>
                      <span class="bar-val">$250 (20%)</span>
                    </div>
                    <div class="progress-track"><div class="progress-fill" style="width: 20%; background: #4a77e5;"></div></div>
                  </div>
                </div>
              </div>

              <div class="floating-pill insight-pill float-reverse">
                <span class="pill-sparkle">✨</span>
                <span>Dining is <strong>8% lower</strong> than last week</span>
              </div>
            </div>
          }

          @case (2) {
            <!-- Slide 3: Budgets & Trend Velocity -->
            <div class="stage-art slide-enter" aria-label="Budget pacing and spending trends preview">
              <div class="glass-card trend-card float-slow">
                <div class="card-header">
                  <span class="card-label">Monthly Pacing</span>
                  <span class="status-pill status-safe">On Track</span>
                </div>

                <div class="budget-meter">
                  <div class="meter-info">
                    <span class="meter-spent">$1,250 spent</span>
                    <span class="meter-cap">$2,400 budget</span>
                  </div>
                  <div class="meter-bar">
                    <div class="meter-fill" style="width: 52%;"></div>
                  </div>
                  <div class="meter-sub">52% consumed • 16 days remaining</div>
                </div>

                <!-- Animated SVG Trend Wave -->
                <div class="wave-chart">
                  <svg viewBox="0 0 240 60" class="wave-svg" preserveAspectRatio="none">
                    <defs>
                      <linearGradient id="waveGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="#127457" stop-opacity="0.3" />
                        <stop offset="100%" stop-color="#127457" stop-opacity="0.0" />
                      </linearGradient>
                    </defs>
                    <path d="M0,45 Q40,50 70,30 T140,25 T200,15 L240,10 L240,60 L0,60 Z" fill="url(#waveGrad)" />
                    <path d="M0,45 Q40,50 70,30 T140,25 T200,15 L240,10" fill="none" stroke="#127457" stroke-width="3" stroke-linecap="round" />
                    <!-- Data points -->
                    <circle cx="70" cy="30" r="4" fill="#ffffff" stroke="#127457" stroke-width="2.5" />
                    <circle cx="140" cy="25" r="4" fill="#ffffff" stroke="#127457" stroke-width="2.5" />
                    <circle cx="200" cy="15" r="5" fill="#127457" stroke="#ffffff" stroke-width="2" />
                  </svg>
                </div>
              </div>

              <!-- Activity Heatmap Mini Card -->
              <div class="floating-pill activity-pill float-reverse">
                <span class="activity-label">Active Days</span>
                <div class="activity-dots">
                  <span class="dot d-low"></span>
                  <span class="dot d-med"></span>
                  <span class="dot d-high"></span>
                  <span class="dot d-med"></span>
                  <span class="dot d-high"></span>
                  <span class="dot d-none"></span>
                  <span class="dot d-high"></span>
                </div>
              </div>
            </div>
          }

          @case (3) {
            <!-- Slide 4: Habits, Savings & Financial Progress -->
            <div class="stage-art slide-enter" aria-label="Savings rate and financial habit scoring preview">
              <div class="glass-card habit-card float-slow">
                <div class="card-header">
                  <span class="card-label">Financial Clarity Score</span>
                  <span class="score-badge">94 / 100</span>
                </div>

                <div class="health-gauge">
                  <div class="gauge-ring">
                    <span class="gauge-number">34.2%</span>
                    <span class="gauge-caption">Savings Rate</span>
                  </div>
                </div>

                <div class="habit-milestones">
                  <div class="milestone-item">
                    <span class="milestone-icon">🎯</span>
                    <div class="milestone-text">
                      <div class="milestone-title">Emergency Fund</div>
                      <div class="milestone-bar"><div class="bar-fill" style="width: 82%;"></div></div>
                    </div>
                    <span class="milestone-val">82%</span>
                  </div>
                  <div class="milestone-item">
                    <span class="milestone-icon">🛡️</span>
                    <div class="milestone-text">
                      <div class="milestone-title">Fixed Bills Covered</div>
                      <div class="milestone-bar"><div class="bar-fill" style="width: 100%;"></div></div>
                    </div>
                    <span class="milestone-val">100%</span>
                  </div>
                </div>
              </div>

              <!-- Growth Badge -->
              <div class="floating-pill habit-pill float-reverse">
                <span class="growth-icon">📈</span>
                <span><strong>+$620.00</strong> added to reserves this month</span>
              </div>
            </div>
          }
        }
      </div>

      <!-- Slide Text Content -->
      <div class="narrative-content">
        <span class="narrative-badge">{{ slides[currentSlide()].badge }}</span>
        <h2 class="narrative-title">{{ slides[currentSlide()].title }}</h2>
        <p class="narrative-desc">{{ slides[currentSlide()].description }}</p>
      </div>

      <!-- Navigation Footer -->
      <div class="hero-footer">
        <div class="step-indicators" role="tablist" aria-label="Onboarding slide indicators">
          @for (slide of slides; track slide.id; let idx = $index) {
            <button
              type="button"
              class="indicator-dot"
              [class.active]="idx === currentSlide()"
              (click)="goToSlide(idx)"
              [attr.aria-label]="'Go to slide ' + (idx + 1) + ': ' + slide.title"
              [attr.aria-selected]="idx === currentSlide()"
              role="tab"
            ></button>
          }
        </div>

        <div class="nav-arrows">
          <button
            type="button"
            class="arrow-btn"
            (click)="prevSlide()"
            aria-label="Previous slide"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <button
            type="button"
            class="arrow-btn"
            (click)="nextSlide()"
            aria-label="Next slide"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }

    .hero-container {
      position: relative;
      height: 100%;
      min-height: 580px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 2.5rem 2.5rem 2.2rem;
      border-radius: var(--radius-xl);
      background: linear-gradient(155deg, #0d382b 0%, #09261d 55%, #051610 100%);
      color: #ffffff;
      overflow: hidden;
      box-shadow: var(--shadow-lg);
    }

    /* Ambient dynamic glow spots */
    .glow {
      position: absolute;
      border-radius: 50%;
      pointer-events: none;
      filter: blur(80px);
      z-index: 1;
    }
    .glow-1 {
      width: 320px;
      height: 320px;
      top: -80px;
      right: -60px;
      background: rgba(30, 185, 128, 0.22);
    }
    .glow-2 {
      width: 280px;
      height: 280px;
      bottom: 20px;
      left: -40px;
      background: rgba(20, 110, 80, 0.28);
    }

    /* Brand Header */
    .brand-badge {
      position: relative;
      z-index: 2;
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      align-self: flex-start;
      background: rgba(255, 255, 255, 0.08);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.14);
      padding: 0.4rem 0.85rem;
      border-radius: var(--radius-full);
    }
    .brand-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #2dd4bf;
      box-shadow: 0 0 10px #2dd4bf;
    }
    .brand-name {
      font-weight: 700;
      font-size: 0.95rem;
      letter-spacing: -0.02em;
    }
    .brand-tag {
      font-size: 0.72rem;
      color: rgba(255, 255, 255, 0.6);
      border-left: 1px solid rgba(255, 255, 255, 0.2);
      padding-left: 0.55rem;
      font-weight: 500;
    }

    /* Visual Stage */
    .stage-wrapper {
      position: relative;
      z-index: 2;
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 280px;
      margin: 1.5rem 0;
    }
    .stage-art {
      position: relative;
      width: 100%;
      max-width: 380px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }

    /* Glass Cards */
    .glass-card {
      width: 100%;
      background: rgba(255, 255, 255, 0.09);
      backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.16);
      border-radius: var(--radius-lg);
      padding: 1.4rem;
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.35);
      position: relative;
      z-index: 2;
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
    }
    .card-label {
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: rgba(255, 255, 255, 0.7);
      font-weight: 600;
    }

    /* Slide 1 Styles */
    .balance-figure {
      font-size: 2.2rem;
      font-weight: 800;
      letter-spacing: -0.04em;
      color: #ffffff;
      margin-bottom: 0.9rem;
    }
    .trend-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.25rem 0.55rem;
      border-radius: var(--radius-full);
    }
    .trend-up {
      background: rgba(45, 212, 191, 0.2);
      color: #34d399;
      border: 1px solid rgba(52, 211, 153, 0.3);
    }
    .cash-flow-chips {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .chip {
      font-size: 0.75rem;
      font-weight: 600;
      padding: 0.25rem 0.6rem;
      border-radius: var(--radius-sm);
    }
    .chip-income {
      background: rgba(16, 185, 129, 0.2);
      color: #6ee7b7;
      border: 1px solid rgba(16, 185, 129, 0.25);
    }
    .chip-expense {
      background: rgba(239, 68, 68, 0.2);
      color: #fca5a5;
      border: 1px solid rgba(239, 68, 68, 0.25);
    }

    /* Floating Pills */
    .floating-pill {
      position: absolute;
      z-index: 3;
      background: rgba(18, 38, 29, 0.85);
      backdrop-filter: blur(14px);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: var(--radius-md);
      padding: 0.55rem 0.85rem;
      box-shadow: 0 12px 28px rgba(0, 0, 0, 0.4);
      display: flex;
      align-items: center;
      gap: 0.6rem;
      font-size: 0.8rem;
      white-space: nowrap;
    }
    .pill-icon {
      width: 28px;
      height: 28px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.9rem;
    }
    .food-bg { background: rgba(230, 126, 61, 0.25); }
    .travel-bg { background: rgba(74, 119, 229, 0.25); }
    .income-bg { background: rgba(18, 130, 84, 0.3); }

    .pill-info {
      display: flex;
      flex-direction: column;
    }
    .pill-title {
      font-weight: 600;
      color: #ffffff;
      line-height: 1.2;
    }
    .pill-cat {
      font-size: 0.68rem;
      color: rgba(255, 255, 255, 0.6);
    }
    .pill-amount {
      font-weight: 700;
      font-size: 0.82rem;
      margin-left: auto;
    }
    .pill-amount.expense { color: #fca5a5; }
    .pill-amount.income { color: #6ee7b7; }

    .tx-pill-1 {
      top: -18px;
      right: -20px;
    }
    .tx-pill-2 {
      bottom: -15px;
      left: -15px;
    }
    .tx-pill-3 {
      bottom: 45px;
      right: -25px;
    }

    /* Slide 2 Donut & Category Styles */
    .active-badge {
      font-size: 0.7rem;
      color: #2dd4bf;
      background: rgba(45, 212, 191, 0.15);
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-full);
      border: 1px solid rgba(45, 212, 191, 0.25);
      font-weight: 600;
    }
    .donut-display {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0.6rem 0 1rem;
    }
    .donut-svg {
      transform: rotate(-90deg);
    }
    .donut-arc {
      transition: stroke-dasharray 0.8s ease;
    }
    .donut-center-text {
      position: absolute;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      pointer-events: none;
    }
    .center-total {
      font-size: 1.15rem;
      font-weight: 700;
      color: #ffffff;
    }
    .center-sub {
      font-size: 0.68rem;
      color: rgba(255, 255, 255, 0.6);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .category-bars {
      display: flex;
      flex-direction: column;
      gap: 0.55rem;
    }
    .cat-bar-item {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .bar-meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.85);
    }
    .dot-indicator {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      display: inline-block;
      margin-right: 0.4rem;
    }
    .bar-name {
      margin-right: auto;
      font-weight: 500;
    }
    .bar-val {
      font-weight: 600;
    }
    .progress-track {
      height: 5px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 3px;
      overflow: hidden;
    }
    .progress-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.7s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .insight-pill {
      bottom: -18px;
      left: 10px;
    }
    .pill-sparkle {
      font-size: 1rem;
    }

    /* Slide 3 Pacing & Wave Styles */
    .status-pill {
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.2rem 0.55rem;
      border-radius: var(--radius-full);
    }
    .status-safe {
      background: rgba(16, 185, 129, 0.2);
      color: #34d399;
      border: 1px solid rgba(52, 211, 153, 0.3);
    }
    .budget-meter {
      margin-bottom: 0.8rem;
    }
    .meter-info {
      display: flex;
      justify-content: space-between;
      font-size: 0.82rem;
      font-weight: 600;
      margin-bottom: 0.35rem;
    }
    .meter-cap {
      color: rgba(255, 255, 255, 0.65);
    }
    .meter-bar {
      height: 8px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: var(--radius-full);
      overflow: hidden;
      margin-bottom: 0.35rem;
    }
    .meter-fill {
      height: 100%;
      border-radius: var(--radius-full);
      background: linear-gradient(90deg, #10b981 0%, #2dd4bf 100%);
    }
    .meter-sub {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.6);
    }
    .wave-chart {
      margin-top: 0.5rem;
      border-radius: var(--radius-sm);
      overflow: hidden;
      height: 65px;
    }
    .wave-svg {
      width: 100%;
      height: 100%;
    }
    .activity-pill {
      bottom: -15px;
      right: 0px;
      gap: 0.7rem;
    }
    .activity-label {
      font-size: 0.72rem;
      color: rgba(255, 255, 255, 0.75);
      font-weight: 600;
    }
    .activity-dots {
      display: flex;
      gap: 0.25rem;
      align-items: center;
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 2px;
    }
    .d-none { background: rgba(255, 255, 255, 0.15); }
    .d-low { background: #34d399; opacity: 0.4; }
    .d-med { background: #34d399; opacity: 0.7; }
    .d-high { background: #34d399; }

    /* Slide 4 Habits & Savings Progress */
    .score-badge {
      font-size: 0.75rem;
      font-weight: 700;
      color: #2dd4bf;
      background: rgba(45, 212, 191, 0.18);
      padding: 0.25rem 0.6rem;
      border-radius: var(--radius-full);
      border: 1px solid rgba(45, 212, 191, 0.3);
    }
    .health-gauge {
      display: flex;
      justify-content: center;
      margin: 0.4rem 0 1rem;
    }
    .gauge-ring {
      width: 110px;
      height: 110px;
      border-radius: 50%;
      border: 8px solid rgba(255, 255, 255, 0.1);
      border-top-color: #2dd4bf;
      border-right-color: #34d399;
      border-bottom-color: #10b981;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }
    .gauge-number {
      font-size: 1.4rem;
      font-weight: 800;
      color: #ffffff;
    }
    .gauge-caption {
      font-size: 0.68rem;
      color: rgba(255, 255, 255, 0.65);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .habit-milestones {
      display: flex;
      flex-direction: column;
      gap: 0.55rem;
    }
    .milestone-item {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      font-size: 0.75rem;
    }
    .milestone-icon {
      font-size: 1rem;
    }
    .milestone-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .milestone-title {
      font-weight: 600;
      color: rgba(255, 255, 255, 0.9);
    }
    .milestone-bar {
      height: 4px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 2px;
      overflow: hidden;
    }
    .bar-fill {
      height: 100%;
      background: #2dd4bf;
      border-radius: 2px;
    }
    .milestone-val {
      font-weight: 700;
      color: #2dd4bf;
    }
    .habit-pill {
      bottom: -15px;
      left: 10px;
    }
    .growth-icon {
      font-size: 0.95rem;
    }

    /* Narrative Content Section */
    .narrative-content {
      position: relative;
      z-index: 2;
      margin-top: 1rem;
    }
    .narrative-badge {
      display: inline-block;
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: #2dd4bf;
      margin-bottom: 0.4rem;
    }
    .narrative-title {
      font-size: clamp(1.35rem, 2vw, 1.65rem);
      font-weight: 700;
      letter-spacing: -0.025em;
      line-height: 1.25;
      color: #ffffff;
      margin-bottom: 0.45rem;
    }
    .narrative-desc {
      font-size: 0.88rem;
      line-height: 1.5;
      color: rgba(255, 255, 255, 0.75);
      margin: 0;
      max-width: 420px;
    }

    /* Hero Footer & Controls */
    .hero-footer {
      position: relative;
      z-index: 2;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 1.5rem;
      padding-top: 1rem;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
    }
    .step-indicators {
      display: flex;
      gap: 0.45rem;
      align-items: center;
    }
    .indicator-dot {
      width: 24px;
      height: 5px;
      border-radius: 3px;
      background: rgba(255, 255, 255, 0.25);
      border: 0;
      padding: 0;
      cursor: pointer;
      transition: all 0.25s ease;
    }
    .indicator-dot.active {
      width: 44px;
      background: #2dd4bf;
    }
    .indicator-dot:focus-visible {
      outline: 2px solid #2dd4bf;
      outline-offset: 2px;
    }
    .nav-arrows {
      display: flex;
      gap: 0.4rem;
    }
    .arrow-btn {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .arrow-btn:hover {
      background: rgba(255, 255, 255, 0.2);
      transform: scale(1.05);
    }
    .arrow-btn:active {
      transform: scale(0.95);
    }

    /* Smooth Micro Animations */
    @keyframes slideEnter {
      from {
        opacity: 0;
        transform: translateY(8px) scale(0.98);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }
    .slide-enter {
      animation: slideEnter 0.35s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }

    @keyframes floatY {
      0%, 100% { transform: translateY(0px); }
      50% { transform: translateY(-6px); }
    }
    @keyframes floatYReverse {
      0%, 100% { transform: translateY(0px); }
      50% { transform: translateY(6px); }
    }

    .float-slow {
      animation: floatY 5s ease-in-out infinite;
    }
    .float-reverse {
      animation: floatYReverse 4.5s ease-in-out infinite;
    }
    .float-delayed {
      animation: floatY 5.5s ease-in-out 1s infinite;
    }

    /* Responsive Constraints */
    @media (max-width: 900px) {
      .hero-container {
        min-height: auto;
        padding: 1.75rem 1.5rem 1.5rem;
        border-radius: var(--radius-lg);
      }
      .stage-wrapper {
        min-height: 220px;
        margin: 1rem 0;
      }
      .balance-figure {
        font-size: 1.8rem;
      }
      .tx-pill-1, .tx-pill-2, .tx-pill-3, .insight-pill, .activity-pill, .habit-pill {
        position: relative;
        top: auto !important;
        bottom: auto !important;
        left: auto !important;
        right: auto !important;
        margin-top: 0.6rem;
        width: 100%;
        max-width: 340px;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .slide-enter,
      .float-slow,
      .float-reverse,
      .float-delayed {
        animation: none !important;
      }
    }
  `],
})
export class OnboardingHeroComponent implements OnInit, OnDestroy {
  currentSlide = signal(0);
  isPaused = false;
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly slides: OnboardingSlide[] = [
    {
      id: 1,
      badge: 'Real-time Visibility',
      title: 'Know where your money goes.',
      description: 'Bring every card, bank, and transaction together into one crystal-clear real-time stream.',
      highlightMetric: '$4,850.00',
      highlightLabel: 'Total Balance',
    },
    {
      id: 2,
      badge: 'Smart Categorization',
      title: 'Turn transactions into insight.',
      description: 'Instantly understand spending patterns across food, shopping, travel, and essentials.',
      highlightMetric: '38% Dining',
      highlightLabel: 'Top Category',
    },
    {
      id: 3,
      badge: 'Pacing & Budgets',
      title: 'Stay ahead of your spending.',
      description: 'Track velocity against your targets and maintain full control without budgeting stress.',
      highlightMetric: '52% Spent',
      highlightLabel: 'Pacing on Track',
    },
    {
      id: 4,
      badge: 'Wealth & Habits',
      title: 'Build better financial habits.',
      description: 'Spend with intention. Save with confidence. Grow your net worth with clarity.',
      highlightMetric: '34.2%',
      highlightLabel: 'Savings Rate',
    },
  ];

  ngOnInit(): void {
    this.startAutoAdvance();
  }

  ngOnDestroy(): void {
    this.stopAutoAdvance();
  }

  goToSlide(index: number): void {
    this.currentSlide.set(index);
  }

  nextSlide(): void {
    this.currentSlide.update((prev) => (prev + 1) % this.slides.length);
  }

  prevSlide(): void {
    this.currentSlide.update((prev) => (prev - 1 + this.slides.length) % this.slides.length);
  }

  pause(): void {
    this.isPaused = true;
  }

  resume(): void {
    this.isPaused = false;
  }

  private startAutoAdvance(): void {
    this.timer = setInterval(() => {
      if (!this.isPaused) {
        this.nextSlide();
      }
    }, 6000);
  }

  private stopAutoAdvance(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
