package fr.equilibre.app;

/** Stable storage names, with the five activity factors supplied for the application. */
public enum ActivityLevel {
    SEDENTARY("Sédentaire", 1.2,
        "Travail de bureau assis, très peu ou pas d’exercice physique, déplacements en voiture ou transports."),
    LIGHT("Légèrement actif", 1.375,
        "Travail assis avec une position debout fréquente, ou sport léger 1 à 3 fois par semaine : marche, étirements."),
    MODERATE("Modérément actif", 1.55,
        "Travail debout (vendeur, enseignant), ou métier assis avec un entraînement modéré 3 à 5 fois par semaine."),
    VERY_ACTIVE("Très actif", 1.725,
        "Travail physique pénible (bâtiment, déménagement), ou sport intense 6 à 7 fois par semaine."),
    EXTREME("Extrêmement actif", 2.0,
        "Métier très physique avec des entraînements intensifs quotidiens, ou athlète de haut niveau avec deux entraînements par jour : marathonien, cycliste.");

    public final String label;
    public final double factor;
    public final String description;

    ActivityLevel(String label, double factor, String description) {
        this.label=label; this.factor=factor; this.description=description;
    }

    public String factorLabel() {
        return this==EXTREME ? "2,0" : java.math.BigDecimal.valueOf(factor).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
