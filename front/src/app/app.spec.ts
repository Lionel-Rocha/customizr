import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  it('abre com o tema salvo aplicado no <html>', async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(document.documentElement.dataset['theme']).toBeTruthy();
  });
});
