package fr.equilibre.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import fr.equilibre.app.HealthMath.Entry;

/** Two explicitly labelled axes; missing records are never plotted as zero. */
public final class TrendView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dashes = new DashPathEffect(new float[]{4,7},0);
    private final Path path = new Path(), area = new Path();
    private final LocalDate start;
    private final int days;
    private final double[] calories, weights;
    private final double kcalMax, kgMin, kgMax;
    private int selected = -1;
    private IntConsumer listener;
    private float left, right;

    public TrendView(Context context) { this(context,java.util.Collections.emptyList(),LocalDate.now(),7); }

    public TrendView(Context context, List<Entry> entries, LocalDate end, int days) {
        super(context); this.days=days; start=end.minusDays(days-1);
        calories=new double[days]; weights=new double[days];
        double max=0, lo=Double.POSITIVE_INFINITY, hi=Double.NEGATIVE_INFINITY;
        for (int i=0;i<days;i++) {
            LocalDate day=start.plusDays(i); boolean hasCalories=false; Entry lastWeight=null;
            double total=0;
            for (Entry e: entries) if (e.at.toLocalDate().equals(day)) {
                if (e.kind.equals("calories")) { hasCalories=true; total+=e.value; }
                else if (lastWeight==null || e.at.isAfter(lastWeight.at) || (e.at.equals(lastWeight.at) && e.id>lastWeight.id)) lastWeight=e;
            }
            calories[i]=hasCalories ? total : Double.NaN;
            weights[i]=lastWeight==null ? Double.NaN : lastWeight.value;
            if (hasCalories) max=Math.max(max,total);
            if (lastWeight!=null) { lo=Math.min(lo,lastWeight.value); hi=Math.max(hi,lastWeight.value); }
        }
        kcalMax=Math.max(1000,Math.ceil(max/500)*500);
        kgMin=Double.isFinite(lo) ? Math.floor((lo-1)*2)/2 : 50;
        kgMax=Double.isFinite(hi) ? Math.ceil((hi+1)*2)/2 : 100;
        setContentDescription("Graphique des calories en kcal sur l’axe gauche et du poids en kg sur l’axe droit. " + days + " jours. Touchez un jour pour voir les valeurs.");
        setFocusable(true);
    }
    public void onSelection(IntConsumer listener) { this.listener=listener; }
    public String detail(int i) {
        return start.plusDays(i).format(DateTimeFormatter.ofPattern("d MMM",Locale.FRANCE)) + "  ·  "
                + (Double.isNaN(calories[i]) ? "kcal non renseignées" : String.format(Locale.FRANCE,"%,.0f kcal",calories[i]))
                + "  ·  " + (Double.isNaN(weights[i]) ? "poids non renseigné" : String.format(Locale.FRANCE,"%.1f kg",weights[i]));
    }
    public boolean hasRecords() {
        for (int i=0;i<days;i++) if (!Double.isNaN(calories[i]) || !Double.isNaN(weights[i])) return true;
        return false;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); left=Ui.dp(getContext(),35); right=getWidth()-Ui.dp(getContext(),36);
        float top=Ui.dp(getContext(),25), bottom=getHeight()-Ui.dp(getContext(),29), height=bottom-top;
        paint.setTextSize(Ui.dp(getContext(),10)); paint.setStrokeWidth(Ui.dp(getContext(),1));
        for(int i=0;i<=4;i++) {
            float y=top+height*i/4;
            paint.setStyle(Paint.Style.STROKE); paint.setColor(Ui.LINE); paint.setPathEffect(dashes);
            canvas.drawLine(left,y,right,y,paint); paint.setPathEffect(null); paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.RIGHT); paint.setColor(Ui.GREEN);
            canvas.drawText(String.format(Locale.FRANCE,"%.0f",kcalMax*(4-i)/4),left-Ui.dp(getContext(),7),y+3,paint);
            paint.setTextAlign(Paint.Align.LEFT); paint.setColor(Ui.BLUE);
            canvas.drawText(String.format(Locale.FRANCE,"%.1f",kgMin+(kgMax-kgMin)*(4-i)/4),right+Ui.dp(getContext(),7),y+3,paint);
        }
        paint.setTextAlign(Paint.Align.LEFT); paint.setColor(Ui.GREEN); canvas.drawText("kcal",left,Ui.dp(getContext(),12),paint);
        paint.setTextAlign(Paint.Align.RIGHT); paint.setColor(Ui.BLUE); canvas.drawText("kg",right,Ui.dp(getContext(),12),paint);
        paint.setColor(Ui.MUTED); paint.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<days;i++) if(days==7 || i==0 || i==days-1 || i%7==0) {
            String label=start.plusDays(i).format(DateTimeFormatter.ofPattern(days==7 ? "EEEEE" : "d/M",Locale.FRANCE));
            canvas.drawText(label,x(i),getHeight()-Ui.dp(getContext(),6),paint);
        }
        if(selected>=0) {
            paint.setColor(Ui.LINE); paint.setStrokeWidth(Ui.dp(getContext(),1)); canvas.drawLine(x(selected),top,x(selected),bottom,paint);
        }
        series(canvas,calories,0,kcalMax,Ui.GREEN,top,bottom,true);
        series(canvas,weights,kgMin,kgMax,Ui.BLUE,top,bottom,false);
        if(!hasRecords()) {
            paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(Ui.dp(getContext(),12)); paint.setColor(Ui.MUTED);
            canvas.drawText("Aucune donnée sur cette période",(left+right)/2,top+height/2,paint);
        }
    }
    private float x(int i) { return left+(right-left)*i/(days-1); }
    private void series(Canvas canvas,double[] values,double min,double max,int color,float top,float bottom,boolean fill) {
        path.reset(); int first=-1,last=-1;
        for(int i=0;i<days;i++) if(!Double.isNaN(values[i])) {
            float y=bottom-(float)((values[i]-min)/(max-min))*(bottom-top);
            if(first<0) { path.moveTo(x(i),y); first=i; } else path.lineTo(x(i),y);
            last=i;
        }
        if(first<0) return;
        if(fill && first!=last) {
            area.set(path); area.lineTo(x(last),bottom); area.lineTo(x(first),bottom); area.close();
            paint.setColor(0x1465E2FF); paint.setStyle(Paint.Style.FILL); canvas.drawPath(area,paint);
        }
        paint.setColor(color); paint.setStrokeWidth(Ui.dp(getContext(),2.5f)); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path,paint); paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<days;i++) if(!Double.isNaN(values[i])) {
            float y=bottom-(float)((values[i]-min)/(max-min))*(bottom-top);
            if(days==7 || i==first || i==last || i==selected) {
                paint.setColor(Ui.SURFACE); canvas.drawCircle(x(i),y,Ui.dp(getContext(),4),paint);
                paint.setColor(color); canvas.drawCircle(x(i),y,Ui.dp(getContext(),2.6f),paint);
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_DOWN) return true;
        if(event.getAction()==MotionEvent.ACTION_UP && right>left) {
            selected=Math.max(0,Math.min(days-1,Math.round((event.getX()-left)/(right-left)*(days-1))));
            invalidate(); performClick(); if(listener!=null) listener.accept(selected); setContentDescription(detail(selected)); return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
