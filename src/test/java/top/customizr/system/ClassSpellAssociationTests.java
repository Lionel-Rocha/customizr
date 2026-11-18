package top.customizr.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.customizr.system.model.Components;
import top.customizr.system.model.Duration;
import top.customizr.system.model.DurationUnit;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Source;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SpellLevel;
import top.customizr.system.model.SpellSchool;
import top.customizr.system.model.Time;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpellRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.model.User;
import top.customizr.system.security.JwtService;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassSpellAssociationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DndClassRepository dndClassRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private SpellRepository spellRepository;

    // a rota exige login (SecurityConfig): o teste entra com um usuario de verdade
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @org.springframework.transaction.annotation.Transactional   // ler a lista (lazy) da classe no fim
    void createsClassSpellAssociationFromJsonRequest() throws Exception {
        Source source = sourceRepository.save(new Source("Test Source", "TST"));
        DndClass dndClass = dndClassRepository.save(new DndClass("Test Wizard", 8, 2, source));
        Spell spell = spellRepository.save(new Spell(
                "Fireball",
                SpellSchool.Evocation,
                SpellLevel.THIRD,
                Time.Action,
                150,
                Set.of(Components.Verbal, Components.Somatic),
                new Duration(1, DurationUnit.ROUND, true),
                source,
                "A fiery burst."
        ));

        String username = "class-spell-" + java.util.UUID.randomUUID();
        userRepository.save(new User(username, "p"));
        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(username));

        mockMvc.perform(post("/class-spell/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"Test Wizard\",\"spellName\":\"Fireball\"}"))
                .andExpect(status().isCreated());

        DndClass savedClass = dndClassRepository.findById(dndClass.getClassId()).orElseThrow();
        assertTrue(savedClass.getAvailableSpells().contains(spell));
    }
}
