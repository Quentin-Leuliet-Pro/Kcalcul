package fr.equilibre.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import fr.equilibre.app.HealthMath.Entry;
import fr.equilibre.app.HealthMath.Profile;

/** One private, offline SQLite database. Dates retain the local time entered by the user. */
public final class LocalStore extends SQLiteOpenHelper {
    public LocalStore(Context context) { super(context, "equilibre.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profile (id INTEGER PRIMARY KEY CHECK(id=1), gender TEXT NOT NULL, age INTEGER NOT NULL, height REAL NOT NULL, initial_weight REAL NOT NULL, activity TEXT NOT NULL DEFAULT 'SEDENTARY')");
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('calories','weight')), value REAL NOT NULL CHECK(value>0), occurred_at TEXT NOT NULL)");
        db.execSQL("CREATE INDEX entries_by_time ON entries(occurred_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE profile ADD COLUMN activity TEXT NOT NULL DEFAULT 'SEDENTARY'");
    }

    public Profile profile() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT gender, age, height, initial_weight, activity FROM profile WHERE id=1", null)) {
            return c.moveToFirst() ? new Profile(c.getString(0), c.getInt(1), c.getDouble(2), c.getDouble(3),ActivityLevel.valueOf(c.getString(4))) : null;
        }
    }

    public void saveProfile(Profile profile, LocalDateTime now) {
        validateProfile(profile);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            boolean first = profile() == null;
            ContentValues values = new ContentValues();
            values.put("id", 1); values.put("gender", profile.gender); values.put("age", profile.age);
            values.put("height", profile.height); values.put("initial_weight", profile.initialWeight);
            values.put("activity",profile.activity.name());
            if (db.insertWithOnConflict("profile", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1)
                throw new IllegalStateException("Profil non enregistré.");
            if (first) saveEntry(-1, "weight", profile.initialWeight, now);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public long saveEntry(long id, String kind, double value, LocalDateTime at) {
        if (!kind.equals("calories") && !kind.equals("weight")) throw new IllegalArgumentException("Type invalide.");
        HealthMath.parsePositive(Double.toString(value), kind.equals("weight") ? 20 : 1, kind.equals("weight") ? 500 : 30000);
        if (at == null || at.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("La date ne peut pas être dans le futur.");
        ContentValues values = new ContentValues();
        values.put("kind", kind); values.put("value", value); values.put("occurred_at", at.withSecond(0).withNano(0).toString());
        SQLiteDatabase db = getWritableDatabase();
        if (id < 0) return db.insertOrThrow("entries", null, values);
        if (db.update("entries", values, "id=?", new String[]{Long.toString(id)}) != 1)
            throw new IllegalStateException("Cette saisie n’existe plus.");
        return id;
    }

    public void deleteEntry(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{Long.toString(id)});
    }

    public List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, kind, value, occurred_at FROM entries ORDER BY occurred_at DESC, id DESC", null)) {
            while (c.moveToNext()) result.add(new Entry(c.getLong(0), c.getString(1), c.getDouble(2), LocalDateTime.parse(c.getString(3))));
        }
        return result;
    }

    private static void validateProfile(Profile p) {
        HealthMath.parsePositive(Double.toString(p.height), 100, 250);
        HealthMath.parsePositive(Double.toString(p.initialWeight), 20, 500);
        if (p.age < 1 || p.age > 120) throw new IllegalArgumentException("Âge attendu : 1 à 120 ans.");
        if (!HealthMath.hasKnownGender(p.gender)) throw new IllegalArgumentException("Sélectionnez Femme ou Homme.");
        if (p.activity == null) throw new IllegalArgumentException("Niveau d’activité manquant.");
    }
}
