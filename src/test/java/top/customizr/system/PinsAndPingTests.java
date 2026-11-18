package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CampaignImportResponse;
import top.customizr.system.dto.EncyclopediaEntryRequest;
import top.customizr.system.dto.PinRequest;
import top.customizr.system.dto.PinResponse;
import top.customizr.system.dto.PingRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.EntryType;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SceneRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CampaignTransferService;
import top.customizr.system.service.EncyclopediaService;
import top.customizr.system.service.LiveTableService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alfinetes no mapa (com verbete da enciclopedia) e ping. */
@SpringBootTest
@Transactional
class PinsAndPingTests {

    @Autowired private LiveTableService live;
    @Autowired private EncyclopediaService encyclopediaService;
    @Autowired private CampaignTransferService transfer;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SceneRepository sceneRepository;

    private String master;
    private String ana;
    private Campaign campaign;
    private UUID sceneId;

    @BeforeEach
    void setUp() {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("Vila", userRepository.save(new User(master, "p"))));
        User u = userRepository.save(new User(ana, "p"));
        membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Vila", null, GridType.SQUARE, 70, 5, 1400, 1000), master).id();
    }

    @Test
    void pinsHaveNotesAndEntriesAndHiddenOnesStayWithTheMaster() {
        var tavern = encyclopediaService.create(campaign.getId(),
                new EncyclopediaEntryRequest("Taverna do Javali", "O dono deve dinheiro ao barão.", EntryType.PLACE, true, null, null), master);
        PinResponse pin = live.addPin(sceneId, new PinRequest(300.0, 200.0, "Taverna", "🍺", "Ponto de encontro", tavern.id(), true), master);
        live.addPin(sceneId, new PinRequest(900.0, 600.0, "Armadilha", "⚠", "Fosso de 3 m", null, false), master);

        assertEquals(2, live.readScene(sceneId, master).pins().size());
        var seenByAna = live.readScene(sceneId, ana).pins();
        assertEquals(1, seenByAna.size(), "alfinete oculto fica so com o mestre");
        assertEquals(tavern.id(), seenByAna.getFirst().entryId());
        assertEquals("O dono deve dinheiro ao barão.", encyclopediaService.read(seenByAna.getFirst().entryId(), ana).information());

        assertThrows(AccessDeniedException.class, () -> live.addPin(sceneId, new PinRequest(0.0, 0.0, "X", null, null, null, true), ana));
        assertThrows(ResponseStatusException.class, () -> live.addPin(sceneId, new PinRequest(0.0, 0.0, " ", null, null, null, true), master));

        PinResponse moved = live.updatePin(pin.id(), new PinRequest(350.0, 210.0, null, null, null, null, null), master);
        assertEquals(350, moved.x());
        assertEquals("Taverna", moved.label(), "campo vazio fica como esta");
        live.deletePin(pin.id(), master);
        assertEquals(1, live.readScene(sceneId, master).pins().size());
    }

    @Test
    void everyonePingsButOnlyTheMasterPullsTheView() {
        live.ping(sceneId, new PingRequest(100.0, 100.0, false), ana);
        live.ping(sceneId, new PingRequest(100.0, 100.0, true), master);
        assertThrows(AccessDeniedException.class, () -> live.ping(sceneId, new PingRequest(100.0, 100.0, true), ana));
        assertThrows(ResponseStatusException.class, () -> live.ping(sceneId, new PingRequest(null, 100.0, false), ana));
    }

    @Test
    void pinsTravelInTheExportLinkedToTheEntryByName() {
        var tavern = encyclopediaService.create(campaign.getId(),
                new EncyclopediaEntryRequest("Taverna do Javali", "Texto", EntryType.PLACE, true, null, null), master);
        live.addPin(sceneId, new PinRequest(300.0, 200.0, "Taverna", "🍺", null, tavern.id(), true), master);
        sceneRepository.flush();
        CampaignImportResponse imported = transfer.importCampaign(transfer.export(campaign.getId(), true, master), master);
        var scene = sceneRepository.findByCampaignOrderByName(campaignRepository.findById(imported.campaignId()).orElseThrow()).getFirst();
        var pin = scene.getPins().getFirst();
        assertEquals("Taverna", pin.getLabel());
        assertNotEquals(tavern.id(), pin.getEntryId(), "ligado ao verbete da campanha nova");
        assertTrue(pin.getEntryId() != null);
        assertEquals("Taverna do Javali", encyclopediaService.read(pin.getEntryId(), master).entryName());
    }
}
