import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
})
export class AssistantComponent {
  private readonly assistantService = inject(AssistantService);

  @ViewChild('chatScroll') private readonly chatScrollContainer?: ElementRef<HTMLDivElement>;

  userInput = '';
  loading = false;
  errorMessage: string | null = null;

  messages: DisplayMessage[] = [
    {
      id: 'welcome',
      role: 'assistant',
      content: 'Hello! I am your SpendSense Financial Assistant. I can review your verified spending, category breakdowns, budgets, and linked accounts. How can I help you today?',
      timestamp: new Date(),
    },
  ];

  readonly starterPrompts: string[] = [
    'How much did I spend last month?',
    'How much did I spend on food?',
    'Did I spend more this month than last month?',
    'Am I within my food budget?',
    'What bank accounts do I have linked?',
    'What transactions over ₹5,000 do I have?',
  ];

  sendMessage(textToSubmit?: string): void {
    const text = (textToSubmit || this.userInput).trim();
    if (!text || this.loading) {
      return;
    }

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
          this.errorMessage = res.error.message || 'Failed to receive assistant response.';
        }
        this.scrollToBottom();
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err?.error?.error?.message || err?.message || 'Error communicating with assistant.';
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
