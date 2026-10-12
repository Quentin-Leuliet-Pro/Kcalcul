package fr.equilibre.app;

import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import fr.equilibre.app.HealthMath.Entry;
import static org.junit.Assert.*;

public class HealthMathTest {
    @Test public void sexCoefficientChangesTheCalorieEstimate() {
        assertEquals(1978.5,HealthMath.calorieLimit(70,175,30,ActivityLevel.SEDENTARY,"Homme"),.00001);
        assertEquals(1779.3,HealthMath.calorieLimit(70,175,30,ActivityLevel.SEDENTARY,"Femme"),.00001);
        assertEquals(2038.78125,HealthMath.calorieLimit(70,175,30,ActivityLevel.LIGHT,"Femme"),.00001);
        for(String gender:new String[]{null,"","Non renseigné"})
            assertThrows(IllegalArgumentException.class,()->HealthMath.calorieLimit(70,175,30,ActivityLevel.SEDENTARY,gender));
    }
    @Test public void fatRatioAndSurplusNeverReportPositiveLoss() {
        assertEquals(1,HealthMath.estimatedFatLossKg(7700),0);
        assertEquals(.1,HealthMath.estimatedFatLossKg(770),.000001);
        assertEquals(0,HealthMath.estimatedFatLossKg(-500),0);
        assertEquals(0,HealthMath.estimatedFatLossKg(0),0);
    }

    @Test public void accumulatedDeficitOffsetsSurplusAndExcludesUntrackedOpenAndFutureDays() {
        LocalDate today=LocalDate.parse("2026-01-04");
        HealthMath.Profile p=new HealthMath.Profile("Homme",30,175,70);
        List<Entry> entries=List.of(
            new Entry(1,"calories",1478.5,LocalDateTime.parse("2026-01-01T12:00")),
            new Entry(2,"calories",2178.5,LocalDateTime.parse("2026-01-02T12:00")),
            new Entry(3,"weight",70,LocalDateTime.parse("2026-01-03T12:00")),
            new Entry(4,"calories",978.5,LocalDateTime.parse("2026-01-04T12:00")),
            new Entry(5,"calories",100,LocalDateTime.parse("2026-01-05T12:00")));
        HealthMath.DeficitSummary net=HealthMath.accumulatedDeficit(entries,today.plusDays(1),today,p);
        assertEquals(2,net.days); assertEquals(300,net.kcal,.00001);
        assertEquals(500,HealthMath.accumulatedDeficit(entries,today.minusDays(3),today,p).kcal,.00001);
        assertFalse(HealthMath.hasCalories(entries,today.minusDays(1)));
        assertEquals(0,HealthMath.accumulatedDeficit(List.of(),today,today,p).days);
    }

    @Test public void accumulatedEstimateRecalculatesAfterDeletionAndProfileChanges() {
        LocalDate today=LocalDate.parse("2026-01-03");
        HealthMath.Profile p=new HealthMath.Profile("Homme",30,175,70);
        List<Entry> entries=List.of(new Entry(1,"calories",1500,today.minusDays(1).atTime(12,0)));
        assertEquals(478.5,HealthMath.accumulatedDeficit(entries,today,today,p).kcal,.00001);
        HealthMath.Profile updated=new HealthMath.Profile("Homme",30,175,70,ActivityLevel.MODERATE);
        assertEquals(1055.5625,HealthMath.accumulatedDeficit(entries,today,today,updated).kcal,.00001);
        assertEquals(0,HealthMath.accumulatedDeficit(List.of(),today,today,updated).kcal,0);
    }
    @Test public void exactRequestedFormula() {
        assertEquals(1978.5,HealthMath.calorieLimit(70,175,30,ActivityLevel.SEDENTARY,"Homme"),.00001);
    }
    @Test public void allFiveActivityFactorsUseTheProvidedValues() {
        ActivityLevel[] levels=ActivityLevel.values();
        double[] expected={1978.5,2267.03125,2555.5625,2844.09375,3297.5};
        for(int i=0;i<levels.length;i++) assertEquals(expected[i],HealthMath.calorieLimit(70,175,30,levels[i],"Homme"),.00001);
    }
    @Test public void dailyLimitUsesNewWeightAndChangedProfileWithNoCachedTarget() {
        LocalDate day=LocalDate.parse("2026-01-02");
        HealthMath.Profile initial=new HealthMath.Profile("Homme",30,175,70,ActivityLevel.LIGHT);
        List<Entry> entries=List.of(new Entry(1,"weight",80,day.atTime(8,0)));
        assertEquals(2267.03125,HealthMath.dailyLimit(List.of(),day,initial),.00001);
        assertEquals(2404.53125,HealthMath.dailyLimit(entries,day,initial),.00001);
        assertEquals(2267.03125,HealthMath.dailyLimit(entries,day.minusDays(1),initial),.00001);
        HealthMath.Profile changed=new HealthMath.Profile("Homme",31,180,70,ActivityLevel.MODERATE);
        assertEquals(2751.25,HealthMath.dailyLimit(entries,day,changed),.00001);
    }
    @Test public void commaDecimalAndWhitespace() {
        assertEquals(65.4,HealthMath.parsePositive(" 65,4 ",20,500),0);
    }
    @Test public void invalidNumbersAreRejected() {
        for(String value:List.of("", "NaN", "Infinity", "-1", "0", "99999", "abc")) {
            assertThrows(IllegalArgumentException.class,()->HealthMath.parsePositive(value,1,30000));
        }
    }
    @Test public void dayBoundariesDoNotMixCaloriesOrWeight() {
        List<Entry> entries=List.of(
            new Entry(1,"calories",500,LocalDateTime.parse("2026-01-01T23:59")),
            new Entry(2,"calories",250,LocalDateTime.parse("2026-01-02T00:00")),
            new Entry(3,"calories",350,LocalDateTime.parse("2026-01-02T12:30")),
            new Entry(4,"weight",70,LocalDateTime.parse("2026-01-02T09:00")));
        assertEquals(500,HealthMath.totalCalories(entries,LocalDate.parse("2026-01-01")),0);
        assertEquals(600,HealthMath.totalCalories(entries,LocalDate.parse("2026-01-02")),0);
        assertEquals(0,HealthMath.totalCalories(entries,LocalDate.parse("2026-01-03")),0);
    }
    @Test public void historicalBudgetDoesNotUseFutureWeight() {
        List<Entry> entries=List.of(
            new Entry(3,"weight",68,LocalDateTime.parse("2026-01-03T09:00")),
            new Entry(1,"weight",70,LocalDateTime.parse("2026-01-01T09:00")),
            new Entry(2,"weight",69,LocalDateTime.parse("2026-01-02T09:00")));
        assertEquals(71,HealthMath.weightAt(entries,LocalDate.parse("2025-12-31"),71),0);
        assertEquals(70,HealthMath.weightAt(entries,LocalDate.parse("2026-01-01"),71),0);
        assertEquals(69,HealthMath.weightAt(entries,LocalDate.parse("2026-01-02"),71),0);
    }
    @Test public void latestWeightInSameMinuteIsDeterministic() {
        LocalDateTime time=LocalDateTime.parse("2026-01-01T09:00");
        List<Entry> entries=List.of(new Entry(1,"weight",70,time),new Entry(2,"weight",69.5,time));
        assertEquals(69.5,HealthMath.weightAt(entries,time.toLocalDate(),71),0);
    }
}
