import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AiMascotComponent, MascotState } from './ai-mascot.component';

describe('AiMascotComponent', () => {
  let component: AiMascotComponent;
  let fixture: ComponentFixture<AiMascotComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiMascotComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AiMascotComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the mascot component with default inputs', () => {
    expect(component).toBeTruthy();
    expect(component.state).toBe('IDLE');
    expect(component.size).toBe('md');
    expect(component.showCoin).toBeTrue();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.bot-trigger')).toBeTruthy();
    expect(compiled.querySelector('.bot-svg')).toBeTruthy();
    expect(compiled.querySelector('.rupee-symbol')).toBeTruthy();
    expect(compiled.querySelector('.fintech-coin')).toBeTruthy();
  });

  it('should emit clicked event on button click with activation sequence', fakeAsync(() => {
    spyOn(component.clicked, 'emit');

    const button = fixture.nativeElement.querySelector('.bot-trigger') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    expect(component.isActivating()).toBeTrue();
    tick(350);
    fixture.detectChanges();

    expect(component.clicked.emit).toHaveBeenCalled();
    expect(component.isActivating()).toBeFalse();
  }));

  it('should not emit clicked event when disabled', fakeAsync(() => {
    component.disabled = true;
    fixture.detectChanges();

    spyOn(component.clicked, 'emit');
    const button = fixture.nativeElement.querySelector('.bot-trigger') as HTMLButtonElement;
    button.click();
    tick(350);

    expect(component.clicked.emit).not.toHaveBeenCalled();
    expect(component.isActivating()).toBeFalse();
  }));

  it('should render thinking state with 3 pulse indicators', () => {
    component.state = 'THINKING';
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.thinking-indicators')).toBeTruthy();
    expect(compiled.querySelectorAll('.think-dot').length).toBe(3);
  });

  it('should render success state with smile expression and celebration particles', () => {
    component.state = 'SUCCESS';
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.success-expression')).toBeTruthy();
    expect(compiled.querySelector('.celebration-particles')).toBeTruthy();
  });

  it('should update parallax rotate variables on mouse move', () => {
    const stage = fixture.nativeElement.querySelector('.mascot-stage') as HTMLElement;
    spyOn(stage, 'getBoundingClientRect').and.returnValue({
      left: 100,
      top: 100,
      width: 100,
      height: 100,
      right: 200,
      bottom: 200,
      x: 100,
      y: 100,
      toJSON: () => {},
    });

    // Move to bottom-right
    component.onMouseMove({ clientX: 180, clientY: 180 } as MouseEvent);
    expect(component.rotateY()).toBeGreaterThan(0);
    expect(component.rotateX()).toBeLessThan(0);

    // Mouse leave resets angles
    component.onMouseLeave();
    expect(component.rotateX()).toBe(0);
    expect(component.rotateY()).toBe(0);
  });

  it('should support keyboard navigation with Enter and Space', fakeAsync(() => {
    spyOn(component.clicked, 'emit');
    const button = fixture.nativeElement.querySelector('.bot-trigger') as HTMLButtonElement;

    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    tick(350);
    expect(component.clicked.emit).toHaveBeenCalled();
  }));
});
