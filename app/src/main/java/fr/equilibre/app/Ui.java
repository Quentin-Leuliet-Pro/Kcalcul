package fr.equilibre.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xFF0B1017, SURFACE = 0xFF141D29, INK = 0xFFEDF4FA, MUTED = 0xFF91A0B4;
    static final int ACCENT = 0xFF65E2FF, GREEN = ACCENT, BLUE = 0xFF9B9BFF;
    static final int LINE = 0xFF293749, ORANGE = 0xFFFFA58C, WHITE = Color.WHITE;

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, Math.min(radius,16)));
        if(color==SURFACE) d.setStroke(dp(c,1),LINE);
        return d;
    }
    static void background(View v, int color, int radius) { v.setBackground(shape(v.getContext(), color, radius)); }
    static void clickable(View v, int color, int radius) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2465E2FF), shape(v.getContext(), color, radius), shape(v.getContext(), WHITE, radius)));
    }
    static LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static TextView text(Context c, String value, int size, int color, boolean medium) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false); t.setLineSpacing(dp(c, 3), 1); return t;
    }
    static void pad(View v, int left, int top, int right, int bottom) { Context c = v.getContext(); v.setPadding(dp(c,left),dp(c,top),dp(c,right),dp(c,bottom)); }
    static void gap(LinearLayout l, int height) { View v = new View(l.getContext()); l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(),height))); }
    static void grow(LinearLayout row, View view) { row.addView(view, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1)); }
    static TextView button(Context c, String title, int color, Runnable action) {
        TextView t = text(c,title,15,color==ACCENT?BG:INK,true); t.setGravity(Gravity.CENTER); pad(t,20,18,20,18);
        clickable(t,color,18); t.setOnClickListener(v -> action.run()); t.setMinHeight(dp(c,56)); return t;
    }

    static final class Icon extends View {
        final String name;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF rect = new RectF();
        final int color;
        public Icon(Context c) { this(c,"mark",ACCENT); }
        Icon(Context c, String name, int color) { super(c); this.name=name; this.color=color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.save();
            float width=getWidth()-getPaddingLeft()-getPaddingRight(), height=getHeight()-getPaddingTop()-getPaddingBottom();
            float scale=Math.min(width,height)/24f;
            c.translate(getPaddingLeft()+(width-24*scale)/2,getPaddingTop()+(height-24*scale)/2); c.scale(scale,scale);
            p.setColor(color); p.setStrokeWidth(1.65f); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch(name) {
                case "mark":
                    p.setStrokeWidth(2); c.drawLine(8,6,8,18,p);
                    path.moveTo(17,6); path.lineTo(10,12); path.lineTo(17,18); c.drawPath(path,p);
                    c.drawLine(3,8,3,3,p); c.drawLine(3,3,8,3,p); c.drawLine(16,21,21,21,p); c.drawLine(21,21,21,16,p); break;
                case "profile": c.drawCircle(12,8,3.2f,p); rect.set(5,13,19,23); c.drawArc(rect,185,170,false,p); break;
                case "plus": c.drawLine(12,5,12,19,p); c.drawLine(5,12,19,12,p); break;
                case "left": path.moveTo(14,6); path.lineTo(8,12); path.lineTo(14,18); c.drawPath(path,p); break;
                case "right": path.moveTo(10,6); path.lineTo(16,12); path.lineTo(10,18); c.drawPath(path,p); break;
                case "down": path.moveTo(7,10); path.lineTo(12,15); path.lineTo(17,10); c.drawPath(path,p); break;
                case "chart": path.moveTo(4,17); path.lineTo(9,12); path.lineTo(13,14); path.lineTo(20,6); c.drawPath(path,p); c.drawLine(4,21,20,21,p); break;
                case "journal": rect.set(5,3,19,21); c.drawRoundRect(rect,3,3,p); c.drawLine(9,8,15,8,p); c.drawLine(9,12,15,12,p); c.drawLine(9,16,13,16,p); break;
                case "weight": rect.set(4,4,20,20); c.drawRoundRect(rect,5,5,p); rect.set(8,7,16,14); c.drawArc(rect,195,150,false,p); c.drawLine(12,11,14,8,p); break;
                case "clock": c.drawCircle(12,12,8,p); c.drawLine(12,7,12,12,p); c.drawLine(12,12,16,14,p); break;
                case "check": path.moveTo(5,12); path.lineTo(10,17); path.lineTo(19,7); c.drawPath(path,p); break;
                case "close": c.drawLine(6,6,18,18,p); c.drawLine(18,6,6,18,p); break;
            }
            c.restore();
        }
    }

    static final class OrbitalGauge extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF arc = new RectF();
        final double ratio;
        android.graphics.Shader spectrum,halo;
        float x,y,r;
        public OrbitalGauge(Context c) { this(c,0); }
        OrbitalGauge(Context c, double ratio) { super(c); this.ratio=ratio; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
            x=width/2f; y=height*.47f; r=Math.min(width,height)/2f-dp(getContext(),18);
            arc.set(x-r,y-r,x+r,y+r);
            android.graphics.SweepGradient gradient=new android.graphics.SweepGradient(x,y,new int[]{ACCENT,BLUE,ACCENT},new float[]{0,.75f,1});
            android.graphics.Matrix rotation=new android.graphics.Matrix(); rotation.setRotate(135,x,y); gradient.setLocalMatrix(rotation); spectrum=gradient;
            halo=new android.graphics.RadialGradient(x,y,r,new int[]{0x1865E2FF,0x089B9BFF,0x00141D29},new float[]{0,.7f,1},android.graphics.Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); if(r<=0) return;
            float progress=(float)Math.max(0,Math.min(1,ratio));
            p.setStyle(Paint.Style.FILL); p.setColor(WHITE); p.setShader(halo); c.drawCircle(x,y,r,p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(dp(getContext(),1));
            for(int i=0;i<=45;i++) {
                double angle=Math.toRadians(135+i*6);
                float outer=r+dp(getContext(),10), inner=outer-dp(getContext(),i%5==0?5:2.5f);
                p.setColor(i/45f<=progress?(ratio>1?0x80FFA58C:0x8065E2FF):LINE);
                c.drawLine(x+(float)Math.cos(angle)*inner,y+(float)Math.sin(angle)*inner,x+(float)Math.cos(angle)*outer,y+(float)Math.sin(angle)*outer,p);
            }
            p.setStrokeWidth(dp(getContext(),3)); p.setColor(LINE); c.drawArc(arc,135,270,false,p);
            p.setColor(ratio>1?ORANGE:ACCENT); if(ratio<=1) p.setShader(spectrum);
            c.drawArc(arc,135,270*progress,false,p); p.setShader(null);
            double end=Math.toRadians(135+270*progress);
            float endX=x+(float)Math.cos(end)*r,endY=y+(float)Math.sin(end)*r;
            p.setStyle(Paint.Style.FILL); p.setColor(ratio>1?0x30FFA58C:0x3065E2FF); c.drawCircle(endX,endY,dp(getContext(),7),p);
            p.setColor(ratio>1?ORANGE:ACCENT); c.drawCircle(endX,endY,dp(getContext(),3.5f),p);
        }
    }
}
