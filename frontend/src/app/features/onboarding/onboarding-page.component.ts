import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

export interface OnboardingStep {
  step: number;
  tag: string;
  title: string;
  subtitle: string;
  badge: string;
}

@Component({
  selector: 'app-onboarding-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="onboarding-viewport">
      <!-- Top Navigation Bar -->
      <header class="top-nav">
        <div class="nav-brand">
          <span class="brand-badge"></span>
          <span class="brand-text">SpendSense</span>
        </div>
        <div class="nav-actions">
          <span class="step-counter">Step {{ currentStep() + 1 }} of {{ steps.length }}</span>
          <a routerLink="/register" class="skip-btn">Skip tour</a>
        </div>
      </header>

      <!-- Main Onboarding Experience Container -->
      <main class="onboarding-main">
        <div class="onboarding-card">
          <!-- Step Progress Track -->
          <div class="stepper-track" role="progressbar" [attr.aria-valuenow]="currentStep() + 1" aria-valuemin="1" [attr.aria-valuemax]="steps.length">
            @for (step of steps; track step.step; let idx = $index) {
              <div
                class="step-segment"
                [class.completed]="idx < currentStep()"
                [class.current]="idx === currentStep()"
              ></div>
            }
          </div>

          <!-- Interactive Interactive Stage Art -->
          <div class="visual-canvas" [attr.data-step]="currentStep()">
            @switch (currentStep()) {
              @case (0) {
                <!-- Step 1: Real-time Cashflow & Transaction Stream -->
                <div class="scene scene-1 animate-fade-up">
                  <div class="fin-card primary-card float-gentle">
                    <div class="card-meta">
                      <span class="meta-title">Monthly Cashflow</span>
                      <span class="badge badge-success">+24.8% vs last month</span>
                    </div>
                    <div class="primary-amount">$5,420.00</div>
                    <div class="mini-metrics">
                      <div class="metric-block">
                        <span class="m-label">Inflow</span>
                        <span class="m-val text-income">+$4,200</span>
                      </div>
                      <div class="metric-divider"></div>
                      <div class="metric-block">
                        <span class="m-label">Outflow</span>
                        <span class="m-val text-expense">-$1,380</span>
                      </div>
                    </div>
                  </div>

                  <!-- Flowing Live Transaction Chips -->
                  <div class="stream-pill tx-1 float-reverse">
                    <span class="pill-emoji">🛒</span>
                    <div class="pill-body">
                      <div class="pill-heading">Whole Foods Market</div>
                      <div class="pill-sub">Groceries & Essentials</div>
                    </div>
                    <span class="pill-amt text-expense">-$86.40</span>
                  </div>

                  <div class="stream-pill tx-2 float-gentle">
                    <span class="pill-emoji">☕</span>
                    <div class="pill-body">
                      <div class="pill-heading">Blue Bottle Coffee</div>
                      <div class="pill-sub">Dining Out</div>
                    </div>
                    <span class="pill-amt text-expense">-$5.75</span>
                  </div>

                  <div class="stream-pill tx-3 float-delayed">
                    <span class="pill-emoji">⚡</span>
                    <div class="pill-body">
                      <div class="pill-heading">Cloud Hosting Payroll</div>
                      <div class="pill-sub">Direct Deposit</div>
                    </div>
                    <span class="pill-amt text-income">+$2,100.00</span>
                  </div>
                </div>
              }

              @case (1) {
                <!-- Step 2: Instant Category Intelligence -->
                <div class="scene scene-2 animate-fade-up">
                  <div class="fin-card category-card float-gentle">
                    <div class="card-meta">
                      <span class="meta-title">Spending Categories</span>
                      <span class="badge badge-accent">Auto-Categorized</span>
                    </div>

                    <div class="cat-donut-wrapper">
                      <svg class="donut-chart" viewBox="0 0 100 100" width="110" height="110">
                        <circle cx="50" cy="50" r="38" fill="transparent" stroke="#f0f5f2" stroke-width="12" />
                        <circle cx="50" cy="50" r="38" fill="transparent" stroke="#e67e3d" stroke-width="12"
                          stroke-dasharray="90 238" stroke-dashoffset="0" />
                        <circle cx="50" cy="50" r="38" fill="transparent" stroke="#4a77e5" stroke-width="12"
                          stroke-dasharray="64 238" stroke-dashoffset="-90" />
                        <circle cx="50" cy="50" r="38" fill="transparent" stroke="#9c59b6" stroke-width="12"
                          stroke-dasharray="48 238" stroke-dashoffset="-154" />
                        <circle cx="50" cy="50" r="38" fill="transparent" stroke="#2e9f8a" stroke-width="12"
                          stroke-dasharray="36 238" stroke-dashoffset="-202" />
                      </svg>
                      <div class="donut-center-lbl">
                        <span class="lbl-amt">$1,380</span>
                        <span class="lbl-cap">Total</span>
                      </div>
                    </div>

                    <div class="cat-list">
                      <div class="cat-row">
                        <span class="cat-dot" style="background:#e67e3d"></span>
                        <span class="cat-title">Food & Dining</span>
                        <span class="cat-ratio">38%</span>
                        <span class="cat-sum">$524.40</span>
                      </div>
                      <div class="cat-row">
                        <span class="cat-dot" style="background:#4a77e5"></span>
                        <span class="cat-title">Travel & Commute</span>
                        <span class="cat-ratio">27%</span>
                        <span class="cat-sum">$372.60</span>
                      </div>
                      <div class="cat-row">
                        <span class="cat-dot" style="background:#9c59b6"></span>
                        <span class="cat-title">Shopping & Retail</span>
                        <span class="cat-ratio">20%</span>
                        <span class="cat-sum">$276.00</span>
                      </div>
                    </div>
                  </div>

                  <div class="stream-pill insight-bubble float-reverse">
                    <span class="insight-star">💡</span>
                    <span>No uncategorized transactions this cycle</span>
                  </div>
                </div>
              }

              @case (2) {
                <!-- Step 3: Pacing, Trends & Activity Calendar -->
                <div class="scene scene-3 animate-fade-up">
                  <div class="fin-card pacing-card float-gentle">
                    <div class="card-meta">
                      <span class="meta-title">Monthly Pacing</span>
                      <span class="badge badge-success">Under Budget</span>
                    </div>

                    <div class="pacing-status">
                      <div class="pacing-hdr">
                        <span class="p-spent">$1,380 consumed</span>
                        <span class="p-lim">of $2,800 limit</span>
                      </div>
                      <div class="pacing-track">
                        <div class="pacing-bar" style="width: 49.2%;"></div>
                      </div>
                      <span class="p-note">49.2% spent • 14 days remaining in month</span>
                    </div>

                    <!-- Mini Sparkline Area -->
                    <div class="sparkline-box">
                      <svg viewBox="0 0 200 45" class="spark-svg" preserveAspectRatio="none">
                        <path d="M0,35 Q30,38 60,25 T120,20 T170,10 L200,8 L200,45 L0,45 Z" fill="rgba(18, 116, 87, 0.12)" />
                        <path d="M0,35 Q30,38 60,25 T120,20 T170,10 L200,8" fill="none" stroke="#127457" stroke-width="2.5" />
                        <circle cx="170" cy="10" r="3.5" fill="#127457" />
                      </svg>
                    </div>

                    <!-- Mini Calendar Dots -->
                    <div class="cal-mini">
                      <span class="c-day d-high"></span>
                      <span class="c-day d-med"></span>
                      <span class="c-day d-high"></span>
                      <span class="c-day d-none"></span>
                      <span class="c-day d-med"></span>
                      <span class="c-day d-low"></span>
                      <span class="c-day d-high"></span>
                    </div>
                  </div>

                  <div class="stream-pill forecast-pill float-reverse">
                    <span class="pill-emoji">🎯</span>
                    <span>Projected to save <strong>$1,420</strong> this month</span>
                  </div>
                </div>
              }

              @case (3) {
                <!-- Step 4: Wealth Habits & Financial Freedom -->
                <div class="scene scene-4 animate-fade-up">
                  <div class="fin-card freedom-card float-gentle">
                    <div class="card-meta">
                      <span class="meta-title">Financial Health</span>
                      <span class="badge badge-gold">Top Tier • 96</span>
                    </div>

                    <div class="score-circle">
                      <span class="score-val">34.2%</span>
                      <span class="score-lbl">Savings Rate</span>
                    </div>

                    <div class="habit-stack">
                      <div class="habit-bar-item">
                        <div class="hb-info">
                          <span>Emergency Runway</span>
                          <span class="hb-perc">6.2 months</span>
                        </div>
                        <div class="hb-track"><div class="hb-fill" style="width: 88%;"></div></div>
                      </div>
                      <div class="habit-bar-item">
                        <div class="hb-info">
                          <span>Discretionary Cap</span>
                          <span class="hb-perc">Within bounds</span>
                        </div>
                        <div class="hb-track"><div class="hb-fill" style="width: 65%;"></div></div>
                      </div>
                    </div>
                  </div>

                  <div class="stream-pill trophy-pill float-reverse">
                    <span class="pill-emoji">🏆</span>
                    <span>Milestone unlocked: <strong>3rd month</strong> of positive cash flow</span>
                  </div>
                </div>
              }
            }
          </div>

          <!-- Story Narrative Text -->
          <div class="story-block">
            <span class="story-tag">{{ steps[currentStep()].tag }}</span>
            <h1 class="story-title">{{ steps[currentStep()].title }}</h1>
            <p class="story-subtitle">{{ steps[currentStep()].subtitle }}</p>
          </div>

          <!-- Bottom Navigation Controls -->
          <div class="onboarding-controls">
            @if (currentStep() > 0) {
              <button type="button" class="btn-step-back" (click)="prev()">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
                <span>Back</span>
              </button>
            } @else {
              <div></div>
            }

            <div class="step-dots" role="tablist">
              @for (step of steps; track step.step; let idx = $index) {
                <button
                  type="button"
                  class="dot-tab"
                  [class.active]="idx === currentStep()"
                  (click)="goTo(idx)"
                  [attr.aria-label]="'Go to step ' + (idx + 1)"
                ></button>
              }
            </div>

            @if (currentStep() < steps.length - 1) {
              <button type="button" class="btn-step-next" (click)="next()">
                <span>Continue</span>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
              </button>
            } @else {
              <a routerLink="/register" class="btn-step-primary">
                <span>Create Free Account</span>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
              </a>
            }
          </div>

          <!-- Direct Sign-in Link -->
          <div class="signin-row">
            <span>Already have an account?</span>
            <a routerLink="/login" class="signin-link">Sign in</a>
          </div>
        </div>
      </main>
    </div>
  `,
  styles: [`
    .onboarding-viewport {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background:
        radial-gradient(ellipse at top, rgba(18, 116, 87, 0.08), transparent 60%),
        var(--canvas);
      padding: 1.25rem;
      box-sizing: border-box;
    }

    /* Top Bar */
    .top-nav {
      max-width: 900px;
      width: 100%;
      margin: 0 auto 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .nav-brand {
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }
    .brand-badge {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--brand);
      box-shadow: 0 0 10px rgba(18, 116, 87, 0.5);
    }
    .brand-text {
      font-weight: 750;
      font-size: 1.15rem;
      letter-spacing: -0.03em;
      color: var(--ink);
    }
    .nav-actions {
      display: flex;
      align-items: center;
      gap: 1.25rem;
    }
    .step-counter {
      font-size: 0.84rem;
      font-weight: 600;
      color: var(--muted);
    }
    .skip-btn {
      font-size: 0.86rem;
      font-weight: 650;
      color: var(--brand);
      text-decoration: none;
    }
    .skip-btn:hover {
      text-decoration: underline;
    }

    /* Onboarding Card Shell */
    .onboarding-main {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
    }
    .onboarding-card {
      width: 100%;
      max-width: 680px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-xl);
      box-shadow: var(--shadow-lg);
      padding: 2.25rem 2.25rem 2rem;
      display: flex;
      flex-direction: column;
      box-sizing: border-box;
      position: relative;
    }

    /* Stepper Track */
    .stepper-track {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1.75rem;
    }
    .step-segment {
      flex: 1;
      height: 4px;
      border-radius: 2px;
      background: var(--surface-subtle);
      transition: all 0.3s ease;
    }
    .step-segment.completed,
    .step-segment.current {
      background: var(--brand);
    }

    /* Visual Canvas */
    .visual-canvas {
      position: relative;
      min-height: 290px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(145deg, #0e3b2e 0%, #08281f 100%);
      border-radius: var(--radius-lg);
      padding: 1.75rem;
      overflow: hidden;
      box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.2);
    }
    .scene {
      position: relative;
      width: 100%;
      max-width: 420px;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    /* Fin Cards */
    .fin-card {
      width: 100%;
      background: rgba(255, 255, 255, 0.12);
      backdrop-filter: blur(14px);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: var(--radius-md);
      padding: 1.25rem 1.4rem;
      color: #ffffff;
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.3);
    }
    .card-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.6rem;
    }
    .meta-title {
      font-size: 0.76rem;
      font-weight: 650;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: rgba(255, 255, 255, 0.7);
    }
    .badge {
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.2rem 0.55rem;
      border-radius: var(--radius-full);
    }
    .badge-success {
      background: rgba(45, 212, 191, 0.2);
      color: #34d399;
      border: 1px solid rgba(52, 211, 153, 0.3);
    }
    .badge-accent {
      background: rgba(45, 212, 191, 0.2);
      color: #2dd4bf;
      border: 1px solid rgba(45, 212, 191, 0.3);
    }
    .badge-gold {
      background: rgba(245, 158, 11, 0.25);
      color: #fbbf24;
      border: 1px solid rgba(245, 158, 11, 0.35);
    }

    .primary-amount {
      font-size: 2.1rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: #ffffff;
      margin-bottom: 0.8rem;
    }
    .mini-metrics {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      padding-top: 0.65rem;
      border-top: 1px solid rgba(255, 255, 255, 0.12);
    }
    .metric-block {
      display: flex;
      flex-direction: column;
    }
    .m-label {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.65);
    }
    .m-val {
      font-size: 0.95rem;
      font-weight: 700;
    }
    .metric-divider {
      width: 1px;
      height: 24px;
      background: rgba(255, 255, 255, 0.15);
    }
    .text-income { color: #6ee7b7; }
    .text-expense { color: #fca5a5; }

    /* Stream Pills */
    .stream-pill {
      position: absolute;
      background: rgba(14, 38, 29, 0.9);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: var(--radius-sm);
      padding: 0.45rem 0.85rem;
      box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
      display: flex;
      align-items: center;
      gap: 0.6rem;
      font-size: 0.78rem;
      color: #ffffff;
      white-space: nowrap;
    }
    .pill-emoji { font-size: 1rem; }
    .pill-body {
      display: flex;
      flex-direction: column;
    }
    .pill-heading { font-weight: 600; line-height: 1.2; }
    .pill-sub { font-size: 0.65rem; color: rgba(255, 255, 255, 0.6); }
    .pill-amt { font-weight: 700; font-size: 0.8rem; margin-left: auto; }

    .tx-1 { top: -14px; right: -15px; }
    .tx-2 { bottom: -12px; left: -10px; }
    .tx-3 { bottom: 40px; right: -20px; }
    .insight-bubble { bottom: -12px; left: 10px; }
    .forecast-pill { bottom: -12px; right: 10px; }
    .trophy-pill { bottom: -12px; left: 10px; }

    /* Scene 2 Donut */
    .cat-donut-wrapper {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0.4rem 0 0.8rem;
    }
    .donut-chart { transform: rotate(-90deg); }
    .donut-center-lbl {
      position: absolute;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .lbl-amt { font-size: 1.05rem; font-weight: 700; color: #ffffff; }
    .lbl-cap { font-size: 0.65rem; color: rgba(255, 255, 255, 0.6); text-transform: uppercase; }

    .cat-list {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .cat-row {
      display: flex;
      align-items: center;
      font-size: 0.74rem;
      gap: 0.5rem;
    }
    .cat-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }
    .cat-title { flex: 1; color: rgba(255, 255, 255, 0.9); }
    .cat-ratio { color: rgba(255, 255, 255, 0.65); font-weight: 600; }
    .cat-sum { font-weight: 700; color: #ffffff; }

    /* Scene 3 Pacing */
    .pacing-hdr {
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
      font-weight: 600;
      margin-bottom: 0.35rem;
    }
    .p-lim { color: rgba(255, 255, 255, 0.65); }
    .pacing-track {
      height: 7px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 4px;
      overflow: hidden;
      margin-bottom: 0.35rem;
    }
    .pacing-bar {
      height: 100%;
      background: linear-gradient(90deg, #10b981 0%, #2dd4bf 100%);
      border-radius: 4px;
    }
    .p-note {
      font-size: 0.68rem;
      color: rgba(255, 255, 255, 0.65);
    }
    .sparkline-box {
      margin-top: 0.6rem;
      height: 45px;
      border-radius: 4px;
      overflow: hidden;
    }
    .spark-svg { width: 100%; height: 100%; }
    .cal-mini {
      display: flex;
      gap: 0.3rem;
      margin-top: 0.5rem;
      justify-content: center;
    }
    .c-day { width: 8px; height: 8px; border-radius: 2px; }
    .d-none { background: rgba(255, 255, 255, 0.15); }
    .d-low { background: #34d399; opacity: 0.4; }
    .d-med { background: #34d399; opacity: 0.7; }
    .d-high { background: #34d399; }

    /* Scene 4 Score */
    .score-circle {
      width: 90px;
      height: 90px;
      margin: 0.2rem auto 0.75rem;
      border-radius: 50%;
      border: 6px solid rgba(255, 255, 255, 0.1);
      border-top-color: #2dd4bf;
      border-right-color: #34d399;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }
    .score-val { font-size: 1.35rem; font-weight: 800; color: #ffffff; }
    .score-lbl { font-size: 0.65rem; color: rgba(255, 255, 255, 0.65); text-transform: uppercase; }
    .habit-stack {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .habit-bar-item {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
      font-size: 0.72rem;
    }
    .hb-info {
      display: flex;
      justify-content: space-between;
      color: rgba(255, 255, 255, 0.85);
    }
    .hb-perc { font-weight: 700; color: #2dd4bf; }
    .hb-track {
      height: 4px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 2px;
      overflow: hidden;
    }
    .hb-fill { height: 100%; background: #2dd4bf; border-radius: 2px; }

    /* Story Block */
    .story-block {
      margin: 1.75rem 0 1.5rem;
      text-align: center;
    }
    .story-tag {
      font-size: 0.74rem;
      font-weight: 750;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--brand);
      display: block;
      margin-bottom: 0.4rem;
    }
    .story-title {
      font-size: clamp(1.45rem, 2.4vw, 1.85rem);
      font-weight: 750;
      letter-spacing: -0.03em;
      line-height: 1.25;
      color: var(--ink);
      margin: 0 0 0.5rem;
    }
    .story-subtitle {
      font-size: 0.94rem;
      line-height: 1.55;
      color: var(--muted);
      margin: 0 auto;
      max-width: 480px;
    }

    /* Controls */
    .onboarding-controls {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 1.25rem;
      border-top: 1px solid var(--line);
    }
    .step-dots {
      display: flex;
      gap: 0.4rem;
    }
    .dot-tab {
      width: 24px;
      height: 6px;
      border-radius: 3px;
      background: var(--surface-subtle);
      border: 0;
      padding: 0;
      cursor: pointer;
      transition: all 0.25s ease;
    }
    .dot-tab.active {
      width: 44px;
      background: var(--brand);
    }
    .dot-tab:focus-visible {
      outline: 2px solid var(--brand);
    }

    .btn-step-back {
      background: transparent;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      padding: 0.65rem 1rem;
      font-size: 0.88rem;
      font-weight: 650;
      color: var(--muted);
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .btn-step-back:hover {
      background: var(--surface-subtle);
      color: var(--ink);
    }

    .btn-step-next,
    .btn-step-primary {
      background: var(--brand);
      color: #ffffff;
      border: 0;
      border-radius: var(--radius-sm);
      padding: 0.68rem 1.2rem;
      font-size: 0.9rem;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(18, 116, 87, 0.24);
      text-decoration: none;
      transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .btn-step-next:hover,
    .btn-step-primary:hover {
      background: var(--brand-hover);
      box-shadow: 0 6px 18px rgba(18, 116, 87, 0.32);
      transform: translateY(-1px);
    }

    .signin-row {
      margin-top: 1.2rem;
      text-align: center;
      font-size: 0.85rem;
      color: var(--muted);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.4rem;
    }
    .signin-link {
      font-weight: 650;
      color: var(--brand);
    }

    /* Floating Animations */
    @keyframes floatGentle {
      0%, 100% { transform: translateY(0px); }
      50% { transform: translateY(-5px); }
    }
    @keyframes floatRev {
      0%, 100% { transform: translateY(0px); }
      50% { transform: translateY(5px); }
    }
    @keyframes fadeUp {
      from { opacity: 0; transform: translateY(10px) scale(0.98); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
    .animate-fade-up {
      animation: fadeUp 0.32s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
    .float-gentle { animation: floatGentle 5s ease-in-out infinite; }
    .float-reverse { animation: floatRev 4.5s ease-in-out infinite; }
    .float-delayed { animation: floatGentle 5.5s ease-in-out 1s infinite; }

    @media (max-width: 600px) {
      .onboarding-card {
        padding: 1.5rem 1.25rem;
      }
      .visual-canvas {
        min-height: 250px;
        padding: 1rem;
      }
      .primary-amount {
        font-size: 1.7rem;
      }
      .tx-1, .tx-2, .tx-3, .insight-bubble, .forecast-pill, .trophy-pill {
        position: static;
        width: 100%;
        margin-top: 0.5rem;
      }
      .onboarding-controls {
        flex-direction: column-reverse;
        gap: 1rem;
      }
      .btn-step-next, .btn-step-primary, .btn-step-back {
        width: 100%;
        justify-content: center;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .animate-fade-up,
      .float-gentle,
      .float-reverse,
      .float-delayed {
        animation: none !important;
      }
    }
  `],
})
export class OnboardingPageComponent {
  currentStep = signal(0);

  readonly steps: OnboardingStep[] = [
    {
      step: 1,
      tag: 'Step 1 • Full Visibility',
      title: 'Know where your money goes.',
      subtitle: 'Bring your transactions together in real time and see your true net cash flow clearly.',
      badge: 'Real-time Flow',
    },
    {
      step: 2,
      tag: 'Step 2 • Intelligent Insights',
      title: 'Turn transactions into insight.',
      subtitle: 'Automatically understand where your money is going across food, shopping, travel, and essentials.',
      badge: 'AI Categorized',
    },
    {
      step: 3,
      tag: 'Step 3 • Proactive Control',
      title: 'Stay ahead of your spending.',
      subtitle: 'Track your habits, understand your trends, and stay within the limits you set without budgeting fatigue.',
      badge: 'Pacing on Track',
    },
    {
      step: 4,
      tag: 'Step 4 • Long-term Wealth',
      title: 'Build better financial habits.',
      subtitle: 'Spend with intention. Save with confidence. Watch your savings rate and net reserves expand.',
      badge: 'Financial Clarity',
    },
  ];

  next(): void {
    if (this.currentStep() < this.steps.length - 1) {
      this.currentStep.update((s) => s + 1);
    }
  }

  prev(): void {
    if (this.currentStep() > 0) {
      this.currentStep.update((s) => s - 1);
    }
  }

  goTo(idx: number): void {
    this.currentStep.set(idx);
  }
}
