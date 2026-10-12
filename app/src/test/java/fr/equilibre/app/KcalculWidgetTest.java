package fr.equilibre.app;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="fr-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KcalculWidgetTest {
    @Before public void reset() { RuntimeEnvironment.getApplication().deleteDatabase("equilibre.db"); }

    private void profile() {
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())) {
            store.saveProfile(new HealthMath.Profile("Homme",30,175,70),LocalDateTime.now().minusDays(1));
        }
    }

    private Intent tap(int button) {
        Application context=RuntimeEnvironment.getApplication();
        View widget=KcalculWidget.views(context).apply(context,new FrameLayout(context));
        assertTrue(widget.findViewById(button).performClick());
        Intent intent=Shadows.shadowOf(context).getNextStartedActivity();
        assertNotNull(intent); assertEquals(MainActivity.class.getName(),intent.getComponent().getClassName());
        return intent;
    }

    @Test @Config(sdk={26,36}) public void bothRemoteViewButtonsOpenAndPersistTheCorrectEntry() {
        profile();
        int[] buttons={R.id.widget_add_calories,R.id.widget_add_weight};
        String[] actions={KcalculWidget.ADD_CALORIES,KcalculWidget.ADD_WEIGHT};
        String[] kinds={"calories","weight"};
        String[] values={"450","69,5"};
        for(int i=0;i<buttons.length;i++) {
            Intent intent=tap(buttons[i]); assertEquals(actions[i],intent.getAction());
            try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,intent).setup()) {
                Dialog editor=ShadowDialog.getLatestDialog(); assertTrue(editor.isShowing());
                ((EditText)editor.findViewById(R.id.entry_value)).setText(values[i]);
                editor.findViewById(R.id.entry_save).performClick(); assertFalse(editor.isShowing());
                try(LocalStore store=new LocalStore(controller.get())) {
                    boolean found=false;
                    for(HealthMath.Entry entry:store.entries()) if(entry.kind.equals(kinds[i]) && entry.at.toLocalDate().equals(LocalDate.now())) {
                        assertEquals(i==0?450:69.5,entry.value,0); found=true;
                    }
                    assertTrue(found);
                }
            }
        }
    }

    @Test public void warmLaunchUsesTodayAndRotationDoesNotReopenASavedEntry() {
        profile();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.nav_evolution).performClick();
            controller.newIntent(tap(R.id.widget_add_calories));
            Dialog editor=ShadowDialog.getLatestDialog(); assertTrue(editor.isShowing());
            ((EditText)editor.findViewById(R.id.entry_value)).setText("620");
            controller.recreate();
            editor=ShadowDialog.getLatestDialog(); assertTrue(editor.isShowing());
            assertEquals("620",((EditText)editor.findViewById(R.id.entry_value)).getText().toString());
            editor.findViewById(R.id.entry_save).performClick();
            controller.recreate(); assertFalse(ShadowDialog.getLatestDialog().isShowing());
            try(LocalStore store=new LocalStore(controller.get())) {
                assertEquals(620,HealthMath.totalCalories(store.entries(),LocalDate.now()),0);
            }
        }
    }

    @Test public void widgetWaitsForProfileAndPreservesTheRequestedActionAcrossRotation() {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,tap(R.id.widget_add_weight)).setup()) {
            assertNotNull(controller.get().findViewById(R.id.save_profile)); controller.recreate();
            MainActivity activity=controller.get();
            ((EditText)activity.findViewById(R.id.age_input)).setText("30");
            ((EditText)activity.findViewById(R.id.height_input)).setText("175");
            ((EditText)activity.findViewById(R.id.weight_input)).setText("70");
            findText(activity.getWindow().getDecorView(),"Homme").performClick();
            activity.findViewById(R.id.save_profile).performClick();
            Dialog editor=ShadowDialog.getLatestDialog(); assertTrue(editor.isShowing());
            assertNotNull(findText(editor.getWindow().getDecorView(),"Poids"));
        }
    }

    @Test public void capturesWidgetAndChecksFourByTwoSize() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        try(android.content.res.XmlResourceParser xml=context.getResources().getXml(R.xml.kcalcul_widget_info)) {
            while(xml.next()!=org.xmlpull.v1.XmlPullParser.START_TAG) { }
            String ns="http://schemas.android.com/apk/res/android";
            assertEquals(4,xml.getAttributeIntValue(ns,"targetCellWidth",0));
            assertEquals(2,xml.getAttributeIntValue(ns,"targetCellHeight",0));
        }
        View widget=KcalculWidget.views(context).apply(context,new FrameLayout(context));
        widget.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));
        widget.layout(0,0,960,480); widget.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        invalidateTree(widget);
        Bitmap bitmap=Bitmap.createBitmap(960,480,Bitmap.Config.ARGB_8888); widget.draw(new Canvas(bitmap));
        File directory=new File("../artifacts/previews"); assertTrue(directory.isDirectory() || directory.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(directory,"widget.png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
        }
    }

    private void invalidateTree(View view) {
        view.invalidate();
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            invalidateTree(((ViewGroup)view).getChildAt(i));
        }
    }

    private TextView findText(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return (TextView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            TextView found=findText(((ViewGroup)view).getChildAt(i),text); if(found!=null) return found;
        }
        return null;
    }
}
