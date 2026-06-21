import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LandingComponent } from './landing.component';

describe('LandingComponent', () => {
  let fixture: ComponentFixture<LandingComponent>;
  let component: LandingComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(LandingComponent);
    component = fixture.componentInstance;
  });

  it('creates successfully', () => {
    expect(component).toBeTruthy();
  });

  it('exposes 3 feature cards and 5 process steps for the template', () => {
    fixture.detectChanges();

    expect(component.features.length).toBe(3);
    expect(component.steps.length).toBe(5);
  });

  it('every step has a unique sequence number', () => {
    const nums = component.steps.map((s) => s.num);
    expect(new Set(nums).size).toBe(nums.length);
  });
});
