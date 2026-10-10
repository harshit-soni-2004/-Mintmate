import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';

export type MascotState = 'IDLE' | 'HOVER' | 'THINKING' | 'SUCCESS' | 'ALERT' | 'CHAT_OPEN';
export type MascotSize = 'sm' | 'md' | 'lg' | 'xl';

@Component({
  selector: 'app-ai-mascot',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-mascot.component.html',
  styleUrl: './ai-mascot.component.scss',
})
export class AiMascotComponent implements OnInit, OnChanges, OnDestroy {
  @Input() state: MascotState = 'IDLE';
  @Input() size: MascotSize = 'md';
  @Input() disabled: boolean = false;
  @Input() showCoin: boolean = true;
  @Input() interactive: boolean = true;
  @Input() tooltipText: string = 'Ask Mintmate AI';
  @Input() showTooltipOnHover: boolean = true;
  @Input() ariaLabel: string = 'Open Mintmate AI assistant';

  @Output() clicked = new EventEmitter<Event>();
  @Output() stateChange = new EventEmitter<MascotState>();

  // Interactive mouse parallax state (CSS variables)
  rotateX = signal<number>(0);
  rotateY = signal<number>(0);
  isHovered = signal<boolean>(false);
  isActivating = signal<boolean>(false);
  isBlinking = signal<boolean>(false);

  private blinkTimeoutId: any = null;
  private successResetTimeoutId: any = null;

  constructor(private readonly el: ElementRef<HTMLElement>) {}

  ngOnInit(): void {
    if (this.interactive) {
      this.scheduleRandomBlink();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['state'] && this.state === 'SUCCESS') {
      // Auto-revert SUCCESS to IDLE or CHAT_OPEN after 2.6 seconds if not held
      clearTimeout(this.successResetTimeoutId);
      this.successResetTimeoutId = setTimeout(() => {
        if (this.state === 'SUCCESS') {
          this.state = 'IDLE';
          this.stateChange.emit(this.state);
        }
      }, 2600);
    }
  }

  ngOnDestroy(): void {
    if (this.blinkTimeoutId) {
      clearTimeout(this.blinkTimeoutId);
    }
    if (this.successResetTimeoutId) {
      clearTimeout(this.successResetTimeoutId);
    }
  }

  onTriggerClick(event?: Event): void {
    if (this.disabled) {
      return;
    }

    // Trigger activation animation sequence
    this.isActivating.set(true);

    setTimeout(() => {
      this.isActivating.set(false);
      this.clicked.emit(event || new CustomEvent('click'));
    }, 320);
  }

  onMouseEnter(): void {
    if (this.disabled || !this.interactive) {
      return;
    }
    this.isHovered.set(true);
    if (this.state === 'IDLE') {
      this.stateChange.emit('HOVER');
    }
  }

  onMouseLeave(): void {
    this.isHovered.set(false);
    this.rotateX.set(0);
    this.rotateY.set(0);
    if (this.state === 'HOVER') {
      this.stateChange.emit('IDLE');
    }
  }

  onMouseMove(event: MouseEvent): void {
    if (this.disabled || !this.interactive) {
      return;
    }

    const hostRect = this.el.nativeElement.getBoundingClientRect();
    const centerX = hostRect.left + hostRect.width / 2;
    const centerY = hostRect.top + hostRect.height / 2;

    const deltaX = (event.clientX - centerX) / (hostRect.width / 2);
    const deltaY = (event.clientY - centerY) / (hostRect.height / 2);

    // Keep parallax subtle: ±4deg on Y axis, ±3deg on X axis
    const clampedX = Math.max(-1, Math.min(1, deltaX));
    const clampedY = Math.max(-1, Math.min(1, deltaY));

    this.rotateY.set(clampedX * 4);
    this.rotateX.set(-clampedY * 3);
  }

  private scheduleRandomBlink(): void {
    // Randomized interval between 4 and 7 seconds
    const nextBlinkDelay = Math.floor(Math.random() * 3000) + 4000;
    this.blinkTimeoutId = setTimeout(() => {
      // Don't blink while thinking (eyes are in processing mode)
      if (this.state !== 'THINKING') {
        this.triggerBlink();
      }
      this.scheduleRandomBlink();
    }, nextBlinkDelay);
  }

  private triggerBlink(): void {
    this.isBlinking.set(true);
    setTimeout(() => {
      this.isBlinking.set(false);
    }, 150);
  }
}
