package com.plusgroup.pos.util

import android.app.Activity
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.plusgroup.pos.R

/**
 * Lang APK a: Kreyòl (ht, lang sous la) oswa Français (fr).
 *
 *  - Tout tèks yo rete ekri an kreyòl nan layout yo ak nan kòd la.
 *  - [apply] pase sou tout ekran an epi tradwi tèks, hint ak contentDescription
 *    ki gen yon tradiksyon nan [FR]. Li kontinye veye: si kòd la chanje yon tèks
 *    apre (ex: "Salut ..." apre rezo a reponn) oswa si yon nouvo liy ajoute nan
 *    yon lis, li tradwi l tou.
 *  - [t] pou tèks ki parèt nan dyalòg, Toast, meni (pa nan ekran an menm).
 *
 * Chwa a sere sou aparèy la (SharedPreferences "plus_lang") — chak ajan chwazi pa l.
 */
object Lang {
    private const val PREFS = "plus_lang"
    private const val KEY = "lang"
    private var current: String? = null

    fun get(ctx: Context): String {
        current?.let { return it }
        val v = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "ht") ?: "ht"
        current = if (v == "fr") "fr" else "ht"
        return current!!
    }

    fun isFr(ctx: Context) = get(ctx) == "fr"

    /** Chanje lang lan epi rekonstwi ekran an. */
    fun set(activity: Activity, code: String) {
        val c = if (code == "fr") "fr" else "ht"
        if (c == get(activity)) return
        current = c
        activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, c).apply()
        activity.recreate()
    }

    /** Tradwi yon tèks kreyòl (si lang lan se fransè epi tradiksyon an egziste). */
    fun t(ctx: Context, s: String?): String {
        if (s == null) return ""
        if (get(ctx) != "fr") return s
        return FR[s] ?: FR[s.trim()] ?: s
    }

    /** Menm jan ak t(ctx, s), san context (lang lan deja chaje pa BaseActivity). */
    fun tr(s: String): String {
        if (current != "fr") return s
        return FR[s] ?: FR[s.trim()] ?: s
    }

    /** Tèks ak varyab: tf(ctx, "{a} boul", "a" to 3) */
    fun tf(ctx: Context, template: String, vararg vars: Pair<String, Any?>): String {
        var out = t(ctx, template)
        vars.forEach { (k, v) -> out = out.replace("{$k}", v?.toString() ?: "") }
        return out
    }

    /** Tradwi tit yon meni (PopupMenu). */
    fun menu(ctx: Context, menu: Menu) {
        if (get(ctx) != "fr") return
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.title = t(ctx, item.title?.toString())
        }
    }

    // ==================== Ekran an ====================

    /** Tradwi tout yon pyebwa view epi veye chanjman yo. */
    fun apply(root: View?) {
        if (root == null || get(root.context) != "fr") return
        translateTree(root)
    }

    private fun translateTree(v: View) {
        translateView(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) translateTree(v.getChildAt(i))
            // Nouvo view ki ajoute pita (lis fich, liy, seksyon paramèt...)
            v.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) { child?.let { translateTree(it) } }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
        }
    }

    private fun translateView(v: View) {
        val ctx = v.context
        v.contentDescription?.toString()?.let { cd -> FR[cd]?.let { v.contentDescription = it } }
        if (v !is TextView) return
        v.hint?.toString()?.let { h -> FR[h]?.let { v.hint = it } }
        if (v is EditText) return // pa janm touche sa moun nan tape
        v.text?.toString()?.let { tx -> (FR[tx] ?: FR[tx.trim()])?.let { v.text = it } }
        if (v.getTag(R.id.tag_lang_watch) == null) {
            v.setTag(R.id.tag_lang_watch, true)
            v.addTextChangedListener(object : TextWatcher {
                private var busy = false
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (busy || get(ctx) != "fr") return
                    val raw = s?.toString() ?: return
                    val tr = FR[raw] ?: FR[raw.trim()] ?: translateDynamic(raw) ?: return
                    if (tr == raw) return
                    busy = true
                    v.text = tr
                    busy = false
                }
            })
        }
    }

    /** Kèk tèks ki gen varyab ladan yo (Salut X, X boul…). */
    private fun translateDynamic(s: String): String? {
        PATTERNS.forEach { (re, fr) ->
            val m = re.matchEntire(s) ?: return@forEach
            var out = fr
            m.groupValues.drop(1).forEachIndexed { i, g -> out = out.replace("{$i}", g) }
            return out
        }
        return null
    }

    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("^Salut (.+)$") to "Salut {0}",
        Regex("^Tiraj disponib \\((\\d+)\\)$") to "Tirages disponibles ({0})",
        Regex("^Version (.+) @PlusGroup$") to "Version {0} @PlusGroup",
        Regex("^(\\d+) boul$") to "{0} boule(s)",
        Regex("^(\\d+) liy$") to "{0} ligne(s)",
        Regex("^(\\d+) fich$") to "{0} fiche(s)",
        Regex("^Fich #(.+)$") to "Fiche #{0}",
        Regex("^(\\d+) sèvè chaje$") to "{0} serveur(s) chargé(s)",
        Regex("^Ap konekte ak (.+)\\.\\.\\.$") to "Connexion à {0}...",
        Regex("^Fich peye: (.+) HTG$") to "Fiche payée : {0} HTG",
        Regex("^Erè: (.+)$") to "Erreur : {0}",
        Regex("^Erè rezo: (.+)$") to "Erreur réseau : {0}",
    )

    // ==================== Tradiksyon fransè ====================
    val FR: Map<String, String> = hashMapOf(
        // Akèy
        "Salut" to "Salut",
        "BALANCE" to "SOLDE",
        "Balance" to "Solde",
        "CRÉDIT DE VENTE" to "CRÉDIT DE VENTE",
        "LIMITE GAIN" to "LIMITE DE GAIN",
        "Illimité" to "Illimité",
        "NOUVEL FICH" to "NOUVELLE FICHE",
        "CHACHE FICH" to "CHERCHER UNE FICHE",
        "FICH MWEN YO" to "MES FICHES",
        "RAPÒ" to "RAPPORTS",
        "Bolèt" to "Loterie",
        "Paramèt" to "Paramètres",
        "Enprime" to "Imprimer",
        "Mesaj" to "Messages",
        "Plis opsyon" to "Plus d’options",
        "Rafrechi" to "Actualiser",
        "PLUS LOTTERIE · PLUS GROUP" to "PLUS LOTTERIE · PLUS GROUP",
        // Meni 3 pwen
        "Tiraj disponib" to "Tirages disponibles",
        "Boul ki sòti" to "Boules sorties",
        "Boul ki soti" to "Boules sorties",
        "Kont" to "Compte",
        "Gwosè ekran" to "Taille d’affichage",
        "Ap konekte..." to "Connexion...",
        "Ap telechaje mizajou a" to "Téléchargement de la mise à jour",
        "Mizajou a pa telechaje" to "La mise à jour n’a pas été téléchargée",
        "Eseye nan navigatè" to "Essayer dans le navigateur",
        "Fichye APK a poko disponib sou sit la. Eseye ankò pita oswa kontakte administratè a." to "Le fichier APK n’est pas encore disponible sur le site. Réessayez plus tard ou contactez l’administrateur.",
        "Telechajman an echwe. Verifye entènèt la epi eseye ankò." to "Le téléchargement a échoué. Vérifiez Internet et réessayez.",
        "Ap tann koneksyon an..." to "En attente de connexion...",
        "Telechajman fini. Klike \"Enstale\"." to "Téléchargement terminé. Touchez « Installer ».",
        "jodi a" to "aujourd’hui",
        "demen" to "demain",
        "nan {n} jou" to "dans {n} jours",
        "Vèsyon esè a prèske fini" to "La version d’essai se termine bientôt",
        "Bolèt sa a se yon esè 7 jou. L ap kanpe otomatikman {q}. Kontakte Plus Group pou n kreye bolèt la nèt." to "Cette borlette est un essai de 7 jours. Elle s’arrêtera automatiquement {q}. Contactez Plus Group pour créer la borlette définitive.",
        "Rele" to "Appeler",
        "Vèsyon APK" to "Version APK",
        "Ap tcheke mizajou..." to "Recherche de mise à jour...",
        "Pa ka tcheke mizajou kounye a. Verifye entènèt la." to "Impossible de vérifier les mises à jour. Vérifiez Internet.",
        "Ou gen dènye vèsyon an ({v})." to "Vous avez la dernière version ({v}).",
        "Gen yon nouvo vèsyon Plus Lotterie:" to "Une nouvelle version de Plus Lotterie est disponible :",
        "Vèsyon ou genyen an:" to "Votre version :",
        "Mizajou sa a obligatwa pou w kontinye vann." to "Cette mise à jour est obligatoire pour continuer à vendre.",
        "Nouvo mizajou disponib" to "Nouvelle mise à jour disponible",
        "TELECHAJE" to "TÉLÉCHARGER",
        "Pita" to "Plus tard",
        "Pèmèt \"Enstale aplikasyon enkoni\" pou Plus Lotterie, epi klike TELECHAJE ankò." to "Autorisez « Installer des applis inconnues » pour Plus Lotterie, puis touchez TÉLÉCHARGER à nouveau.",
        "Mizajou APK" to "Mise à jour APK",
        "Mizajou a ap telechaje... Gade notifikasyon an." to "Téléchargement de la mise à jour... Voir la notification.",
        "Resi an 2 kolòn (ekonomize papye)" to "Reçu sur 2 colonnes (économise le papier)",
        "AKTIF ✓" to "ACTIF ✓",
        "Fich yo ap enprime sou 2 kolòn." to "Les fiches seront imprimées sur 2 colonnes.",
        "Fich yo ap enprime sou 1 kolòn." to "Les fiches seront imprimées sur 1 colonne.",
        "Dekonekte" to "Se déconnecter",
        "Lang / Langue" to "Langue / Lang",
        // Login
        "Jwe, Tcheke, Genyen !" to "Jouez, Vérifiez, Gagnez !",
        "CHAJE SÈVÈ" to "CHARGER LES SERVEURS",
        "AP CHAJE..." to "CHARGEMENT...",
        "Konekte" to "Se connecter",
        "KONEKTE" to "SE CONNECTER",
        "Ap konekte..." to "Connexion...",
        "Mete idantifyan ak modpas ou." to "Saisissez votre identifiant et votre mot de passe.",
        "Antre idantifyan ak modpas ou." to "Saisissez votre identifiant et votre mot de passe.",
        "Idantifyan" to "Identifiant",
        "Modpas" to "Mot de passe",
        "Sèvè :" to "Serveur :",
        "Chwazi sèvè ou" to "Choisissez votre serveur",
        "Pa t ka chaje lis sèvè yo. Verifye entènèt la epi eseye ankò." to "Impossible de charger la liste des serveurs. Vérifiez Internet et réessayez.",
        "Pa t ka verifye aparèy la. Verifye entènèt ou epi eseye ankò." to "Impossible de vérifier l’appareil. Vérifiez Internet et réessayez.",
        // Ekran yo
        "Retounen" to "Retour",
        "Mes fiches" to "Mes fiches",
        "Debut" to "Début",
        "Fin" to "Fin",
        "Chèche" to "Rechercher",
        "Rechercher fiche" to "Rechercher une fiche",
        "Entrer Ticket" to "Saisir le ticket",
        "Scan" to "Scanner",
        "Rapport" to "Rapport",
        "Enprime rapò" to "Imprimer le rapport",
        "Partiel" to "Partiel",
        "F.Tirage" to "F.Tirage",
        "F.Gagnant" to "F.Gagnant",
        "F.Eliminer" to "F.Éliminer",
        "Trasact.." to "Transact..",
        "De" to "Du",
        "Tiraj" to "Tirage",
        "Tirage" to "Tirage",
        "Total mise" to "Total des mises",
        "Total gain" to "Total des gains",
        "Rapport partiel" to "Rapport partiel",
        "Rapport fin tirage" to "Rapport de fin de tirage",
        "Fiche vendu" to "Fiches vendues",
        "Fiche gagnant" to "Fiches gagnantes",
        "Fich elimine" to "Fiches éliminées",
        "Fich Elimine" to "Fiches éliminées",
        "Pa gen fich pou peryòd sa a." to "Aucune fiche pour cette période.",
        // Nouvèl fich
        "Efase tout" to "Tout effacer",
        "Pataje" to "Partager",
        "Boul" to "Boule",
        "Kòb" to "Montant",
        "Boul la" to "La boule",
        "Pri" to "Prix",
        "Pri pou chak boul" to "Prix par boule",
        "Pri pou chak liy" to "Prix par ligne",
        "Antre" to "Entrer",
        "Revè" to "Revers",
        "Maryaj" to "Mariage",
        "MARYAJ GRATIS" to "MARIAGES GRATUITS",
        "CHWAZI TIRAJ" to "CHOISIR LE TIRAGE",
        "CHWAZI TIRAJ (youn oswa plizyè)" to "CHOISIR LE(S) TIRAGE(S)",
        "CHWAZI KATEGORI" to "CHOISIR LA CATÉGORIE",
        "Chanje kategori" to "Changer de catégorie",
        "Modifye pri" to "Modifier le prix",
        "Efase liy" to "Supprimer la ligne",
        "Total: 0.00" to "Total : 0.00",
        "Chwazi yon Tiraj anvan ou ka vann" to "Choisissez un tirage avant de vendre",
        "Chwazi yon tiraj anvan" to "Choisissez d’abord un tirage",
        "Antre yon nimewo" to "Saisissez un numéro",
        "Antre pri a anvan" to "Saisissez d’abord le prix",
        "Antre yon pri valab" to "Saisissez un prix valide",
        "Nimewo dwe 2, 3, 4, oswa 5 chif" to "Le numéro doit avoir 2, 3, 4 ou 5 chiffres",
        "Antre yon nimewo 4 chif pou Loto4" to "Saisissez un numéro à 4 chiffres pour le Loto4",
        "Antre yon nimewo 5 chif pou Loto5" to "Saisissez un numéro à 5 chiffres pour le Loto5",
        "Antre 4 chif (egzanp 2532) pou Mariage" to "Saisissez 4 chiffres (ex. 2532) pour un mariage",
        "Tape 4 chif (Loto4) oswa 5 chif (Loto5)" to "Tapez 4 chiffres (Loto4) ou 5 chiffres (Loto5)",
        "Ajoute omwen 2 boul borlette anvan" to "Ajoutez d’abord au moins 2 boules borlette",
        "Ajoute omwen yon liy anvan w enprime" to "Ajoutez au moins une ligne avant d’imprimer",
        "Ou pa koche okenn opsyon" to "Aucune option cochée",
        "Pa gen liy pou enprime" to "Aucune ligne à imprimer",
        "Pa gen anyen pou enprime" to "Rien à imprimer",
        "Pa gen liy." to "Aucune ligne.",
        "Pa gen jwèt disponib" to "Aucun jeu disponible",
        "Pa gen kategori jwèt disponib" to "Aucune catégorie de jeu disponible",
        "Pa gen tiraj ouvè kounye a" to "Aucun tirage ouvert en ce moment",
        "Pa gen tiraj ouvè kounye a." to "Aucun tirage ouvert en ce moment.",
        "Pa t ka chaje tiraj yo. Verifye entènèt la." to "Impossible de charger les tirages. Vérifiez Internet.",
        "Revè: boul 2 chif sèlman (egz. 35 → 53)" to "Revers : boules à 2 chiffres seulement (ex. 35 → 53)",
        "Fich enprime avèk siksè!" to "Fiche imprimée avec succès !",
        "Fich voye bay enprimant lan" to "Fiche envoyée à l’imprimante",
        "Fich sa a te deja vann — n ap enprime l." to "Cette fiche est déjà vendue — impression en cours.",
        "Enprimant pa konekte." to "Imprimante non connectée.",
        "Enprimant pa konekte toujou" to "Imprimante toujours non connectée",
        "Fich chaje — ajoute/retire liy si w vle, epi enprime" to "Fiche chargée — ajoutez/retirez des lignes puis imprimez",
        "Yon sèl tiraj pa fich (Double tiraj fèmen)" to "Un seul tirage par fiche (double tirage désactivé)",
        "Tèks fich la kopye" to "Texte de la fiche copié",
        "Vire (3212 → 1232…)" to "Inverser (3212 → 1232…)",
        "Ak revè yo (3212 → 3221, 2312, 2321)" to "Avec les revers (3212 → 3221, 2312, 2321)",
        "Ak revè yo (32*12 → 32*21, 23*12, 23*21)" to "Avec les revers (32*12 → 32*21, 23*12, 23*21)",
        "Byento" to "Bientôt",
        // Fich / detay
        "Fich" to "Fiche",
        "Fich pa jwenn" to "Fiche introuvable",
        "Elimine fich la?" to "Éliminer la fiche ?",
        "Wi, elimine l" to "Oui, l’éliminer",
        "Fich la pa ka elimine" to "Impossible d’éliminer la fiche",
        "Peye fich la?" to "Payer la fiche ?",
        "Wi, peye" to "Oui, payer",
        "Fich la pa ka peye" to "Impossible de payer la fiche",
        "Fich la pa vann" to "Fiche non vendue",
        "Eskane QR ki sou fich la, oswa tape nimewo fich la." to "Scannez le QR de la fiche ou tapez son numéro.",
        "Antre nimewo fich la, oswa peze SCAN" to "Saisissez le numéro de fiche ou appuyez sur SCAN",
        "A paye" to "À payer",
        "ENPÒTAN" to "IMPORTANT",
        // Rezilta / mesaj
        "Rezilta tiraj yo" to "Résultats des tirages",
        "Jodi a" to "Aujourd’hui",
        "Yè" to "Hier",
        "7 jou" to "7 jours",
        "1e lo" to "1er lot",
        "2e lo" to "2e lot",
        "3e lo" to "3e lot",
        "Maten" to "Matin",
        "Midi" to "Midi",
        "Swa" to "Soir",
        "Bwat mesaj" to "Messagerie",
        "Mesaj bank ou a" to "Messages de votre banque",
        "Pa gen mesaj pou kounye a." to "Aucun message pour le moment.",
        "Pa t ka chaje mesaj yo.\nVerifye entènèt la." to "Impossible de charger les messages.\nVérifiez Internet.",
        "Pa t ka chaje rezilta yo.\nVerifye entènèt la epi peze ↻." to "Impossible de charger les résultats.\nVérifiez Internet puis appuyez sur ↻.",
        // Paramèt / Enprimant
        "Kont $" to "Compte $",
        "Mizajou" to "Mise à jour",
        "Enprimant" to "Imprimante",
        "Konfigire enprimant ou epi teste l." to "Configurez votre imprimante puis testez-la.",
        "Enprimant: ap konekte..." to "Imprimante : connexion...",
        "Chwazi enprimant Bluetooth" to "Choisir une imprimante Bluetooth",
        "Tès enprimant" to "Tester l’imprimante",
        "Pa gen aparèy Bluetooth pè. Al nan Paramèt > Bluetooth pou pè enprimant ou anvan." to "Aucun appareil Bluetooth appairé. Allez dans Paramètres > Bluetooth pour appairer votre imprimante.",
        "Rapò voye bay enprimant lan" to "Rapport envoyé à l’imprimante",
        "Piti" to "Petit",
        "Trè piti" to "Très petit",
        "Nòmal" to "Normal",
        "Gwo" to "Grand",
        // Dyalòg
        "Fèmen" to "Fermer",
        "Anile" to "Annuler",
        "Ajoute" to "Ajouter",
        "Non" to "Non",
        "OK" to "OK",
        "Wi, dekonekte" to "Oui, déconnecter",
        "Ou vle dekonekte sou aparèy sa a?" to "Voulez-vous vous déconnecter de cet appareil ?",
        // Kont
        "Non konplè" to "Nom complet",
        "Bank" to "Banque",
        "Succursal" to "Succursale",
        "Sikisal" to "Succursale",
        "Estati" to "Statut",
        "Total vant" to "Total des ventes",
        "Crédit de vente" to "Crédit de vente",
        "Limite gain" to "Limite de gain",
        "Pourcentage" to "Pourcentage",
        "Device id" to "Device ID",
        "Version : 1.0.0 @PlusGroup" to "Version : 1.0.0 @PlusGroup",
    )
}
