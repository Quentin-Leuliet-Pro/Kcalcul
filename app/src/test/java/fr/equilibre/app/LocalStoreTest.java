package fr.equilibre.app;

import android.content.Context;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.LocalDateTime;
import fr.equilibre.app.HealthMath.Profile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class LocalStoreTest {
    @Test public void unknownGenderCannotBeSavedAndFemaleCoefficientPersists() {
        assertThrows(IllegalArgumentException.class,()->store.saveProfile(new Profile("Non renseigné",30,175,70),time));
        assertNull(store.profile()); assertTrue(store.entries().isEmpty());
        store.saveProfile(new Profile("Femme",30,175,70),time); store.close(); store=new LocalStore(context);
        assertEquals("Femme",store.profile().gender);
        assertEquals(1779.3,HealthMath.dailyLimit(store.entries(),time.toLocalDate(),store.profile()),.00001);
    }
    Context context;
    LocalStore store;
    final LocalDateTime time=LocalDateTime.parse("2025-01-01T12:30");
    @Before public void setup() { context=RuntimeEnvironment.getApplication(); context.deleteDatabase("equilibre.db");store=new LocalStore(context); }
    @After public void cleanup() {store.close();}

    @Test public void profileAndRecordsSurviveDatabaseReopening() {
        store.saveProfile(new Profile("Homme",30,175,70),time);
        store.saveEntry(-1,"calories",450,time.plusHours(1)); store.close(); store=new LocalStore(context);
        assertEquals("Homme",store.profile().gender); assertEquals(2,store.entries().size());
        assertEquals(450,HealthMath.totalCalories(store.entries(),time.toLocalDate()),0);
    }
    @Test public void updatingProfileDoesNotDuplicateOrRewriteInitialWeight() {
        store.saveProfile(new Profile("Homme",30,175,70),time);
        store.saveProfile(new Profile("Homme",31,176,70),time.plusDays(1));
        assertEquals(1,store.entries().size()); assertEquals(time,store.entries().get(0).at);
        assertEquals(31,store.profile().age);
    }
    @Test public void editTimeMovesCaloriesToCorrectDayAndDeleteRemovesIt() {
        long id=store.saveEntry(-1,"calories",450,time);
        store.saveEntry(id,"calories",500,time.minusDays(1).withHour(23).withMinute(59));
        assertEquals(0,HealthMath.totalCalories(store.entries(),time.toLocalDate()),0);
        assertEquals(500,HealthMath.totalCalories(store.entries(),time.minusDays(1).toLocalDate()),0);
        store.deleteEntry(id); assertTrue(store.entries().isEmpty());
    }
    @Test public void failedInitialWeightRollsBackProfile() {
        assertThrows(IllegalArgumentException.class,()->store.saveProfile(new Profile("Homme",30,175,70),LocalDateTime.now().plusDays(1)));
        assertNull(store.profile()); assertTrue(store.entries().isEmpty());
    }
    @Test public void rejectsInvalidWeightAndFutureDates() {
        assertThrows(IllegalArgumentException.class,()->store.saveEntry(-1,"weight",Double.NaN,time));
        assertThrows(IllegalArgumentException.class,()->store.saveEntry(-1,"calories",450,LocalDateTime.now().plusDays(1)));
        assertTrue(store.entries().isEmpty());
    }

    @Test public void activityChoiceSurvivesReopening() {
        store.saveProfile(new Profile("Homme",30,175,70,ActivityLevel.EXTREME),time);
        store.close(); store=new LocalStore(context);
        assertEquals(ActivityLevel.EXTREME,store.profile().activity);
        assertEquals(3297.5,HealthMath.dailyLimit(store.entries(),time.toLocalDate(),store.profile()),.00001);
    }

    @Test public void versionOneUpgradePreservesProfileAndHistory() {
        try(android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase("equilibre.db",0,null)) {
            old.execSQL("CREATE TABLE profile (id INTEGER PRIMARY KEY, gender TEXT NOT NULL, age INTEGER NOT NULL, height REAL NOT NULL, initial_weight REAL NOT NULL)");
            old.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, value REAL NOT NULL, occurred_at TEXT NOT NULL)");
            old.execSQL("INSERT INTO profile VALUES(1,'Homme',30,175,70)");
            old.execSQL("INSERT INTO entries VALUES(10,'weight',70,'2025-01-01T12:30')");
            old.execSQL("INSERT INTO entries VALUES(11,'calories',450,'2025-01-01T13:30')");
            old.setVersion(1);
        }
        assertEquals(ActivityLevel.SEDENTARY,store.profile().activity);
        assertEquals("Homme",store.profile().gender);
        assertEquals(2,store.entries().size()); assertEquals(11,store.entries().get(0).id);
        assertEquals(450,HealthMath.totalCalories(store.entries(),time.toLocalDate()),0);
        assertEquals(1978.5,HealthMath.dailyLimit(store.entries(),time.toLocalDate(),store.profile()),.00001);
        store.saveProfile(new Profile("Homme",30,175,70,ActivityLevel.MODERATE),time);
        assertEquals(2,store.entries().size());
        assertEquals(2555.5625,HealthMath.dailyLimit(store.entries(),time.toLocalDate(),store.profile()),.00001);
    }
}
