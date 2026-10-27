import { Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AssistantService } from './assistant.service';
import {
  AssistantPendingAction,
  ChatMessage,
  ToolCallSummary,
} from '../../core/api/api.types';

export interface DisplayMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  executedTools?: ToolCallSummary[];
  pendingAction?: AssistantPendingAction | null;
  actionStatus?: 'PENDING' | 'CONFIRMING' | 'EXECUTED' | 'CANCELLING' | 'CANCELLED' | 'FAILED';
  actionError?: string | null;
  actionResult?: string | null;
}

export interface AssistantVisualHighlight {
  category: string;
  amount: string;
  percentage?: number;
  count?: number;
}

import { AiMascotComponent } from '../../shared/components/ai-mascot/ai-mascot.component';

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AiMascotComponent],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
})
export class AssistantComponent implements OnInit {
  private readonly assistantService = inject(AssistantService);
  private readonly route = inject(ActivatedRoute);

  @ViewChild('chatScroll') private readonly chatScrollContainer?: ElementRef<HTMLDivElement>;

  userInput = '';
  loading = false;
  errorMessage: string | null = null;
  lastSubmittedPrompt = '';

  messages: DisplayMessage[] = [
    {
      id: 'welcome',
      role: 'assistant',
      content: 'Hello! I am your Mintmate Financial Assistant. I can review your verified spending, category breakdowns, budgets, and linked accounts. How can I help you today?',
      timestamp: new Date(),
    },
  ];

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      const prompt = params['prompt'];
      if (prompt && typeof prompt === 'string' && prompt.trim()) {
        this.sendMessage(prompt.trim());
      }
    });
  }

  readonly starterPrompts: string[] = [
    'How much did I spend last month?',
    'How much did I spend on food?',
    'Did I spend more this month than last month?',
    'Am I within my food budget?',
    'What bank accounts do I have linked?',
    'What transactions over ₹5,000 do I have?',
  ];

  cleanText(content: string): string {
    if (!content) return '';
    // Format human-friendly dates if ISO dates appear:
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
      .replace(/\s*\([A-Z_]{3,}\)/g, ''); // Strip internal category enums like (FOOD)
  }

  categoryLabel(cat?: string | null): string {
    if (!cat) return 'Other';
    const c = cat.trim();
    return c.charAt(0).toUpperCase() + c.slice(1).toLowerCase();
  }

  getVisualHighlight(content: string): AssistantVisualHighlight | null {
    if (!content) return null;
    // Pattern: spending on <Category> was <₹Amount> [across <N> transactions] [(<X>% of total expenses)]
    const match = content.match(/spending on ([^,]+?) was (₹[\d,]+(?:\.\d{1,2})?)(?: across (\d+) transactions)?(?:\s*\(([0-9.]+)% of total expenses\))?/i);
    if (match) {
      return {
        category: match[1].trim(),
        amount: match[2].trim(),
        count: match[3] ? parseInt(match[3], 10) : undefined,
        percentage: match[4] ? Math.round(parseFloat(match[4])) : undefined,
      };
    }
    return null;
  }

  retryLast(): void {
    if (this.lastSubmittedPrompt) {
      this.sendMessage(this.lastSubmittedPrompt);
    }
  }

  sendMessage(textToSubmit?: string): void {
    const text = (textToSubmit || this.userInput).trim();
    if (!text || this.loading) {
      return;
    }

    this.lastSubmittedPrompt = text;
    this.errorMessage = null;
    const userMessage: DisplayMessage = {
      id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
      role: 'user',
      content: text,
      timestamp: new Date(),
    };

    this.messages.push(userMessage);
    this.userInput = '';
    this.loading = true;
    this.scrollToBottom();

    // Prepare stateless history for context
    const history: ChatMessage[] = this.messages
      .filter((m) => m.id !== 'welcome')
      .slice(-6)
      .map((m) => ({ role: m.role, content: m.content }));

    this.assistantService.chat(text, history).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.data) {
          const assistantMsg: DisplayMessage = {
            id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
            role: 'assistant',
            content: res.data.answer,
            timestamp: new Date(),
            executedTools: res.data.executedTools || [],
            pendingAction: res.data.requiresConfirmation ? res.data.pendingAction : null,
            actionStatus: res.data.requiresConfirmation ? 'PENDING' : undefined,
          };
          this.messages.push(assistantMsg);
        } else if (res.error) {
          this.errorMessage = 'Something went wrong. Please try again.';
        }
        this.scrollToBottom();
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Something went wrong. Please try again.';
        this.scrollToBottom();
      },
    });
  }

  confirmAction(msg: DisplayMessage): void {
    if (!msg.pendingAction || msg.actionStatus !== 'PENDING') {
      return;
    }

    msg.actionStatus = 'CONFIRMING';
    msg.actionError = null;

    this.assistantService.confirmAction(msg.pendingAction.actionId).subscribe({
      next: (res) => {
        msg.actionStatus = 'EXECUTED';
        msg.actionResult = res.data?.summaryText || 'Action executed successfully.';
        this.scrollToBottom();
      },
      error: (err) => {
        msg.actionStatus = 'FAILED';
        msg.actionError = err?.error?.error?.message || 'Failed to confirm action.';
        this.scrollToBottom();
      },
    });
  }

  cancelAction(msg: DisplayMessage): void {
    if (!msg.pendingAction || msg.actionStatus !== 'PENDING') {
      return;
    }

    msg.actionStatus = 'CANCELLING';
    msg.actionError = null;

    this.assistantService.cancelAction(msg.pendingAction.actionId).subscribe({
      next: () => {
        msg.actionStatus = 'CANCELLED';
        this.scrollToBottom();
      },
      error: (err) => {
        msg.actionStatus = 'FAILED';
        msg.actionError = err?.error?.error?.message || 'Failed to cancel action.';
        this.scrollToBottom();
      },
    });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.chatScrollContainer?.nativeElement) {
        const el = this.chatScrollContainer.nativeElement;
        el.scrollTop = el.scrollHeight;
      }
    }, 50);
  }
}
