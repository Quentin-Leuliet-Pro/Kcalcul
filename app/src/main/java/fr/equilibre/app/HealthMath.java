package fr.equilibre.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Mifflin–St Jeor calculations using the selected sex coefficient and activity factor. */
public final class HealthMath {
    public static final double KCAL_PER_KG_FAT = 7700;
    private HealthMath() { }

    public static boolean hasKnownGender(String gender) {
        return "Femme".equals(gender) || "Homme".equals(gender);
    }

    public static double calorieLimit(double weightKg, double heightCm, int age, ActivityLevel activity, String gender) {
        if(!hasKnownGender(gender)) throw new IllegalArgumentException("Sélectionnez Femme ou Homme.");
        double coefficient="Femme".equals(gender)?-161:5;
        return ((10 * weightKg) + (6.25 * heightCm) - (5 * age) + coefficient) * activity.factor;
    }

    /** Always computed from current profile and recorded weight; never a cached daily target. */
    public static double dailyLimit(List<Entry> entries, LocalDate day, Profile profile) {
        return calorieLimit(weightAt(entries,day,profile.initialWeight),profile.height,profile.age,profile.activity,profile.gender);
    }

    public static double estimatedFatLossKg(double deficitKcal) {
        return Math.max(0,deficitKcal) / KCAL_PER_KG_FAT;
    }

    public static boolean hasCalories(List<Entry> entries, LocalDate day) {
        for(Entry e:entries) if(e.kind.equals("calories") && e.at.toLocalDate().equals(day)) return true;
        return false;
    }

    /** Signed net balance: surplus days offset deficit days; untracked/open days are excluded. */
    public static DeficitSummary accumulatedDeficit(List<Entry> entries, LocalDate through, LocalDate today, Profile profile) {
        if(!hasKnownGender(profile.gender)) throw new IllegalArgumentException("Sélectionnez Femme ou Homme.");
        java.util.Map<LocalDate,Double> totals=new java.util.TreeMap<>();
        for(Entry e:entries) {
            LocalDate d=e.at.toLocalDate();
            if(e.kind.equals("calories") && !d.isAfter(through) && d.isBefore(today))
                totals.put(d,totals.getOrDefault(d,0.0)+e.value);
        }
        double deficit=0;
        for(java.util.Map.Entry<LocalDate,Double> total:totals.entrySet())
            deficit+=dailyLimit(entries,total.getKey(),profile)-total.getValue();
        return new DeficitSummary(deficit,totals.size());
    }

    public static final class DeficitSummary {
        public final double kcal;
        public final int days;
        public DeficitSummary(double kcal,int days) {this.kcal=kcal;this.days=days;}
    }

    public static double parsePositive(String value, double min, double max) {
        double parsed;
        try { parsed = Double.parseDouble(value.trim().replace(',', '.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Saisissez un nombre valide."); }
        if (!Double.isFinite(parsed) || parsed < min || parsed > max)
            throw new IllegalArgumentException("Valeur attendue : " + min + " à " + max + ".");
        return parsed;
    }

    public static double totalCalories(List<Entry> entries, LocalDate day) {
        double total = 0;
        for (Entry e : entries) if (e.kind.equals("calories") && e.at.toLocalDate().equals(day)) total += e.value;
        return total;
    }

    public static double weightAt(List<Entry> entries, LocalDate day, double initialWeight) {
        LocalDateTime latest = null;
        long latestId = -1;
        double weight = initialWeight;
        for (Entry e : entries) {
            if (e.kind.equals("weight") && !e.at.toLocalDate().isAfter(day)
                    && (latest == null || e.at.isAfter(latest) || (e.at.equals(latest) && e.id > latestId))) {
                latest = e.at;
                latestId = e.id;
                weight = e.value;
            }
        }
        return weight;
    }

    public static final class Entry {
        public final long id;
        public final String kind;
        public final double value;
        public final LocalDateTime at;
        public Entry(long id, String kind, double value, LocalDateTime at) {
            this.id = id; this.kind = kind; this.value = value; this.at = at;
        }
    }

    public static final class Profile {
        public final String gender;
        public final int age;
        public final double height;
        public final double initialWeight;
        public final ActivityLevel activity;
        public Profile(String gender, int age, double height, double initialWeight) {
            this(gender,age,height,initialWeight,ActivityLevel.SEDENTARY);
        }
        public Profile(String gender, int age, double height, double initialWeight, ActivityLevel activity) {
            this.gender = gender; this.age = age; this.height = height; this.initialWeight = initialWeight;
            this.activity = activity;
        }
    }
}
