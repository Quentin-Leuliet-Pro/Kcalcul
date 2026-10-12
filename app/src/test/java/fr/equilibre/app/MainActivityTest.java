package fr.equilibre.app;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import fr.equilibre.app.HealthMath.Profile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="fr-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    @Before public void setup() {RuntimeEnvironment.getApplication().deleteDatabase("equilibre.db");}

    @Test public void onboardingRequiresAnExplicitChoiceAndChangingSexRecalculates() {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            assertNull(findText(activity.getWindow().getDecorView(),"Non renseigné"));
            ((EditText)activity.findViewById(R.id.age_input)).setText("30");
            ((EditText)activity.findViewById(R.id.height_input)).setText("175");
            ((EditText)activity.findViewById(R.id.weight_input)).setText("70");
            activity.findViewById(R.id.save_profile).performClick();
            assertNull(activity.findViewById(R.id.calorie_balance));
            try(LocalStore store=new LocalStore(activity)) {assertNull(store.profile());}
            findText(activity.getWindow().getDecorView(),"Femme").performClick();
            activity.findViewById(R.id.save_profile).performClick(); assertBalance(activity,"1779");
            activity.findViewById(R.id.profile_button).performClick();
            findText(activity.getWindow().getDecorView(),"Homme").performClick();
            activity.findViewById(R.id.save_profile).performClick(); assertBalance(activity,"1979");
        }
    }

    @Test public void existingUnknownGenderRequiresCompletionWithoutLosingHistory() {
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())) {
            store.saveProfile(new Profile("Homme",30,175,70),LocalDateTime.now().minusDays(1));
            store.saveEntry(-1,"calories",450,LocalDateTime.now());
            store.getWritableDatabase().execSQL("UPDATE profile SET gender='Non renseigné'");
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            assertNull(activity.findViewById(R.id.calorie_balance)); assertNotNull(activity.findViewById(R.id.save_profile));
            assertNull(findText(activity.getWindow().getDecorView(),"Non renseigné"));
            findText(activity.getWindow().getDecorView(),"Femme").performClick();
            activity.findViewById(R.id.save_profile).performClick(); assertBalance(activity,"1329");
            try(LocalStore store=new LocalStore(activity)) {assertEquals(2,store.entries().size());assertEquals("Femme",store.profile().gender);}
        }
    }

    @Test @Config(sdk={26,36}) public void onboardingAndCalorieEntryPersistAndReopen() {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            ((EditText)activity.findViewById(R.id.age_input)).setText("30");
            ((EditText)activity.findViewById(R.id.height_input)).setText("175");
            ((EditText)activity.findViewById(R.id.weight_input)).setText("70,0");
            findText(activity.getWindow().getDecorView(),"Homme").performClick();
            activity.findViewById(R.id.activity_choice).performClick();
            Dialog choices=ShadowDialog.getLatestDialog();
            TextView light=findText(choices.getWindow().getDecorView(),"Légèrement actif  ·  1,375");
            assertNotNull(light); ((View)light.getParent()).performClick();
            activity.findViewById(R.id.save_profile).performClick();
            assertNotNull(activity.findViewById(R.id.add_calories));
            activity.findViewById(R.id.add_calories).performClick(); Dialog dialog=ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing()); ((EditText)dialog.findViewById(R.id.entry_value)).setText("450");
            dialog.findViewById(R.id.entry_save).performClick();
            assertFalse(dialog.isShowing());
            try(LocalStore store=new LocalStore(activity)) {
                assertEquals(450,HealthMath.totalCalories(store.entries(),LocalDate.now()),0);
                assertEquals(ActivityLevel.LIGHT,store.profile().activity);
                assertEquals(LocalDate.now(),store.entries().get(0).at.toLocalDate());
            }
        }
        try(ActivityController<MainActivity> reopened=Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNotNull(reopened.get().findViewById(R.id.add_calories));
            assertNull(reopened.get().findViewById(R.id.save_profile));
        }
    }

    @Test public void journalImmediatelyRecalculatesAfterWeightAndProfileChanges() {
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())) {
            store.saveProfile(new Profile("Homme",30,175,70,ActivityLevel.LIGHT),LocalDateTime.now().minusDays(1));
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            assertBalance(activity,"2267");
            activity.findViewById(R.id.add_weight).performClick(); Dialog weight=ShadowDialog.getLatestDialog();
            ((EditText)weight.findViewById(R.id.entry_value)).setText("80"); weight.findViewById(R.id.entry_save).performClick();
            assertBalance(activity,"2405");
            activity.findViewById(R.id.profile_button).performClick();
            ((EditText)activity.findViewById(R.id.age_input)).setText("31");
            ((EditText)activity.findViewById(R.id.height_input)).setText("180");
            activity.findViewById(R.id.activity_choice).performClick(); Dialog choices=ShadowDialog.getLatestDialog();
            TextView moderate=findText(choices.getWindow().getDecorView(),"Modérément actif  ·  1,55");
            assertNotNull(moderate); ((View)moderate.getParent()).performClick();
            activity.findViewById(R.id.save_profile).performClick(); assertBalance(activity,"2751");
        }
    }

    private void assertBalance(MainActivity activity,String expected) {
        assertEquals(expected,((TextView)activity.findViewById(R.id.calorie_balance)).getText().toString().replaceAll("\\D",""));
    }

    @Test public void dailyEstimateRequiresCaloriesAndUpdatesAfterFurtherIntake() {
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())) {
            store.saveProfile(new Profile("Homme",30,175,70),LocalDateTime.now().minusDays(1));
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            assertTrue(((TextView)activity.findViewById(R.id.daily_fat_estimate)).getText().toString().contains("non renseignées"));
            activity.findViewById(R.id.add_calories).performClick(); Dialog input=ShadowDialog.getLatestDialog();
            ((EditText)input.findViewById(R.id.entry_value)).setText("1500");input.findViewById(R.id.entry_save).performClick();
            assertTrue(((TextView)activity.findViewById(R.id.daily_fat_estimate)).getText().toString().contains("0,062 kg"));
            activity.findViewById(R.id.add_calories).performClick(); input=ShadowDialog.getLatestDialog();
            ((EditText)input.findViewById(R.id.entry_value)).setText("1000");input.findViewById(R.id.entry_save).performClick();
            assertTrue(((TextView)activity.findViewById(R.id.daily_fat_estimate)).getText().toString().contains("0,000 kg"));
        }
    }

    @Test public void recreationPreservesAnUnsubmittedEntry() {
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())) {
            store.saveProfile(new Profile("Homme",30,175,70),LocalDateTime.now().minusDays(1));
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.add_calories).performClick();
            Dialog dialog=ShadowDialog.getLatestDialog();
            ((EditText)dialog.findViewById(R.id.entry_value)).setText("620");
            controller.recreate();
            Dialog restored=ShadowDialog.getLatestDialog(); assertTrue(restored.isShowing());
            assertEquals("620",((EditText)restored.findViewById(R.id.entry_value)).getText().toString());
            restored.findViewById(R.id.entry_save).performClick();
            try(LocalStore store=new LocalStore(controller.get())) {
                assertEquals(620,HealthMath.totalCalories(store.entries(),LocalDate.now()),0);
            }
        }
    }

    @Test public void capturesEmptyOnboardingAndPopulatedJournal() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            screenshot(controller.get(),"onboarding.png");
        }
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())) {
            LocalDateTime start=LocalDate.now().minusDays(6).atTime(7,0);
            store.saveProfile(new Profile("Homme",30,175,70),start);
            int[] kcal={1850,1620,1980,1730,1910,1580};
            for(int i=0;i<6;i++) {
                store.saveEntry(-1,"calories",kcal[i],start.plusDays(i).withHour(20));
                store.saveEntry(-1,"weight",70-i*.12,start.plusDays(i).withHour(8));
            }
            // Three records for today's history; never create a timestamp later than the host's clock.
            long minutes=java.time.LocalTime.now().toSecondOfDay()/60;
            store.saveEntry(-1,"calories",420,LocalDate.now().atStartOfDay().plusMinutes(minutes/4));
            store.saveEntry(-1,"calories",560,LocalDate.now().atStartOfDay().plusMinutes(minutes/2));
            store.saveEntry(-1,"calories",280,LocalDate.now().atStartOfDay().plusMinutes(minutes*3/4));
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            screenshot(controller.get(),"journal.png");
            controller.get().findViewById(R.id.nav_journal).performClick();
            View history=controller.get().getWindow().getDecorView();
            history.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.EXACTLY));
            history.layout(0,0,1179,2556);
            android.widget.ScrollView historyScroll=findScroll(history); assertNotNull(historyScroll);
            View headerButton=controller.get().findViewById(R.id.profile_button);
            int[] beforeScroll=new int[2]; headerButton.getLocationOnScreen(beforeScroll);
            historyScroll.scrollTo(0,historyScroll.getChildAt(0).getHeight());
            int[] afterScroll=new int[2]; headerButton.getLocationOnScreen(afterScroll);
            assertTrue(historyScroll.getScrollY()>0);
            assertEquals(beforeScroll[1],afterScroll[1]);
            assertTrue(headerButton.getGlobalVisibleRect(new android.graphics.Rect()));
            screenshot(controller.get(),"journal-history.png");
            controller.get().findViewById(R.id.nav_evolution).performClick();
            screenshot(controller.get(),"evolution.png");
            controller.get().findViewById(R.id.profile_button).performClick();
            screenshot(controller.get(),"profile.png");
            controller.get().findViewById(R.id.activity_choice).performClick();
            Dialog levels=ShadowDialog.getLatestDialog();
            screenshotView(levels.getWindow().getDecorView(),"activity.png",false);
            levels.dismiss();
            controller.get().onBackPressed();
            controller.get().findViewById(R.id.add_calories).performClick();
            Dialog entry=ShadowDialog.getLatestDialog();
            screenshotView(entry.getWindow().getDecorView(),"entry.png",false);
            entry.dismiss();
            controller.get().findViewById(R.id.nav_evolution).performClick();
            View decor=controller.get().getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.EXACTLY));
            decor.layout(0,0,1179,2556);
            android.widget.ScrollView scroll=findScroll(decor); assertNotNull(scroll);
            scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
            screenshot(controller.get(),"deficit.png");
        }
    }

    private TextView findText(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return (TextView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            TextView found=findText(((ViewGroup)view).getChildAt(i),text); if(found!=null) return found;
        }
        return null;
    }

    private android.widget.ScrollView findScroll(View view) {
        if(view instanceof android.widget.ScrollView) return (android.widget.ScrollView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            android.widget.ScrollView found=findScroll(((ViewGroup)view).getChildAt(i)); if(found!=null) return found;
        }
        return null;
    }

    private void screenshot(MainActivity activity,String name) throws Exception {
        screenshotView(activity.getWindow().getDecorView(),name,true);
    }

    private void screenshotView(View decor,String name,boolean fullScreen) throws Exception {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        decor.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        invalidateTree(decor);
        decor.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,fullScreen?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));
        int height=decor.getMeasuredHeight(); decor.layout(0,0,1179,height);
        if(fullScreen && !name.equals("journal-history.png") && !name.equals("deficit.png")) {
            android.widget.ScrollView scroll=findScroll(decor);
            if(scroll!=null) scroll.scrollTo(0,0);
            invalidateTree(decor);
        }
        Bitmap bitmap=Bitmap.createBitmap(1179,height,Bitmap.Config.ARGB_8888); decor.draw(new Canvas(bitmap));
        File directory=new File("../artifacts/previews"); assertTrue(directory.mkdirs() || directory.isDirectory());
        try(FileOutputStream output=new FileOutputStream(new File(directory,name))) {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
        bitmap.recycle();
    }

    private void invalidateTree(View view) {
        view.invalidate();
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) invalidateTree(((ViewGroup)view).getChildAt(i));
    }
}
