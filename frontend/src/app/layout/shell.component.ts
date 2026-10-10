import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { FloatingAssistantComponent } from './floating-assistant/floating-assistant.component';

@Component({
  selector: 'app-shell',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FloatingAssistantComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);

  @ViewChild('mainContent') private mainContent?: ElementRef<HTMLElement>;

  readonly isProfileSheetOpen = signal(false);

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.closeProfileSheet();
        if (this.mainContent?.nativeElement) {
          this.mainContent.nativeElement.scrollTo({ top: 0, behavior: 'instant' });
        }
      });
  }

  toggleProfileSheet(): void {
    this.isProfileSheetOpen.update((v) => !v);
  }

  closeProfileSheet(): void {
    this.isProfileSheetOpen.set(false);
  }
}
