package fr.equilibre.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import fr.equilibre.app.HealthMath.Entry;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class TrendViewTest {
    @Test public void missingDaysRemainMissingAndLastDailyWeightWins() {
        LocalDate end=LocalDate.parse("2026-01-07");
        TrendView chart=new TrendView(RuntimeEnvironment.getApplication(),List.of(
            new Entry(1,"calories",500,LocalDateTime.parse("2026-01-07T12:00")),
            new Entry(2,"calories",700,LocalDateTime.parse("2026-01-07T19:00")),
            new Entry(3,"weight",70,LocalDateTime.parse("2026-01-07T08:00")),
            new Entry(4,"weight",69.5,LocalDateTime.parse("2026-01-07T09:00"))),end,7);
        assertTrue(chart.hasRecords());
        assertTrue(chart.detail(6).contains("1 200 kcal") || chart.detail(6).contains("1 200 kcal") || chart.detail(6).contains("1 200 kcal"));
        assertTrue(chart.detail(6).contains("69,5 kg"));
        assertTrue(chart.detail(5).contains("kcal non renseignées"));
        assertTrue(chart.detail(5).contains("poids non renseigné"));
    }
}
