import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FloatingAssistantComponent } from './floating-assistant.component';
import { Router } from '@angular/router';
import { AssistantService } from '../../features/assistant/assistant.service';
import { of } from 'rxjs';

describe('FloatingAssistantComponent', () => {
  let component: FloatingAssistantComponent;
  let fixture: ComponentFixture<FloatingAssistantComponent>;
  let routerSpy: jasmine.SpyObj<Router>;
  let assistantServiceSpy: jasmine.SpyObj<AssistantService>;

  beforeEach(async () => {
    localStorage.removeItem('mintmate_bot_bubble_dismissed');
    localStorage.removeItem('spendsense_bot_bubble_dismissed');
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);
    assistantServiceSpy = jasmine.createSpyObj('AssistantService', ['chat', 'confirmAction', 'cancelAction']);

    await TestBed.configureTestingModule({
      imports: [FloatingAssistantComponent],
      providers: [
        { provide: Router, useValue: routerSpy },
        { provide: AssistantService, useValue: assistantServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FloatingAssistantComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.removeItem('mintmate_bot_bubble_dismissed');
    localStorage.removeItem('spendsense_bot_bubble_dismissed');
  });

  it('should create and render bot trigger with SVG and Rupee symbol', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.bot-trigger')).toBeTruthy();
    expect(compiled.querySelector('.bot-svg')).toBeTruthy();
    expect(compiled.querySelector('.rupee-symbol')).toBeTruthy();
    expect(compiled.querySelector('.fintech-coin')).toBeTruthy();
  });

  it('should open AI chat panel and update mascot state when clicked', fakeAsync(() => {
    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('.bot-trigger') as HTMLButtonElement;

    expect(component.isChatOpen()).toBeFalse();
    expect(component.mascotState()).toBe('IDLE');

    button.click();
    tick(350); // Mascot activation sequence
    fixture.detectChanges();

    expect(component.isChatOpen()).toBeTrue();
    expect(component.mascotState()).toBe('CHAT_OPEN');

    // Chat flyout should be rendered
    expect(compiled.querySelector('.floating-chat-flyout')).toBeTruthy();
    expect(compiled.querySelector('.welcome-box')).toBeTruthy();
    expect(compiled.querySelectorAll('.suggestion-chip').length).toBe(5);
  }));

  it('should navigate to /app/assistant when expand or openAssistant is called', () => {
    component.openAssistant();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/app/assistant');
  });

  it('should dismiss speech bubble when close button is clicked', fakeAsync(() => {
    tick(1500); // Wait for timeout in constructor
    component.showOnboardingBubble.set(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const closeBtn = compiled.querySelector('.bubble-close-btn') as HTMLButtonElement;
    expect(closeBtn).toBeTruthy();

    closeBtn.click();
    fixture.detectChanges();

    expect(component.showOnboardingBubble()).toBeFalse();
    expect(localStorage.getItem('mintmate_bot_bubble_dismissed')).toBe('true');
  }));

  it('should send suggestion prompt and show response with thinking then success states', fakeAsync(() => {
    assistantServiceSpy.chat.and.returnValue(
      of({
        data: {
          answer: 'Your largest expense category this month is Food & Dining at ₹12,450.',
          executedTools: [],
          requiresConfirmation: false,
        },
        error: null,
        meta: null,
      })
    );

    // Open chat
    component.openChat();
    fixture.detectChanges();

    // Click suggestion
    component.sendPrompt('Where did I spend the most this month?');
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
    expect(component.mascotState()).toBe('SUCCESS');
    expect(component.messages().length).toBe(3); // welcome + user + assistant
    expect(component.messages()[2].content).toContain('Food & Dining');

    // After success timeout, returns to CHAT_OPEN
    tick(2500);
    fixture.detectChanges();
    expect(component.mascotState()).toBe('CHAT_OPEN');
  }));
});
