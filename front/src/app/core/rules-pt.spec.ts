import { ABILITIES, modifier, signed, SKILLS_PT } from './rules-pt';

describe('regras em portugues da ficha', () => {
  it('modificador e sinal como no programa de PC', () => {
    expect(modifier(16)).toBe(3);
    expect(modifier(9)).toBe(-1);
    expect(modifier(10)).toBe(0);
    expect(signed(3)).toBe('+3');
    expect(signed(-1)).toBe('-1');
  });
  it('tem os 6 atributos e as 18 pericias com acento', () => {
    expect(ABILITIES.length).toBe(6);
    expect(Object.keys(SKILLS_PT).length).toBe(18);
    expect(SKILLS_PT['PERCEPTION']).toBe('Percepção');
  });
});
