package fr.equilibre.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import fr.equilibre.app.HealthMath.Entry;
import fr.equilibre.app.HealthMath.Profile;
import static fr.equilibre.app.Ui.*;

public final class MainActivity extends Activity {
    private static final Locale FR = Locale.FRANCE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm",FR);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy",FR);
    private LocalStore store;
    private Profile profile;
    private List<Entry> entries;
    private LocalDate day = LocalDate.now(), lastToday = LocalDate.now();
    private String page = "journal", gender = "";
    private TextView genderError;
    private ActivityLevel activityLevel = ActivityLevel.SEDENTARY;
    private TextView activityLabel, activityDescription;
    private int range = 7;
    private LinearLayout root, body;
    private EditText ageField, heightField, weightField;
    private Bundle restored;
    private Dialog editor;
    private EditText editorAmount;
    private String editorKind;
    private String pendingWidgetEntry;
    private long editorId;
    private LocalDateTime[] editorTime;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable midnight = new Runnable() {
        @Override public void run() {
            refreshDay(); handler.postDelayed(this,60000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new LocalStore(this); restored=state;
        if(state!=null) {
            day=LocalDate.parse(state.getString("day",LocalDate.now().toString()));
            page=state.getString("page","journal"); range=state.getInt("range",7);
        }
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if(android.os.Build.VERSION.SDK_INT>=33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{
                if(profile!=null && HealthMath.hasKnownGender(profile.gender) && !page.equals("journal")) {page="journal";render();} else finish();
            });
        }
        pendingWidgetEntry=state==null?widgetKind(getIntent()):state.getString("pendingWidgetEntry");
        setIntent(new Intent(getIntent()).setAction(null));
        render();
        openPendingWidgetEntry();
        if(state!=null && state.containsKey("editorKind") && profile!=null && HealthMath.hasKnownGender(profile.gender)) {
            String kind=state.getString("editorKind"); long id=state.getLong("editorId",-1);
            Entry entry=null; for(Entry e:entries) if(e.id==id) entry=e;
            if(id<0 || entry!=null) {
                showEntry(kind,entry);
                editorAmount.setText(state.getString("editorAmount",""));
                editorTime[0]=LocalDateTime.parse(state.getString("editorTime"));
                updateEditorTime();
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(new Intent(intent).setAction(null));
        String kind=widgetKind(intent);
        if(kind==null) return;
        if(editor!=null) editor.dismiss();
        pendingWidgetEntry=kind;
        page="journal"; day=LocalDate.now(); render();
        openPendingWidgetEntry();
    }

    private static String widgetKind(Intent intent) {
        if(KcalculWidget.ADD_CALORIES.equals(intent.getAction())) return "calories";
        if(KcalculWidget.ADD_WEIGHT.equals(intent.getAction())) return "weight";
        return null;
    }

    private void openPendingWidgetEntry() {
        if(pendingWidgetEntry==null || profile==null || !HealthMath.hasKnownGender(profile.gender)) return;
        String kind=pendingWidgetEntry; pendingWidgetEntry=null;
        day=LocalDate.now(); page="journal"; render(); showEntry(kind,null);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pendingWidgetEntry",pendingWidgetEntry);
        state.putString("day",day.toString()); state.putString("page",page); state.putInt("range",range);
        if(ageField!=null && (profile==null || page.equals("profile"))) {
            state.putString("gender",gender); state.putString("age",ageField.getText().toString());
            state.putString("activity",activityLevel.name());
            state.putString("height",heightField.getText().toString());
            if(weightField!=null) state.putString("weight",weightField.getText().toString());
        }
        if(editor!=null && editor.isShowing()) {
            state.putString("editorKind",editorKind); state.putLong("editorId",editorId);
            state.putString("editorAmount",editorAmount.getText().toString()); state.putString("editorTime",editorTime[0].toString());
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() { super.onResume(); refreshDay(); handler.postDelayed(midnight,60000); }
    @Override protected void onPause() { handler.removeCallbacks(midnight); super.onPause(); }
    @Override protected void onDestroy() { if(editor!=null) editor.dismiss(); store.close(); super.onDestroy(); }
    private void refreshDay() {
        LocalDate today=LocalDate.now();
        if(!today.equals(lastToday)) {
            if(day.equals(lastToday)) day=today;
            lastToday=today;
            if(profile!=null && !page.equals("profile") && editor==null) render();
        }
    }

    private void render() {
        profile=store.profile(); entries=store.entries();
        root=column(this); root.setBackgroundColor(BG); root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom,left,right;
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                top=bars.top; bottom=Math.max(bars.bottom,ime.bottom); left=bars.left; right=bars.right;
            } else {
                top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom();
                left=insets.getSystemWindowInsetLeft(); right=insets.getSystemWindowInsetRight();
            }
            view.setPadding(left,top,right,bottom); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout frame=column(this); frame.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        if(profile!=null && !HealthMath.hasKnownGender(profile.gender)) page="profile";
        boolean profileScreen=profile==null || page.equals("profile");
        LinearLayout fixedHeader=column(this); pad(fixedHeader,24,12,24,0);
        LinearLayout.LayoutParams headerParams=new LinearLayout.LayoutParams(-1,-2);
        // Use the same width for the fixed header and the scrolling content.
        if(getResources().getConfiguration().screenWidthDp>600) headerParams.width=dp(this,560);
        frame.addView(fixedHeader,headerParams);
        if(profileScreen) profileHeader(fixedHeader,profile==null); else header(fixedHeader);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        body=column(this); pad(body,24,profileScreen?20:12,24,24);
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams viewport=new LinearLayout.LayoutParams(-1,0,1);
        // Keep the reading width comfortable on tablets, while allowing the phone to fill its screen.
        if(getResources().getConfiguration().screenWidthDp>600) viewport.width=dp(this,560);
        frame.addView(scroll,viewport);
        if(profileScreen) { profilePage(profile==null); }
        else {
            if(page.equals("evolution")) evolution(); else journal();
            navigation();
        }
    }

    private void header(LinearLayout container) {
        LinearLayout row=row(this);
        LinearLayout logo=row(this); Icon leaf=new Icon(this,"mark",ACCENT); background(leaf,SURFACE,14);
        pad(leaf,7,7,7,7); logo.addView(leaf,new LinearLayout.LayoutParams(dp(this,36),dp(this,36)));
        TextView brand=text(this,"Kcalcul",21,INK,true); pad(brand,10,0,0,0); logo.addView(brand); grow(row,logo);
        View profileButton=iconButton("profile","Modifier mon profil",SURFACE,()->{page="profile";render();}); profileButton.setId(R.id.profile_button);
        row.addView(profileButton); container.addView(row);
    }

    private void journal() {
        dateControl(); gap(body,20); calorieCard(); gap(body,12);
        TextView add=button(this,"+   Ajouter des kcal",ACCENT,()->showEntry("calories",null)); add.setId(R.id.add_calories); body.addView(add);
        gap(body,24);
        sectionTitle("Historique des kcal",null,null); gap(body,4);
        body.addView(text(this,"Touchez une saisie pour la modifier.",12,MUTED,false)); gap(body,14);
        boolean any=false; LinearLayout list=column(this); background(list,SURFACE,22); pad(list,16,0,16,0);
        for(Entry e:entries) if(e.kind.equals("calories") && e.at.toLocalDate().equals(day)) {
            if(any) divider(list);
            entryRow(list,e); any=true;
        }
        if(any) body.addView(list);
        else {
            LinearLayout empty=column(this); background(empty,SURFACE,22); pad(empty,22,26,22,26);
            empty.addView(text(this,"Aucune saisie",17,INK,true)); gap(empty,7);
            empty.addView(text(this,"Ajoutez les kcal consommées pour cette journée.",13,MUTED,false)); body.addView(empty);
        }
        gap(body,20); weightCard();
        LinearLayout weights=column(this); background(weights,SURFACE,22); pad(weights,16,0,16,0);
        boolean hasWeights=false;
        for(Entry e:entries) if(e.kind.equals("weight") && e.at.toLocalDate().equals(day)) {
            if(hasWeights) divider(weights);
            entryRow(weights,e); hasWeights=true;
        }
        if(hasWeights) {gap(body,20); sectionTitle("Mesures de poids",null,null); gap(body,10); body.addView(weights);}
    }

    private void dateControl() {
        LinearLayout dates=row(this); dates.addView(iconButton("left","Jour précédent",SURFACE,()->{day=day.minusDays(1);render();}));
        LinearLayout center=row(this); center.setGravity(Gravity.CENTER); center.setMinimumHeight(dp(this,48));
        String label=day.equals(LocalDate.now()) ? "Aujourd’hui" : day.equals(LocalDate.now().minusDays(1)) ? "Hier" : day.format(DateTimeFormatter.ofPattern("EEEE",FR));
        TextView date=text(this,label+"  ·  "+day.format(DateTimeFormatter.ofPattern("d MMM",FR)),14,INK,true); center.addView(date);
        Icon down=new Icon(this,"down",MUTED); LinearLayout.LayoutParams arrow=new LinearLayout.LayoutParams(dp(this,16),dp(this,16)); arrow.leftMargin=dp(this,5); center.addView(down,arrow);
        center.setContentDescription("Choisir une journée : "+day.format(DATE)); clickable(center,BG,14);
        center.setOnClickListener(v->pickDate(day,picked->{day=picked;render();})); grow(dates,center);
        View next=iconButton("right","Jour suivant",SURFACE,()->{if(day.isBefore(LocalDate.now())) {day=day.plusDays(1);render();}});
        next.setEnabled(day.isBefore(LocalDate.now())); next.setAlpha(next.isEnabled()?1:.28f); dates.addView(next); body.addView(dates);
    }

    private void calorieCard() {
        double limit=HealthMath.dailyLimit(entries,day,profile);
        double total=HealthMath.totalCalories(entries,day), balance=limit-total;
        LinearLayout card=column(this); background(card,SURFACE,26); pad(card,20,16,20,20);
        if(balance<0) {
            TextView label=text(this,"PLAFOND DÉPASSÉ",10,ORANGE,true); label.setLetterSpacing(.12f); label.setGravity(Gravity.CENTER); card.addView(label); gap(card,8);
        }
        android.widget.FrameLayout dial=new android.widget.FrameLayout(this);
        OrbitalGauge gauge=new OrbitalGauge(this,total/limit); dial.addView(gauge,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout numbers=column(this); numbers.setGravity(Gravity.CENTER);
        TextView big=text(this,format(Math.abs(balance)),64,WHITE,false); big.setId(R.id.calorie_balance);
        big.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL)); big.setLetterSpacing(-.055f);
        big.setSingleLine(true); big.setGravity(Gravity.CENTER);
        big.setAutoSizeTextTypeUniformWithConfiguration(22,64,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        numbers.addView(big,new LinearLayout.LayoutParams(-1,dp(this,72)));
        TextView unit=text(this,"kcal",12,MUTED,true); unit.setLetterSpacing(.18f); unit.setGravity(Gravity.CENTER); numbers.addView(unit);
        android.widget.FrameLayout.LayoutParams numberPosition=new android.widget.FrameLayout.LayoutParams(dp(this,146),-2,Gravity.CENTER); numberPosition.bottomMargin=dp(this,4); dial.addView(numbers,numberPosition);
        TextView percent=text(this,Math.round(total/limit*100)+" % du plafond",11,balance<0?ORANGE:ACCENT,true);
        background(percent,0xFF19313D,12); pad(percent,12,7,12,7); percent.setGravity(Gravity.CENTER);
        android.widget.FrameLayout.LayoutParams percentPosition=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); percentPosition.bottomMargin=dp(this,4); dial.addView(percent,percentPosition);
        card.addView(dial,new LinearLayout.LayoutParams(-1,dp(this,206)));
        gap(card,14); View line=new View(this); line.setBackgroundColor(LINE); card.addView(line,new LinearLayout.LayoutParams(-1,dp(this,1))); gap(card,14);
        LinearLayout footer=row(this); TextView consumed=text(this,format(total)+" kcal consommées",12,WHITE,true); grow(footer,consumed);
        footer.addView(text(this,"/ "+format(limit),12,MUTED,false)); card.addView(footer);
        gap(card,16); divider(card); gap(card,14);
        boolean tracked=HealthMath.hasCalories(entries,day);
        String estimate=tracked ? "Perte théorique : ≈ "+String.format(FR,"%.3f kg",HealthMath.estimatedFatLossKg(balance)) : "Estimation : kcal non renseignées";
        TextView loss=text(this,estimate,14,tracked?ACCENT:MUTED,true); loss.setId(R.id.daily_fat_estimate); card.addView(loss);
        if(!tracked || balance<=0) {
            gap(card,7); card.addView(text(this,tracked?"Aucun déficit estimé pour cette journée.":"Ajoutez vos kcal pour calculer l’équivalent théorique.",11,MUTED,false));
        }
        body.addView(card);
    }

    private void weightCard() {
        LinearLayout card=row(this); clickable(card,SURFACE,22); pad(card,18,18,16,18);
        Icon icon=new Icon(this,"weight",BLUE); background(icon,0xFF202943,14); pad(icon,10,10,10,10);
        card.addView(icon,new LinearLayout.LayoutParams(dp(this,44),dp(this,44)));
        LinearLayout info=column(this); pad(info,14,0,0,0); info.addView(text(this,"Poids",13,MUTED,false)); gap(info,5);
        info.addView(text(this,String.format(FR,"%.1f",HealthMath.weightAt(entries,day,profile.initialWeight))+" kg",22,INK,true));
        Entry latest=null; for(Entry e:entries) if(e.kind.equals("weight") && !e.at.toLocalDate().isAfter(day)) {latest=e;break;}
        gap(info,4); info.addView(text(this,latest==null ? "Poids initial du profil" : "Mesuré le "+latest.at.format(DateTimeFormatter.ofPattern("d MMM · HH:mm",FR)),11,MUTED,false));
        grow(card,info); Icon plus=new Icon(this,"plus",INK); card.addView(plus,new LinearLayout.LayoutParams(dp(this,24),dp(this,24)));
        card.setContentDescription("Poids : "+HealthMath.weightAt(entries,day,profile.initialWeight)+" kg. Ajouter une mesure.");
        card.setOnClickListener(v->showEntry("weight",null)); card.setId(R.id.add_weight); body.addView(card);
    }

    private void evolution() {
        dateControl(); gap(body,20);
        LinearLayout tabs=row(this); background(tabs,LINE,16); pad(tabs,5,5,5,5);
        for(int period:new int[]{7,30}) {
            TextView tab=button(this,period+" jours",range==period?SURFACE:BG,()->{range=period;render();}); tab.setMinHeight(dp(this,44)); pad(tab,10,14,10,14); grow(tabs,tab);
        }
        body.addView(tabs); gap(body,20); chartCard(range,true); gap(body,16);
        body.addView(text(this,"Chaque point correspond à une saisie. Les kcal sont cumulées par jour ; le poids correspond à la dernière mesure du jour.",12,MUTED,false)); gap(body,28);
        LinearLayout metrics=row(this); LinearLayout kcal=column(this); background(kcal,SURFACE,20); pad(kcal,16,20,16,20);
        kcal.addView(text(this,"KCAL DU JOUR",10,GREEN,true)); gap(kcal,10); kcal.addView(text(this,format(HealthMath.totalCalories(entries,day)),28,INK,true)); gap(kcal,6);
        kcal.addView(text(this,"kcal consommées",11,MUTED,false)); grow(metrics,kcal);
        View spacer=new View(this); metrics.addView(spacer,new LinearLayout.LayoutParams(dp(this,12),1));
        LinearLayout kg=column(this); background(kg,SURFACE,20); pad(kg,16,20,16,20); kg.addView(text(this,"DERNIER POIDS",10,BLUE,true)); gap(kg,10);
        kg.addView(text(this,String.format(FR,"%.1f",HealthMath.weightAt(entries,day,profile.initialWeight)),28,INK,true)); gap(kg,6); kg.addView(text(this,"kg · à cette date",11,MUTED,false)); grow(metrics,kg); body.addView(metrics);
        gap(body,16); deficitCard();
    }

    private void deficitCard() {
        LocalDate through=day.isBefore(LocalDate.now())?day:LocalDate.now().minusDays(1);
        HealthMath.DeficitSummary summary=HealthMath.accumulatedDeficit(entries,through,LocalDate.now(),profile);
        LinearLayout card=column(this); background(card,SURFACE,16); pad(card,18,20,18,20);
        card.addView(text(this,summary.kcal>=0?"Déficit cumulé":"Excédent cumulé",17,INK,true)); gap(card,12);
        card.addView(text(this,format(Math.abs(summary.kcal))+" kcal",25,summary.kcal>=0?ACCENT:ORANGE,true)); gap(card,10);
        TextView loss=text(this,summary.days==0?"Estimation indisponible":"Perte théorique : ≈ "+String.format(FR,"%.3f kg",HealthMath.estimatedFatLossKg(summary.kcal)),15,INK,true);
        loss.setId(R.id.accumulated_fat_estimate); card.addView(loss); gap(card,10);
        String coverage=summary.days==0 ? "Aucune journée terminée avec des kcal renseignées." : summary.days+" journée"+(summary.days==1?"":"s")+" terminée"+(summary.days==1?"":"s")+" avec des kcal renseignées, jusqu’au "+through.format(DATE)+". Les excédents réduisent le déficit cumulé.";
        card.addView(text(this,coverage,11,MUTED,false));
        gap(card,16); divider(card); gap(card,14);
        card.addView(text(this,"Un déficit accumulé de 7 700 kcal correspond environ à la perte de 1 kg de graisse corporelle.",12,INK,true)); gap(card,8);
        card.addView(text(this,"Repère théorique, pas une perte mesurée. La perte réelle varie ; une journée incomplètement renseignée surestime le déficit.",11,MUTED,false));
        body.addView(card);
    }

    private void chartCard(int days,boolean expanded) {
        LinearLayout card=column(this); background(card,SURFACE,22); pad(card,16,20,16,16);
        LinearLayout legend=row(this); grow(legend,legend("Calories",GREEN)); legend.addView(legend("Poids",BLUE)); card.addView(legend); gap(card,18);
        card.addView(text(this,day.minusDays(days-1).format(DateTimeFormatter.ofPattern("d MMM",FR))+" — "+day.format(DateTimeFormatter.ofPattern("d MMM yyyy",FR)),11,MUTED,false)); gap(card,12);
        TrendView chart=new TrendView(this,entries,day,days); card.addView(chart,new LinearLayout.LayoutParams(-1,dp(this,expanded?260:180)));
        if(expanded) {
            gap(card,16); TextView detail=text(this,"Touchez la courbe pour lire une journée.",12,MUTED,false); detail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            chart.onSelection(i->detail.setText(chart.detail(i))); card.addView(detail);
        }
        body.addView(card);
    }

    private LinearLayout legend(String title,int color) {
        LinearLayout row=row(this); View dot=new View(this); background(dot,color,5);
        LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(this,8),dp(this,8)); dotParams.rightMargin=dp(this,7); row.addView(dot,dotParams);
        row.addView(text(this,title,12,INK,true)); return row;
    }

    private void entryRow(LinearLayout list,Entry e) {
        LinearLayout row=row(this); pad(row,0,18,0,18);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2465E2FF),null,shape(this,WHITE,12)));
        boolean kcal=e.kind.equals("calories"); Icon icon=new Icon(this,kcal?"plus":"weight",kcal?GREEN:BLUE);
        background(icon,kcal?0xFF17303B:0xFF202943,12); pad(icon,9,9,9,9); row.addView(icon,new LinearLayout.LayoutParams(dp(this,38),dp(this,38)));
        LinearLayout description=column(this); pad(description,12,0,0,0); description.addView(text(this,kcal?"Apport calorique":"Poids enregistré",14,INK,true)); gap(description,6);
        description.addView(text(this,e.at.format(TIME),12,MUTED,false)); grow(row,description);
        row.addView(text(this,kcal?format(e.value)+" kcal":String.format(FR,"%.1f kg",e.value),16,INK,true));
        row.setOnClickListener(v->showEntry(e.kind,e)); list.addView(row);
    }

    private void navigation() {
        LinearLayout holder=row(this); pad(holder,24,10,24,12); holder.setGravity(Gravity.CENTER);
        LinearLayout nav=row(this); background(nav,SURFACE,24); pad(nav,6,6,6,6);
        for(String target:new String[]{"journal","evolution"}) {
            boolean active=page.equals(target); LinearLayout tab=row(this); tab.setGravity(Gravity.CENTER); pad(tab,12,14,12,14); clickable(tab,active?ACCENT:BG,18);
            tab.setId(target.equals("journal")?R.id.nav_journal:R.id.nav_evolution);
            Icon icon=new Icon(this,target.equals("journal")?"journal":"chart",active?BG:MUTED); tab.addView(icon,new LinearLayout.LayoutParams(dp(this,20),dp(this,20)));
            TextView label=text(this,target.equals("journal")?"Journal":"Évolution",13,active?BG:MUTED,true); pad(label,8,0,0,0); tab.addView(label);
            tab.setContentDescription(label.getText()+ (active?", onglet sélectionné":"")); tab.setOnClickListener(v->{page=target;render();}); grow(nav,tab);
        }
        holder.addView(nav,new LinearLayout.LayoutParams(Math.min(dp(this,420),getResources().getDisplayMetrics().widthPixels-dp(this,48)),-2)); root.addView(holder);
    }

    private void profileHeader(LinearLayout container,boolean first) {
        if(first || !HealthMath.hasKnownGender(profile.gender)) {
            LinearLayout logo=row(this); Icon leaf=new Icon(this,"mark",ACCENT); background(leaf,SURFACE,18); pad(leaf,12,12,12,12);
            logo.addView(leaf,new LinearLayout.LayoutParams(dp(this,52),dp(this,52))); TextView brand=text(this,"Kcalcul",24,INK,true); pad(brand,12,0,0,0); logo.addView(brand); container.addView(logo);
        } else {
            LinearLayout top=row(this); top.addView(iconButton("left","Retour au journal",SURFACE,()->{page="journal";render();})); container.addView(top);
        }
    }

    private void profilePage(boolean first) {
        ageField=null; heightField=null; weightField=null;
        activityDescription=null;
        gender=profile==null?"":profile.gender;
        if(restored!=null && restored.containsKey("gender")) gender=restored.getString("gender");
        body.addView(text(this,"Genre",14,INK,true)); gap(body,10);
        LinearLayout choices=row(this);
        String[] genders={"Femme","Homme"};
        for(String choice:genders) {
            TextView chip=text(this,choice,12,gender.equals(choice)?BG:INK,true); chip.setGravity(Gravity.CENTER); pad(chip,4,16,4,16); clickable(chip,gender.equals(choice)?ACCENT:SURFACE,14);
            chip.setMinHeight(dp(this,48)); chip.setContentDescription(choice+(gender.equals(choice)?", sélectionné":""));
            chip.setOnClickListener(v->{gender=choice; genderError.setVisibility(View.GONE); updateActivityChoice(); for(int i=0;i<choices.getChildCount();i++) {
                TextView child=(TextView)choices.getChildAt(i); boolean selected=child.getText().equals(choice);
                clickable(child,selected?ACCENT:SURFACE,14); child.setTextColor(selected?BG:INK); child.setContentDescription(child.getText()+(selected?", sélectionné":""));
            }});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1); if(choices.getChildCount()>0) params.leftMargin=dp(this,8); choices.addView(chip,params);
        }
        body.addView(choices);
        genderError=text(this,"Sélectionnez Femme ou Homme.",12,ORANGE,false);
        genderError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); genderError.setVisibility(View.GONE); pad(genderError,0,10,0,0); body.addView(genderError);
        gap(body,20);
        ageField=field(body,"Âge","ans","28",false,R.id.age_input); gap(body,18);
        heightField=field(body,"Taille","cm","170",true,R.id.height_input);
        if(first) {gap(body,18); weightField=field(body,"Poids initial","kg","65,0",true,R.id.weight_input);}
        if(profile!=null) {ageField.setText(String.format(FR,"%d",profile.age));heightField.setText(decimalInput(profile.height));}
        activityLevel=profile==null ? ActivityLevel.SEDENTARY : profile.activity;
        if(restored!=null) {
            if(restored.containsKey("activity")) activityLevel=ActivityLevel.valueOf(restored.getString("activity"));
            if(restored.containsKey("age")) ageField.setText(restored.getString("age"));
            if(restored.containsKey("height")) heightField.setText(restored.getString("height"));
            if(weightField!=null && restored.containsKey("weight")) weightField.setText(restored.getString("weight"));
            restored=null;
        }
        gap(body,20); body.addView(text(this,"Niveau d’activité",14,INK,true)); gap(body,9);
        LinearLayout activityCard=row(this); clickable(activityCard,SURFACE,16); pad(activityCard,16,18,16,18);
        activityLabel=text(this,"",15,INK,true); grow(activityCard,activityLabel);
        Icon down=new Icon(this,"down",MUTED); activityCard.addView(down,new LinearLayout.LayoutParams(dp(this,20),dp(this,20)));
        activityCard.setId(R.id.activity_choice); activityCard.setOnClickListener(v->chooseActivity()); body.addView(activityCard);
        if(!first && HealthMath.hasKnownGender(profile.gender)) {
            gap(body,9); activityDescription=text(this,"",12,MUTED,false); body.addView(activityDescription);
        }
        updateActivityChoice();
        gap(body,24);
        TextView save=button(this,first?"Créer le profil":"Enregistrer mon profil",ACCENT,()->saveProfile(first)); save.setId(R.id.save_profile); body.addView(save); gap(body,18);
        if(!first) {
            TextView privacy=text(this,"Vos données restent sur ce téléphone.\nSans compte. Sans connexion.",12,MUTED,false); privacy.setGravity(Gravity.CENTER); body.addView(privacy);
        }
    }

    private void saveProfile(boolean first) {
        if(!HealthMath.hasKnownGender(gender)) {genderError.setVisibility(View.VISIBLE);return;}
        Integer age=validAge(ageField); Double height=validNumber(heightField,100,250);
        Double weight=first?validNumber(weightField,20,500):profile.initialWeight;
        if(age==null || height==null || weight==null) return;
        try {
            store.saveProfile(new Profile(gender,age,height,weight,activityLevel),LocalDateTime.now());
            hideKeyboard(); page="journal"; day=LocalDate.now(); render(); openPendingWidgetEntry();
        } catch(RuntimeException error) { new AlertDialog.Builder(this).setMessage("Impossible d’enregistrer le profil. Réessayez.").setPositiveButton("OK",null).show(); }
    }

    private void updateActivityChoice() {
        activityLabel.setText(activityLevel.label+"  ·  × "+activityLevel.factorLabel());
        if(activityDescription!=null) activityDescription.setText(activityLevel.description);
    }

    private void chooseActivity() {
        ScrollView scroll=new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout options=column(this); pad(options,12,8,12,8); scroll.addView(options);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).setNegativeButton("Annuler",null).create();
        for(ActivityLevel level:ActivityLevel.values()) {
            LinearLayout option=column(this); clickable(option,level==activityLevel?0xFF193342:SURFACE,16); pad(option,16,16,16,16);
            option.addView(text(this,level.label+"  ·  "+level.factorLabel(),15,INK,true)); gap(option,7);
            option.addView(text(this,level.description,12,MUTED,false));
            option.setContentDescription(level.label+", coefficient "+level.factorLabel()+". "+level.description+(level==activityLevel?" Sélectionné.":""));
            option.setOnClickListener(v->{activityLevel=level;updateActivityChoice();dialog.dismiss();});
            options.addView(option); gap(options,8);
        }
        dialog.show();
    }

    private Integer validAge(EditText field) {
        try { int age=Integer.parseInt(field.getText().toString().trim()); if(age<1 || age>120) throw new NumberFormatException(); field.setError(null); return age; }
        catch(NumberFormatException e) {field.setError("Saisissez un âge de 1 à 120 ans.");return null;}
    }
    private Double validNumber(EditText field,double min,double max) {
        try { double n=HealthMath.parsePositive(field.getText().toString(),min,max);field.setError(null);return n; }
        catch(IllegalArgumentException e) {field.setError(e.getMessage());return null;}
    }

    private EditText field(LinearLayout parent,String title,String unit,String hint,boolean decimal,int id) {
        return field(parent,title,unit,hint,decimal,id,null);
    }

    private EditText field(LinearLayout parent,String title,String unit,String hint,boolean decimal,int id,Runnable close) {
        TextView label=text(this,title,14,INK,true); label.setLabelFor(id);
        if(close==null) parent.addView(label);
        else {
            LinearLayout labels=row(this); grow(labels,label); labels.addView(iconButton("close","Fermer la saisie",BG,close)); parent.addView(labels);
        }
        gap(parent,9);
        LinearLayout input=row(this); background(input,SURFACE,16); pad(input,16,4,18,4);
        EditText field=new EditText(this); field.setId(id); field.setSingleLine(true); field.setSelectAllOnFocus(true); field.setTextSize(20); field.setTextColor(INK); field.setHintTextColor(0xFF66768B); field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0)); field.setBackgroundColor(Color.TRANSPARENT); field.setPadding(0,dp(this,12),dp(this,8),dp(this,12));
        field.setMinimumHeight(dp(this,52)); field.setContentDescription(title+", en "+unit); grow(input,field); input.addView(text(this,unit,14,MUTED,false)); parent.addView(input); return field;
    }

    private TextView editorDate,editorClock;
    private void updateEditorTime() { editorDate.setText(editorTime[0].format(DATE)); editorClock.setText(editorTime[0].format(TIME)); }

    private void showEntry(String kind,Entry entry) {
        boolean kcal=kind.equals("calories"); editorKind=kind; editorId=entry==null?-1:entry.id;
        LocalDateTime now=LocalDateTime.now().withSecond(0).withNano(0);
        editorTime=new LocalDateTime[]{entry==null?LocalDateTime.of(day,now.toLocalTime()):entry.at};
        editor=new Dialog(this); editor.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout sheet=column(this); background(sheet,BG,28); pad(sheet,24,12,24,24);
        View handle=new View(this); background(handle,LINE,3); LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(this,32),dp(this,4)); handleParams.gravity=Gravity.CENTER; sheet.addView(handle,handleParams); gap(sheet,12);
        editorAmount=field(sheet,kcal?"Calories ingérées":"Poids",kcal?"kcal":"kg",kcal?"450":"65,0",!kcal,R.id.entry_value,()->editor.dismiss());
        if(entry!=null) editorAmount.setText(kcal?formatPlain(entry.value):decimalInput(entry.value)); gap(sheet,22);
        sheet.addView(text(this,"Date et heure",14,INK,true)); gap(sheet,10);
        LinearLayout timeRow=row(this);
        editorDate=button(this,"",SURFACE,()->pickDate(editorTime[0].toLocalDate(),picked->{editorTime[0]=LocalDateTime.of(picked,editorTime[0].toLocalTime());updateEditorTime();}));
        editorDate.setTextSize(13); pad(editorDate,10,18,10,18); grow(timeRow,editorDate);
        View spacer=new View(this); timeRow.addView(spacer,new LinearLayout.LayoutParams(dp(this,10),1));
        editorClock=button(this,"",SURFACE,()->{
            LocalTime time=editorTime[0].toLocalTime();
            new TimePickerDialog(this,(picker,h,m)->{editorTime[0]=editorTime[0].withHour(h).withMinute(m);updateEditorTime();},time.getHour(),time.getMinute(),true).show();
        }); editorClock.setMinWidth(dp(this,90)); timeRow.addView(editorClock); updateEditorTime(); sheet.addView(timeRow); gap(sheet,24);
        TextView save=button(this,entry!=null?"Enregistrer les modifications":"Enregistrer",ACCENT,()->{
            Double value=validNumber(editorAmount,kcal?1:20,kcal?30000:500); if(value==null) return;
            if(editorTime[0].isAfter(LocalDateTime.now())) {editorClock.setError("Choisissez une heure passée ou actuelle.");return;}
            try {
                store.saveEntry(editorId,kind,value,editorTime[0]); day=editorTime[0].toLocalDate(); hideKeyboard(); editor.dismiss(); render();
            } catch(RuntimeException error) {editorAmount.setError("Impossible d’enregistrer. Réessayez.");}
        }); save.setId(R.id.entry_save); sheet.addView(save);
        if(entry!=null) {
            gap(sheet,12); TextView delete=button(this,"Supprimer cette saisie",BG,()->{
                new AlertDialog.Builder(this).setTitle("Supprimer cette saisie ?").setMessage("Cette action efface uniquement la saisie sélectionnée.")
                    .setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{
                        store.deleteEntry(entry.id); editor.dismiss(); render();
                    }).show();
            }); delete.setTextColor(ORANGE); sheet.addView(delete);
        }
        scroll.addView(sheet); editor.setContentView(scroll);
        Window window=editor.getWindow(); if(window!=null) {
            window.setBackgroundDrawableResource(android.R.color.transparent); window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams params=window.getAttributes(); params.dimAmount=.28f; window.setAttributes(params);
        }
        editor.setOnDismissListener(d->{editor=null;editorAmount=null;editorTime=null;}); editor.show();
        if(window!=null) window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(this,560)),WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void pickDate(LocalDate initial,java.util.function.Consumer<LocalDate> onPick) {
        DatePickerDialog dialog=new DatePickerDialog(this,(view,year,month,d)->onPick.accept(LocalDate.of(year,month+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis()); dialog.show();
    }

    private View iconButton(String name,String description,int color,Runnable action) {
        Icon icon=new Icon(this,name,INK); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); icon.setContentDescription(description);
        clickable(icon,color,16); pad(icon,12,12,12,12); icon.setLayoutParams(new LinearLayout.LayoutParams(dp(this,48),dp(this,48))); icon.setOnClickListener(v->action.run()); return icon;
    }
    private void sectionTitle(String title,String link,Runnable action) {
        LinearLayout row=row(this); grow(row,text(this,title,19,INK,true));
        if(link!=null) {TextView button=text(this,link+"  →",12,GREEN,true);pad(button,12,12,0,12);button.setMinHeight(dp(this,48));button.setOnClickListener(v->action.run());row.addView(button);}
        body.addView(row);
    }
    private void divider(LinearLayout column) { View line=new View(this);line.setBackgroundColor(LINE);column.addView(line,new LinearLayout.LayoutParams(-1,dp(this,1))); }
    private static String format(double number) { return String.format(FR,"%,.0f",number); }
    private static String formatPlain(double number) {return String.format(Locale.US,"%.0f",number);}
    private static String decimalInput(double number) {return java.math.BigDecimal.valueOf(number).stripTrailingZeros().toPlainString().replace('.',',');}
    private void hideKeyboard() {
        View focus=getCurrentFocus(); if(focus!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);
    }
    // Only the pre-33 compatibility path; API 33+ is handled by OnBackInvokedDispatcher above.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation") @Override public void onBackPressed() {
        if(profile!=null && HealthMath.hasKnownGender(profile.gender) && !page.equals("journal")) {page="journal";render();} else super.onBackPressed();
    }
}
