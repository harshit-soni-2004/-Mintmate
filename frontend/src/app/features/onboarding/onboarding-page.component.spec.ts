import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OnboardingPageComponent } from './onboarding-page.component';

describe('OnboardingPageComponent', () => {
  let component: OnboardingPageComponent;
  let fixture: ComponentFixture<OnboardingPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OnboardingPageComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(OnboardingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('starts at step 0', () => {
    expect(component.currentStep()).toBe(0);
  });

  it('navigates next and prev steps accurately', () => {
    component.next();
    expect(component.currentStep()).toBe(1);

    component.next();
    expect(component.currentStep()).toBe(2);

    component.prev();
    expect(component.currentStep()).toBe(1);

    component.goTo(3);
    expect(component.currentStep()).toBe(3);
  });
});
