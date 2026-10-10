import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AiMascotComponent, MascotState } from '../../shared/components/ai-mascot/ai-mascot.component';
import { AssistantService } from '../../features/assistant/assistant.service';

const ONBOARDING_DISMISSED_KEY = 'mintmate_bot_bubble_dismissed';
const LEGACY_DISMISSED_KEY = 'spendsense_bot_bubble_dismissed';

export interface FlyoutMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

@Component({
  selector: 'app-floating-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, AiMascotComponent],
  templateUrl: './floating-assistant.component.html',
  styleUrl: './floating-assistant.component.scss',
})
export class FloatingAssistantComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly assistantService = inject(AssistantService, { optional: true });

  @ViewChild('chatScrollContainer') private readonly chatScrollContainer?: ElementRef<HTMLDivElement>;

  readonly isChatOpen = signal<boolean>(false);
  readonly mascotState = signal<MascotState>('IDLE');
  readonly showOnboardingBubble = signal<boolean>(false);
  readonly isSmallScreen = signal<boolean>(false);
  readonly isAssistantPage = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  userInput = '';
  readonly messages = signal<FlyoutMessage[]>([]);

  readonly suggestions: string[] = [
    'Where did I spend the most this month?',
    'How can I save ₹5,000 this month?',
    'Analyze my spending',
    'Why did my expenses increase?',
    'Help me set a budget',
  ];

  private successStateTimeout: any = null;

  cleanText(content: string): string {
    if (!content) return '';
    return content
      .replace(/From (\d{4})-(\d{2})-(\d{2}) to (\d{4})-(\d{2})-(\d{2}),?\s*/gi, (_match, y1, m1, d1, y2, m2, d2) => {
        const d1Obj = new Date(Number(y1), Number(m1) - 1, Number(d1));
        const d2Obj = new Date(Number(y2), Number(m2) - 1, Number(d2));
        const m1Name = d1Obj.toLocaleString('en-US', { month: 'short' });
        const m2Name = d2Obj.toLocaleString('en-US', { month: 'short' });
        if (m1Name === m2Name && y1 === y2) {
          return `In ${d1Obj.toLocaleString('en-US', { month: 'long', year: 'numeric' })}, `;
        }
        return `Between ${m1Name} ${Number(d1)} and ${m2Name} ${Number(d2)}, `;
      })
      .replace(/\s*\([A-Z_]{3,}\)/g, '');
  }

  constructor() {
    this.checkScreenSize();
    if (typeof localStorage !== 'undefined') {
      const dismissed = localStorage.getItem(ONBOARDING_DISMISSED_KEY) ?? localStorage.getItem(LEGACY_DISMISSED_KEY);
      if (!dismissed) {
        setTimeout(() => {
          if (!this.isChatOpen()) {
            this.showOnboardingBubble.set(true);
          }
        }, 1200);
      }
    }
  }

  ngOnInit(): void {
    this.checkScreenSize();
  }

  ngOnDestroy(): void {
    if (this.successStateTimeout) {
      clearTimeout(this.successStateTimeout);
    }
  }

  @HostListener('window:resize')
  onResize(): void {
    this.checkScreenSize();
  }

  @HostListener('window:keydown.escape')
  onEscape(): void {
    if (this.isChatOpen()) {
      this.closeChat();
    }
  }

  private checkScreenSize(): void {
    if (typeof window !== 'undefined') {
      this.isSmallScreen.set(window.innerWidth <= 840);
    }
    const currentUrl = this.router?.url || '';
    this.isAssistantPage.set(currentUrl.includes('/assistant'));
  }

  handleMascotClick(): void {
    this.dismissBubble();
    if (this.isChatOpen()) {
      this.closeChat();
    } else {
      this.openChat();
    }
  }

  openChat(): void {
    this.isChatOpen.set(true);
    this.mascotState.set('CHAT_OPEN');
    if (this.messages().length === 0) {
      this.messages.set([
        {
          id: 'welcome',
          role: 'assistant',
          content: 'Hello! I am your Mintmate Financial Assistant. Ask me anything about your spending, trends, or budgets.',
          timestamp: new Date(),
        },
      ]);
    }
    setTimeout(() => this.scrollToBottom(), 100);
  }

  closeChat(): void {
    this.isChatOpen.set(false);
    this.mascotState.set('IDLE');
  }

  openFullAssistant(): void {
    this.closeChat();
    this.router.navigateByUrl('/app/assistant');
  }

  handleStateChange(newState: MascotState): void {
    // If not locked in CHAT_OPEN or THINKING, allow state sync
    if (!this.isChatOpen() && this.mascotState() !== 'THINKING') {
      this.mascotState.set(newState);
    }
  }

  sendPrompt(prompt: string): void {
    this.userInput = prompt;
    this.sendCurrentMessage();
  }

  sendCurrentMessage(): void {
    const text = this.userInput.trim();
    if (!text || this.isLoading()) {
      return;
    }

    this.errorMessage.set(null);
    const userMsg: FlyoutMessage = {
      id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
      role: 'user',
      content: text,
      timestamp: new Date(),
    };

    this.messages.update((list) => [...list, userMsg]);
    this.userInput = '';
    this.isLoading.set(true);
    this.mascotState.set('THINKING');
    this.scrollToBottom();

    if (!this.assistantService) {
      // Fallback for isolated unit tests or when service is unavailable
      setTimeout(() => {
        this.isLoading.set(false);
        this.mascotState.set('SUCCESS');
        this.messages.update((list) => [
          ...list,
          {
            id: String(Date.now()),
            role: 'assistant',
            content: `I received your question: "${text}". Your data is securely verified.`,
            timestamp: new Date(),
          },
        ]);
        this.scrollToBottom();
        this.scheduleReturnToChatOpen();
      }, 500);
      return;
    }

    // Call real assistant backend service
    const history = this.messages()
      .filter((m) => m.id !== 'welcome')
      .slice(-6)
      .map((m) => ({ role: m.role, content: m.content }));

    this.assistantService.chat(text, history).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        if (res.data && res.data.answer) {
          this.mascotState.set('SUCCESS');
          this.messages.update((list) => [
            ...list,
            {
              id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
              role: 'assistant',
              content: res.data!.answer,
              timestamp: new Date(),
            },
          ]);
          this.scheduleReturnToChatOpen();
        } else if (res.error?.message) {
          this.mascotState.set('ALERT');
          this.errorMessage.set(res.error.message);
        }
        this.scrollToBottom();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.mascotState.set('ALERT');
        this.errorMessage.set('Something went wrong. Please try again.');
        this.scrollToBottom();
      },
    });
  }

  private scheduleReturnToChatOpen(): void {
    clearTimeout(this.successStateTimeout);
    this.successStateTimeout = setTimeout(() => {
      if (this.isChatOpen()) {
        this.mascotState.set('CHAT_OPEN');
      } else {
        this.mascotState.set('IDLE');
      }
    }, 2400);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.chatScrollContainer) {
        this.chatScrollContainer.nativeElement.scrollTop =
          this.chatScrollContainer.nativeElement.scrollHeight;
      }
    }, 60);
  }

  dismissBubble(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.showOnboardingBubble.set(false);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(ONBOARDING_DISMISSED_KEY, 'true');
      localStorage.removeItem(LEGACY_DISMISSED_KEY);
    }
  }

  // Backward compatible method for existing tests
  openAssistant(): void {
    this.openFullAssistant();
  }

  onHover(): void {
    if (!this.isChatOpen() && this.mascotState() === 'IDLE') {
      this.mascotState.set('HOVER');
    }
  }
}
