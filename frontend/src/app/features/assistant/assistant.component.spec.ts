import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssistantComponent } from './assistant.component';
import { AssistantService } from './assistant.service';
import { of } from 'rxjs';
import { ApiResponse, AssistantChatResponse, PendingActionResult } from '../../core/api/api.types';

describe('AssistantComponent', () => {
  let component: AssistantComponent;
  let fixture: ComponentFixture<AssistantComponent>;
  let mockAssistantService: jasmine.SpyObj<AssistantService>;

  beforeEach(async () => {
    mockAssistantService = jasmine.createSpyObj('AssistantService', ['chat', 'confirmAction', 'cancelAction']);

    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [{ provide: AssistantService, useValue: mockAssistantService }],
    }).compileComponents();

    fixture = TestBed.createComponent(AssistantComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize with welcome message and starter prompts', () => {
    expect(component).toBeTruthy();
    expect(component.messages.length).toBe(1);
    expect(component.messages[0].id).toBe('welcome');
    expect(component.starterPrompts.length).toBeGreaterThan(0);
  });

  it('should send user message and display assistant response', () => {
    const chatResponse: ApiResponse<AssistantChatResponse> = {
      data: {
        answer: 'You spent ₹9,617 on Food last month.',
        executedTools: [{ toolName: 'get_spending_by_category', summary: 'Spending was ₹9,617' }],
        requiresConfirmation: false,
      },
      error: null,
      meta: null,
    };

    mockAssistantService.chat.and.returnValue(of(chatResponse));

    component.userInput = 'How much did I spend on food?';
    component.sendMessage();

    expect(mockAssistantService.chat).toHaveBeenCalledWith('How much did I spend on food?', jasmine.any(Array));
    expect(component.messages.length).toBe(3); // Welcome + User + Assistant
    expect(component.messages[1].role).toBe('user');
    expect(component.messages[2].role).toBe('assistant');
    expect(component.messages[2].content).toContain('₹9,617');
    expect(component.messages[2].executedTools?.length).toBe(1);
  });

  it('should display confirmation card and handle confirm action', () => {
    const confirmationResponse: ApiResponse<AssistantChatResponse> = {
      data: {
        answer: 'You are proposing to change Swiggy to Food.',
        executedTools: [],
        requiresConfirmation: true,
        pendingAction: {
          actionId: 'action-123',
          actionType: 'update_transaction_category',
          summary: 'Change Swiggy to Food & Dining',
          expiresAt: '2026-09-24T01:00:00Z',
        },
      },
      error: null,
      meta: null,
    };

    mockAssistantService.chat.and.returnValue(of(confirmationResponse));

    component.sendMessage('Change Swiggy category to Food');
    const assistantMsg = component.messages[component.messages.length - 1];

    expect(assistantMsg.pendingAction).toBeDefined();
    expect(assistantMsg.actionStatus).toBe('PENDING');

    // Confirm action
    const confirmResult: ApiResponse<PendingActionResult> = {
      data: {
        actionId: 'action-123',
        toolName: 'update_transaction_category',
        status: 'EXECUTED',
        summaryText: 'Swiggy category successfully updated to Food & Dining',
        result: {},
        executedAt: '2026-09-24T00:30:00Z',
      },
      error: null,
      meta: null,
    };

    mockAssistantService.confirmAction.and.returnValue(of(confirmResult));
    component.confirmAction(assistantMsg);

    expect(mockAssistantService.confirmAction).toHaveBeenCalledWith('action-123');
    expect(assistantMsg.actionStatus).toBe('EXECUTED');
    expect(assistantMsg.actionResult).toContain('successfully updated');
  });

  it('should handle cancel action on confirmation card', () => {
    const confirmationResponse: ApiResponse<AssistantChatResponse> = {
      data: {
        answer: 'You are proposing to create a budget.',
        executedTools: [],
        requiresConfirmation: true,
        pendingAction: {
          actionId: 'action-456',
          actionType: 'create_or_update_budget',
          summary: 'Set Food budget to ₹10,000',
          expiresAt: '2026-09-24T01:00:00Z',
        },
      },
      error: null,
      meta: null,
    };

    mockAssistantService.chat.and.returnValue(of(confirmationResponse));
    component.sendMessage('Set food budget to 10000');

    const assistantMsg = component.messages[component.messages.length - 1];
    expect(assistantMsg.actionStatus).toBe('PENDING');

    mockAssistantService.cancelAction.and.returnValue(of({ data: { message: 'Action cancelled' }, error: null, meta: null }));
    component.cancelAction(assistantMsg);

    expect(mockAssistantService.cancelAction).toHaveBeenCalledWith('action-456');
    expect(assistantMsg.actionStatus).toBe('CANCELLED');
  });
});
