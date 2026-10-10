import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ThemeService, ThemePreference } from '../../core/theme/theme.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="page settings-page">
      <header class="settings-header">
        <p class="eyebrow">Preferences</p>
        <h1>Settings</h1>
        <p class="subtitle">Customize your Mintmate experience and display preferences.</p>
      </header>

      <!-- Appearance Card -->
      <div class="settings-section">
        <div class="section-title">
          <h2>Appearance</h2>
          <p>Choose how Mintmate looks to you. Select a specific theme or sync with your system.</p>
        </div>

        <div class="theme-options">
          <!-- System -->
          <button
            type="button"
            class="theme-card"
            [class.selected]="themeService.theme() === 'system'"
            (click)="themeService.setTheme('system')">
            <div class="preview-box system-preview">
              <div class="half light"></div>
              <div class="half dark"></div>
            </div>
            <div class="theme-info">
              <span class="theme-name">System</span>
              <span class="theme-desc">Follow OS preference</span>
            </div>
            <span class="radio-indicator" *ngIf="themeService.theme() === 'system'">✓</span>
          </button>

          <!-- Light -->
          <button
            type="button"
            class="theme-card"
            [class.selected]="themeService.theme() === 'light'"
            (click)="themeService.setTheme('light')">
            <div class="preview-box light-preview">
              <div class="preview-header"></div>
              <div class="preview-content"></div>
            </div>
            <div class="theme-info">
              <span class="theme-name">Light</span>
              <span class="theme-desc">Crisp & clear</span>
            </div>
            <span class="radio-indicator" *ngIf="themeService.theme() === 'light'">✓</span>
          </button>

          <!-- Dark -->
          <button
            type="button"
            class="theme-card"
            [class.selected]="themeService.theme() === 'dark'"
            (click)="themeService.setTheme('dark')">
            <div class="preview-box dark-preview">
              <div class="preview-header"></div>
              <div class="preview-content"></div>
            </div>
            <div class="theme-info">
              <span class="theme-name">Dark</span>
              <span class="theme-desc">Premium dark fintech</span>
            </div>
            <span class="radio-indicator" *ngIf="themeService.theme() === 'dark'">✓</span>
          </button>
        </div>
      </div>

      <!-- Account Profile Section -->
      <div class="settings-section">
        <div class="section-title">
          <h2>Account Profile</h2>
          <p>Your authenticated Mintmate credentials.</p>
        </div>
        <div class="profile-card">
          <div class="profile-row">
            <span class="label">Display Name</span>
            <span class="value">{{ auth.currentUser()?.displayName || 'Mintmate User' }}</span>
          </div>
          <div class="profile-row">
            <span class="label">Email Address</span>
            <span class="value">{{ auth.currentUser()?.email || 'N/A' }}</span>
          </div>
          <div class="profile-row">
            <span class="label">Default Timezone</span>
            <span class="value">Asia/Kolkata (IST)</span>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .settings-page {
      max-width: 860px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 2rem;
    }

    .settings-header {
      h1 {
        margin: 0.25rem 0 0.5rem;
        font-size: 2rem;
        color: var(--text-primary);
      }
      .subtitle {
        color: var(--text-secondary);
        font-size: 0.95rem;
        margin: 0;
      }
    }

    .settings-section {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      padding: clamp(1.25rem, 3vw, 2rem);
      box-shadow: var(--shadow-sm);
      width: 100%;
      box-sizing: border-box;

      .section-title {
        margin-bottom: 1.5rem;
        h2 {
          font-size: 1.25rem;
          margin: 0 0 0.25rem;
          color: var(--text-primary);
        }
        p {
          color: var(--text-secondary);
          font-size: 0.88rem;
          margin: 0;
        }
      }
    }

    .theme-options {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
      gap: 1rem;
    }

    @media (max-width: 640px) {
      .settings-page {
        padding: 0 0 2rem;
      }
      .theme-options {
        grid-template-columns: 1fr;
      }
    }

    .theme-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      padding: 1rem;
      background: var(--surface-muted);
      border: 2px solid var(--border);
      border-radius: var(--radius-md);
      cursor: pointer;
      text-align: left;
      position: relative;
      transition: all 0.2s ease-in-out;

      &:hover {
        border-color: var(--accent);
        transform: translateY(-2px);
      }

      &.selected {
        border-color: var(--accent);
        background: var(--surface-elevated);
        box-shadow: 0 0 0 1px var(--accent);
      }

      .preview-box {
        width: 100%;
        height: 64px;
        border-radius: var(--radius-sm);
        margin-bottom: 0.85rem;
        overflow: hidden;
        border: 1px solid var(--border);
        display: flex;

        &.light-preview {
          background: #f6f9f7;
          flex-direction: column;
          padding: 6px;
          gap: 4px;
          .preview-header { height: 12px; background: #127457; border-radius: 3px; }
          .preview-content { flex: 1; background: #ffffff; border-radius: 3px; }
        }

        &.dark-preview {
          background: #0b0f17;
          flex-direction: column;
          padding: 6px;
          gap: 4px;
          .preview-header { height: 12px; background: #10b981; border-radius: 3px; }
          .preview-content { flex: 1; background: #1a2434; border-radius: 3px; }
        }

        &.system-preview {
          .half {
            flex: 1;
            height: 100%;
            &.light { background: #f6f9f7; }
            &.dark { background: #0b0f17; }
          }
        }
      }

      .theme-info {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;

        .theme-name {
          font-weight: 700;
          font-size: 0.95rem;
          color: var(--text-primary);
        }

        .theme-desc {
          font-size: 0.78rem;
          color: var(--text-secondary);
        }
      }

      .radio-indicator {
        position: absolute;
        top: 0.75rem;
        right: 0.75rem;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background: var(--accent);
        color: #ffffff;
        font-size: 0.75rem;
        font-weight: 800;
        display: grid;
        place-items: center;
      }
    }

    .profile-card {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      background: var(--surface-muted);
      border-radius: var(--radius-md);
      padding: 1.25rem;

      .profile-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-bottom: 0.75rem;
        border-bottom: 1px solid var(--border);

        &:last-child {
          padding-bottom: 0;
          border-bottom: none;
        }

        .label {
          font-size: 0.88rem;
          color: var(--text-secondary);
          font-weight: 500;
        }

        .value {
          font-size: 0.92rem;
          color: var(--text-primary);
          font-weight: 600;
        }
      }
    }
  `]
})
export class SettingsComponent {
  readonly themeService = inject(ThemeService);
  readonly auth = inject(AuthService);
}
