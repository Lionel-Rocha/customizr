package com.vttcaseiro.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Relogio do calendario, eventos marcados, viagem e luz pela hora. */
class CampaignClockTest {

    @Test
    void clockRollsOverDaysAndSaysTheLight() {
        Campaign.Calendar c = new Campaign.Calendar();
        assertEquals("08:00", c.timeText());
        assertEquals("dia", c.lightPeriod());
        c.advanceMinutes(10 * 60 + 30); // 18:30
        assertEquals("18:30", c.timeText());
        assertEquals("penumbra", c.lightPeriod());
        c.advanceMinutes(8 * 60); // 02:30 do dia seguinte
        assertEquals(1, c.day);
        assertEquals("02:30", c.timeText());
        assertEquals("noite", c.lightPeriod());
    }

    @Test
    void eventsAreDueOnceWhenTimePassesThem() {
        Campaign.Calendar c = new Campaign.Calendar();
        Campaign.CalendarEvent festival = new Campaign.CalendarEvent();
        festival.day = c.dayOf(3, 1, 1); // 3 de Janeiro do ano 1
        festival.minute = 20 * 60;
        festival.title = "Festival da colheita";
        c.events.add(festival);
        assertEquals(2, festival.day);

        long from = c.absoluteMinute();
        c.advanceMinutes(1440); // 2 de janeiro: ainda não
        assertTrue(c.due(from, c.absoluteMinute()).isEmpty());
        from = c.absoluteMinute();
        c.advanceMinutes(1440); // 3 de janeiro 08:00: ainda não (é às 20h)
        assertTrue(c.due(from, c.absoluteMinute()).isEmpty());
        from = c.absoluteMinute();
        c.advanceMinutes(12 * 60);
        assertEquals(List.of(festival), c.due(from, c.absoluteMinute()));
        festival.done = true;
        assertTrue(c.due(0, c.absoluteMinute()).isEmpty(), "avisado uma vez só");
        assertEquals(-1, c.dayOf(31, 1, 1), "mês de 30 dias");
    }

    @Test
    void travelTimeAndHiddenEvents() {
        assertEquals(24.0 / 4.5, Campaign.travelHours(24, 1, false), 1e-9);
        assertEquals(16, Campaign.travelHours(24, 0, true), 1e-9, "lento em terreno difícil");
        assertEquals("2 dias", Campaign.travelText(16));
        assertEquals("1 dia e 2,5 h", Campaign.travelText(10.5));

        Campaign camp = new Campaign();
        Campaign.CalendarEvent secret = new Campaign.CalendarEvent();
        secret.title = "Emboscada dos cultistas";
        Campaign.CalendarEvent pub = new Campaign.CalendarEvent();
        pub.title = "Torneio";
        pub.visible = true;
        pub.notes = "o campeão é um vampiro";
        camp.getCalendar().events.add(secret);
        camp.getCalendar().events.add(pub);
        Campaign p = camp.publicCopy();
        assertEquals(1, p.getCalendar().events.size());
        assertEquals("Torneio", p.getCalendar().events.get(0).title);
        assertEquals("", p.getCalendar().events.get(0).notes, "nota do evento é do mestre");
    }
}
