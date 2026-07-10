#!/usr/bin/env python3
"""Build high-quality Latin strings from the French reference + curated lexicon."""
from __future__ import annotations

import hashlib
import json
import re
import time
from pathlib import Path

from deep_translator import GoogleTranslator

ROOT = Path(__file__).resolve().parents[1]
CACHE_PATH = ROOT / ".i18n_la_fr_cache.json"

PH_RE = re.compile(
    r'(%\d+\$[sdif]|%[sdif]|\\n|\\u[0-9a-fA-F]{4}|&amp;|&lt;|&gt;|&#\d+;|\\\'|\\")'
)

STRING_RE = re.compile(
    r'(?P<pre>\s*)<string\s+name="(?P<name>[^"]+)"(?P<attrs>[^>]*)>(?P<body>.*?)</string>',
    re.DOTALL,
)

KEEP_AS_IS = {
    "app_name", "app_version",
    "language_english", "language_french", "language_spanish",
    "language_chinese", "language_chinese_simplified",
    "language_latin", "language_hindi",
    "venue_groove", "venue_le_terreau",
    "uid_label", "nfc_uid_label", "pos_title",
    "ble_reader_row_subtitle",
    "app_icon_creme_black", "app_icon_creme_black_applied",
    "email_placeholder", "guest_email_placeholder", "setup_spreadsheet_title",
    "date_format_dd_mm_yyyy", "date_format_mm_dd_yyyy", "date_of_birth_placeholder",
    "time_format_hh_mm", "time_format_hh_mm_ss", "time_format_hh_mm_ss_sss",
    "date_format_yyyy_mm_dd", "date_change_offset_time", "minutes_short",
    "easter_egg_hextris", "easter_egg_scroll", "easter_egg_pizza_undelivery",
    "easter_egg_wendol_village", "easter_egg_catculus",
    "arcade_credit_hextris", "arcade_credit_scroll", "arcade_credit_pizza_undelivery",
    "arcade_credit_wendol_village", "arcade_credit_catculus",
    "benefits_help_section_profité_title", "benefits_help_section_orion_title",
    "benefits_help_section_galaxie_title", "benefits_help_section_veteran_title",
    "benefits_help_dialog_subtitle", "email_association_name_hint", "email_signature_default",
}

# Longest phrases first — French → Latin (classical / neo-Latin UI style)
FR_LA_PHRASES: list[tuple[str, str]] = [
    ("Synchronisation manuelle maintenant", "Synchroniza manu nunc"),
    ("Dernière mise à jour :", "Ultima renovatio:"),
    ("Dernière mise à jour", "Ultima renovatio"),
    ("Paramètres et configuration", "Optiones et configuratio"),
    ("Paramètres & Configuration", "Optiones &amp; configuratio"),
    ("Paramètres & configuration", "Optiones &amp; configuratio"),
    ("Liste des invités", "Index hospitum"),
    ("Liste d'invités", "Index hospitum"),
    ("Ajouter un nouveau créneau", "Adde novum munus"),
    ("Ajouter un nouvel élément de vente", "Adde novum venditionis elementum"),
    ("Ajouter un type de créneau", "Adde genus muneris"),
    ("Ajouter un lieu", "Adde locum"),
    ("Ajouter de l'argent", "Adde pecuniam"),
    ("Retirer de l'argent", "Remove pecuniam"),
    ("Ajuster le solde du compte", "Compone stateram rationis"),
    ("Ajuster le compte", "Compone rationem"),
    ("Pas assez de crédit", "Creditum insufficient"),
    ("Vente terminée !", "Venditio perfecta!"),
    ("Vente terminée", "Venditio perfecta"),
    ("Panier vide", "Cistella vacua"),
    ("Valider la vente", "Confirma venditionem"),
    ("Scanner la carte", "Scande chartam"),
    ("Scanner carte ou QR", "Scande chartam vel QR"),
    ("En attente de carte", "Exspectans chartam"),
    ("Crédit insuffisant", "Creditum insufficient"),
    ("Crédit restant", "Creditum reliquum"),
    ("Montant manuel", "Summa manualis"),
    ("Montant du compte", "Summa rationis"),
    ("Ajustement manuel", "Compositio manualis"),
    ("Avertissement de sécurité", "Monitio securitatis"),
    ("Avertissement de Sécurité", "Monitio securitatis"),
    ("Authentification Admin", "Authenticatio administratoris"),
    ("Connexion admin par empreinte", "Aditus administratoris per impressionem"),
    ("Enregistrer la biométrie", "Registra biometriam"),
    ("Vérifier l'accès Admin", "Verifica aditum administratoris"),
    ("Je comprends les risques", "Intellego pericula"),
    ("Utiliser l'empreinte digitale", "Utere impressione digitali"),
    ("Utiliser QR / NFC à la place", "Utere QR / NFC potius"),
    ("Limite de taux dépassée", "Terminus festinationis transgressus"),
    ("Réessayer la synchro", "Iterum synchroniza"),
    ("Trop de requêtes", "Nimiae petitiones"),
    ("Fermer", "Claude"),
    ("Annuler", "Abrogare"),
    ("Annuler la vente", "Abroga venditionem"),
    ("Continuer", "Perge"),
    ("Terminé", "Factum"),
    ("Enregistrer", "Serva"),
    ("Supprimer", "Dele"),
    ("Rechercher", "Quaere"),
    ("Chargement", "Oneratur"),
    ("Partager", "Communica"),
    ("Télécharger", "Deprome"),
    ("Téléverser", "Imponere"),
    ("Synchronisation", "Synchronizatio"),
    ("Synchroniser", "Synchroniza"),
    ("Paramètres", "Optiones"),
    ("Configuration", "Configuratio"),
    ("Langue", "Lingua"),
    ("Thème", "Thema"),
    ("Sombre", "Obscurum"),
    ("Clair", "Clarum"),
    ("Invité", "Hospes"),
    ("Invitée", "Hospes"),
    ("Bénévole", "Voluntarius"),
    ("Bénévoles", "Voluntarii"),
    ("Créneau", "Munus"),
    ("Créneaux", "Munera"),
    ("Équipe", "Turma"),
    ("Description", "Descriptio"),
    ("Notes", "Notae"),
    ("Montant", "Summa"),
    ("Solde", "Statera"),
    ("Compte", "Ratio"),
    ("Vente", "Venditio"),
    ("Panier", "Cistella"),
    ("Total", "Summa"),
    ("Scanner", "Scanner"),
    ("Lecteur", "Lector"),
    ("Carte", "Charta"),
    ("Erreur", "Error"),
    ("Avertissement", "Monitio"),
    ("Sécurité", "Securitas"),
    ("Empreinte", "Impressio"),
    ("Administrateur", "Administrator"),
    ("Admin", "Administrator"),
    ("Actif", "Activus"),
    ("Active", "Activus"),
    ("Inactif", "Inactivus"),
    ("Activé", "Activatum"),
    ("Désactivé", "Deactivatum"),
    ("Oui", "Ita"),
    ("Non", "Non"),
    ("Tous", "Omnes"),
    ("Toutes", "Omnes"),
    ("Tout", "Omnia"),
    ("Autre", "Aliud"),
    ("Bar", "Bar"),
    ("Entrée", "Introitus"),
    ("Manuel", "Manualis"),
    ("Automatique", "Automaticus"),
    ("Devise", "Moneta"),
    ("Catégories", "Categoriae"),
    ("Catégorie", "Categoria"),
    ("Emoji", "Emoji"),
    ("Note", "Nota"),
    ("Email", "Epistula"),
    ("Téléphone", "Telephonum"),
    ("Nom", "Nomen"),
    ("Date", "Dies"),
    ("Heure", "Hora"),
    ("Lieu", "Locus"),
    ("Lieux", "Loci"),
    ("Invitation", "Invitatio"),
    ("Invitations", "Invitationes"),
    ("Actions", "Actiones"),
    ("Connexion", "Connexio"),
    ("Déconnexion", "Exi"),
    ("Fermer", "Claude"),
    ("Ouvrir", "Aperi"),
    ("Tester", "Proba"),
    ("Réessayer", "Iterum conare"),
    ("Fermé", "Clausum"),
    ("Réinitialiser", "Restitue"),
    ("Personnaliser", "Personaliza"),
    ("Couleur", "Color"),
    ("Bleu", "Caeruleum"),
    ("Vert", "Viride"),
    ("Gris", "Cinereum"),
    ("Blanc", "Album"),
    ("Noir", "Nigrum"),
    ("Orange", "Aurantium"),
    ("Rose", "Roseum"),
    ("Marron", "Fuscum"),
    ("Violet", "Violaceum"),
    ("Système", "Systema"),
    ("Tablette", "Tabula"),
    ("Ordinateur", "Computatrum"),
    ("Application", "Applicatio"),
    ("Appareil", "Instrumentum"),
    ("Appareils", "Instrumenta"),
    ("Bluetooth", "Bluetooth"),
    ("Caméra", "Camera"),
    ("Webcam", "Camera interretialis"),
    ("Fichier", "Fasciculus"),
    ("Fichiers", "Fasciculi"),
    ("Clé", "Clavis"),
    ("Projet", "Projectum"),
    ("Feuille", "Scheda"),
    ("Tableur", "Tabula computensis"),
    ("Google Sheets", "Google Sheets"),
    ("Gmail", "Gmail"),
    ("QR code", "Codex QR"),
    ("code QR", "codex QR"),
    ("badge NFC", "insignia NFC"),
    (" ou ", " vel "),
    (" du ", " ex "),
    (" des ", " ex "),
    (" la ", " "),
    (" le ", " "),
    (" les ", " "),
    (" un ", " "),
    (" une ", " "),
    (" l'", ""),
    (" d'", "de "),
    (" à ", " ad "),
    (" pour ", " pro "),
    (" dans ", " in "),
    (" avec ", " cum "),
    (" sans ", " sine "),
    (" sur ", " in "),
    (" par ", " per "),
    (" votre ", " tuum "),
    (" vos ", " tua "),
    (" vous ", " tu "),
    ("Veuillez", "Quaeso"),
    ("veuillez", "quaeso"),
    ("Impossible", "Impossibile"),
    ("disponible", "praesto"),
    ("indisponible", "non praesto"),
    ("activé", "activatum"),
    ("désactivé", "deactivatum"),
    ("activée", "activata"),
    ("désactivée", "deactivata"),
    ("maintenant", "nunc"),
    ("aujourd'hui", "hodie"),
    ("nouveau", "novus"),
    ("nouvelle", "nova"),
    ("nouveaux", "novi"),
    ("nouvelles", "novae"),
    ("ancien", "vetus"),
    ("ancienne", "vetera"),
    ("dernier", "novissimus"),
    ("dernière", "novissima"),
    ("prochain", "proximus"),
    ("prochaine", "proxima"),
    ("suivant", "sequens"),
    ("précédent", "prior"),
    ("précédente", "prior"),
    ("vide", "vacuus"),
    ("vide", "vacua"),
    ("vide", "vacuum"),
    ("plein", "plenus"),
    ("complète", "perfecta"),
    ("complété", "perfectum"),
    ("réussi", "successum"),
    ("échec", "defectum"),
    ("échoué", "defectum"),
    ("en cours", "in progressu"),
    ("chargement", "onerans"),
    ("recherche", "quaerens"),
    ("connexion", "connexio"),
    ("déconnecté", "disiunctum"),
    ("connecté", "coniunctum"),
    ("lecture", "lectio"),
    ("écriture", "scriptio"),
    ("lecture en cours", "lectio in cursu"),
    ("sauvegarder", "serva"),
    ("enregistré", "servatum"),
    ("supprimé", "deletum"),
    ("ajouté", "additum"),
    ("modifié", "mutatum"),
    ("créé", "creatum"),
    ("mis à jour", "renovatum"),
    ("mise à jour", "renovatio"),
    ("synchronisé", "synchronizatum"),
    ("synchronisation", "synchronizatio"),
    ("intervalle", "intervallum"),
    ("fréquence", "frequentia"),
    ("paramètre", "optio"),
    ("paramètres", "optiones"),
    ("réglage", "optio"),
    ("réglages", "optiones"),
    ("option", "optio"),
    ("options", "optiones"),
    ("aperçu", "prospectus"),
    ("aperçu", "praevius"),
    ("icône", "icon"),
    ("icône de l'application", "icon applicationis"),
    ("résolution", "resolutio"),
    ("échelle", "scala"),
    ("taille", "magnitudo"),
    ("police", "typus"),
    ("arrière-plan", "fons"),
    ("animation", "animatio"),
    ("page", "pagina"),
    ("écran", "tabula"),
    ("écrans", "tabulae"),
    ("bouton", "puga"),
    ("boutons", "pugae"),
    ("menu", "index"),
    ("liste", "index"),
    ("tableau", "tabula"),
    ("graphique", "graphicum"),
    ("statistiques", "statistica"),
    ("distribution", "distributio"),
    ("répartition", "distributio"),
    ("genre", "genus"),
    ("âge", "aetas"),
    ("inconnu", "ignotus"),
    ("inconnue", "ignota"),
    ("non spécifié", "non specificatum"),
    ("facultatif", "liberum"),
    ("obligatoire", "necessarium"),
    ("requis", "requiritur"),
    ("permis", "licitum"),
    ("interdit", "vetitum"),
    ("privé", "privatum"),
    ("public", "publicum"),
    ("interne", "internum"),
    ("externe", "externum"),
    ("lecteur externe", "lector externus"),
    ("lecteur NFC", "lector NFC"),
    ("lecteur USB", "lector USB"),
    ("lecteur connecté", "lector coniunctus"),
    ("aucun lecteur", "nullus lector"),
    ("carte non reconnue", "charta non cognita"),
    ("profil", "profilum"),
    ("client", "emptor"),
    ("cliente", "emptor"),
    ("profil reconnu", "profilum cognitum"),
    ("changer de profil", "Muta profilum"),
    ("vider", "Vacua"),
    ("effacer", "Dele"),
    ("retour", "Reverte"),
    ("retour arrière", "Reverte"),
    ("ajouter au panier", "Adde ad cistellam"),
    ("payer", "Solve"),
    ("payer et valider", "Solve et confirma"),
    ("espèces", "pecunia"),
    ("carte bancaire", "charta"),
    ("paiement", "solutio"),
    ("paiement en espèces", "solutio pecuniae"),
    ("remise", "discountio"),
    ("rabais", "discountio"),
    ("réduction", "discountio"),
    ("au bar", "ad bar"),
    ("bar", "bar"),
    ("entrée", "introitus"),
    ("merch", "merch"),
    ("autre", "aliud"),
    ("transferts", "translationes"),
    ("transfert", "translatio"),
    ("transferts récents", "translationes recentes"),
    ("montrer", "Ostende"),
    ("masquer", "Cela"),
    ("tout", "omnia"),
    ("moins", "minus"),
    ("plus", "plus"),
    ("tous les", "omnes"),
    ("toutes les", "omnes"),
    ("aucune", "nulla"),
    ("aucun", "nullus"),
    ("sélectionner", "Elige"),
    ("choisir", "Elige"),
    ("choisir un emoji", "Elige emoji"),
    ("filtre", "filtrum"),
    ("trier", "ordina"),
    ("ordre", "ordo"),
    ("croissant", "ascendens"),
    ("décroissant", "descendens"),
    ("actuel", "actualis"),
    ("actuelle", "actualis"),
    ("courant", "usualis"),
    ("courante", "usualis"),
    ("par défaut", "ex consuetudine"),
    ("défaut", "defectus"),
    ("personnalisé", "personalizatum"),
    ("personnalisée", "personalizata"),
    ("personnalisation", "personalizatio"),
    ("éditeur", "editor"),
    ("éditeur de thème", "Editor thematis"),
    ("teinte", "tinctura"),
    ("saturation", "saturatio"),
    ("luminosité", "luminositas"),
    ("luminosité", "claritas"),
    ("comment corriger", "Quomodo corrigere"),
    ("comment", "quomodo"),
    ("redémarrer", "restitue"),
    ("redémarrez", "restitue"),
    ("réessayez", "iterum conare"),
    ("réessayer", "iterum conare"),
    ("courant sur", "frequens in"),
    ("anciennes tablettes", "tabulis veteribus"),
    ("vérifiez", "inspice"),
    ("vérifiez les paramètres", "inspice optiones"),
    ("appareil", "instrumentum"),
    ("identité", "identitas"),
    ("identité admin", "identitas administratoris"),
    ("vérifiée", "verificata"),
    ("vérifier", "verifica"),
    ("confirmer", "confirma"),
    ("confirmez", "confirma"),
    ("annulez", "abroga"),
    ("fermez", "claude"),
    ("ouvrez", "aperi"),
    ("fermer", "claude"),
    ("annuler", "abrogare"),
    ("continuer", "perge"),
    ("terminé", "factum"),
    ("terminée", "perfecta"),
    ("terminer", "perfice"),
    ("commencer", "incipe"),
    ("démarrer", "incipe"),
    ("arrêter", "desine"),
    ("pause", "pausa"),
    ("reprendre", "resume"),
    ("ignorer", "omitte"),
    ("passer", "transi"),
    ("sauter", "transi"),
    ("ignorer pour l'instant", "Omitte in praesens"),
    ("ignorer pour le moment", "Omitte in praesens"),
    ("type", "typus"),
    ("types", "typi"),
    ("statut", "status"),
    ("état", "status"),
    ("statut de synchronisation", "status synchronizationis"),
    ("synchronisation manuelle", "synchronizatio manualis"),
    ("synchronisation automatique", "synchronizatio automatica"),
    ("auto-sync", "synchronizatio automatica"),
    ("synchronisation manuelle", "synchronizatio manualis"),
    ("intervalle de synchronisation", "intervallum synchronizationis"),
    ("tester la connexion", "Proba connexionem"),
    ("tester le lecteur", "Proba lectorem"),
    ("connexion au lecteur", "connexio lectoris"),
    ("oublier", "Obliviscere"),
    ("oublié", "obliviscere"),
    ("mémorisé", "memoratum"),
    ("enregistré", "servatum"),
    ("sauvegardé", "servatum"),
    ("importé", "importatum"),
    ("exporté", "exportatum"),
    ("valide", "validum"),
    ("invalide", "invalidum"),
    ("validation", "validatio"),
    ("validation échouée", "validatio defecit"),
    ("téléchargement", "depromens"),
    ("téléchargement terminé", "depromens perfectum"),
    ("mise à jour disponible", "renovatio praesto"),
    ("aucune mise à jour", "nulla renovatio"),
    ("version", "versio"),
    ("informations", "informationes"),
    ("information", "informatio"),
    ("aide", "auxilium"),
    ("aide sur", "auxilium de"),
    ("instructions", "instructiones"),
    ("guide", "dux"),
    ("documentation", "documentatio"),
    ("journal", "diarium"),
    ("journaux", "diaria"),
    ("contenu du journal", "contentum diarii"),
    ("emplacement", "locus"),
    ("chemin", "via"),
    ("dossier", "folder"),
    ("répertoire", "index"),
    ("source", "fons"),
    ("sources", "fontes"),
    ("URL", "URL"),
    ("URL personnalisée", "URL proprium"),
    ("manifeste", "manifestum"),
    ("magasin d'applications", "taberna applicationum"),
    ("magasin", "taberna"),
    ("boutique", "taberna"),
    ("annonce", "nuntius"),
    ("annonces", "nuntii"),
    ("message", "nuntius"),
    ("messages", "nuntii"),
    ("titre", "titulus"),
    ("sous-titre", "subtitulus"),
    ("contenu", "contentum"),
    ("corps", "corpus"),
    ("signature", "subscriptio"),
    ("objet", "res"),
    ("expéditeur", "mittens"),
    ("destinataire", "destinatarium"),
    ("pièce jointe", "adnexum"),
    ("logo", "logo"),
    ("image", "imago"),
    ("images", "imagines"),
    ("photo", "photographia"),
    ("photos", "photographiae"),
    ("vidéo", "video"),
    ("audio", "audio"),
    ("son", "sonus"),
    ("volume", "volumen"),
    ("muet", "mutum"),
    ("notification", "notificatio"),
    ("notifications", "notificationes"),
    ("alerte", "monitio"),
    ("alertes", "monitiones"),
    ("rappel", "monitio"),
    ("rappels", "monitiones"),
    ("minute", "minutum"),
    ("minutes", "minuta"),
    ("heures", "horae"),
    ("jours", "dies"),
    ("semaines", "hebdomades"),
    ("mois", "menses"),
    ("année", "annus"),
    ("années", "anni"),
    ("année", "annus"),
    ("mois", "mensis"),
    ("semaine", "hebdomas"),
    ("jour", "dies"),
    ("matin", "mane"),
    ("soir", "vespera"),
    ("nuit", "nox"),
    ("après-midi", "postmeridianum"),
    ("midi", "meridies"),
    ("minuit", "media nox"),
    ("plage horaire", "tempus spatium"),
    ("horaire", "horarium"),
    ("calendrier", "calendarium"),
    ("planning", "consilium"),
    ("planification", "consilium"),
    ("événement", "eventus"),
    ("événements", "eventus"),
    ("festival", "festivitas"),
    ("soirée", "vespera"),
    ("soir", "vespera"),
    ("profiter de la soirée", "frui vespera"),
    ("bénéfice", "beneficium"),
    ("bénéfices", "beneficia"),
    ("avantage", "commodum"),
    ("avantages", "commoda"),
    ("récompense", "praemium"),
    ("récompenses", "praemia"),
    ("crédit", "creditum"),
    ("crédits", "credita"),
    ("débit", "debitum"),
    ("débits", "debita"),
    ("portefeuille", "crumena"),
    ("budget", "budgetum"),
    ("prix", "pretium"),
    ("prix unitaire", "pretium unitarium"),
    ("quantité", "quantitas"),
    ("article", "res"),
    ("articles", "res"),
    ("produit", "productum"),
    ("produits", "producta"),
    ("élément", "elementum"),
    ("éléments", "elementa"),
    ("élément de vente", "elementum venditionis"),
    ("éléments de vente", "elementa venditionis"),
    ("inventaire", "inventarium"),
    ("stock", "copia"),
    ("rupture de stock", "copia exhausta"),
    ("disponible", "praesto"),
    ("indisponible", "non praesto"),
    ("épuisé", "exhaustum"),
    ("limité", "limitatum"),
    ("illimité", "illimitatum"),
    ("maximum", "maximum"),
    ("minimum", "minimum"),
    ("moyenne", "medium"),
    ("moyen", "medium"),
    ("niveau", "gradus"),
    ("niveaux", "gradus"),
    ("rang", "gradus"),
    ("grade", "gradus"),
    ("priorité", "prioritas"),
    ("urgent", "urgens"),
    ("normal", "normale"),
    ("bas", "humile"),
    ("basse", "humilis"),
    ("haut", "altum"),
    ("haute", "alta"),
    ("élevé", "altum"),
    ("élevée", "alta"),
    ("faible", "humile"),
    ("fort", "fortis"),
    ("forte", "fortis"),
    ("faible", "infirmum"),
    ("rapide", "celer"),
    ("lent", "tardus"),
    ("lente", "tarda"),
    ("immédiat", "confestim"),
    ("immédiate", "confestim"),
    ("instantané", "confestim"),
    ("instantanée", "confestim"),
    ("automatique", "automaticus"),
    ("automatiques", "automatici"),
    ("automatiquement", "automatico"),
    ("manuel", "manualis"),
    ("manuelle", "manualis"),
    ("manuellement", "manu"),
    ("semi-automatique", "semiautomaticus"),
    ("débogage", "debug"),
    ("dépannage", "problemata solvenda"),
    ("journalisation détaillée", "diarium detaillatum"),
    ("Activer la journalisation détaillée pour le dépannage", "Activa diarium detaillatum ad problemata solvenda"),
    ("Téléchargez ", "Deprome "),
    ("Remplacer ", "Substitue "),
    ("trouvée", "inventa"),
    ("trouvé", "inventus"),
    ("Aperçu ", "Prospectus "),
    ("Voir ", "Vide "),
    ("depuis ", "ex "),
    ("Cette ", "Haec "),
    ("Ce ", "Hic "),
    ("ne doit pas être", "non debet esse"),
    ("ne pas être", "non esse"),
    ("modifiée", "mutata"),
    ("modifié", "mutatum"),
    ("semi-automatiques", "semiautomatici"),
]

# Sort by length descending for greedy replacement
FR_LA_PHRASES.sort(key=lambda x: len(x[0]), reverse=True)

# Second pass: fix leftover French fragments after greedy phrase replacement
LA_POST_CLEANUP: list[tuple[str, str]] = [
    ("Format de date et d'heure", "Forma diei et temporis"),
    ("Format de date et de heure", "Forma diei et temporis"),
    ("Format de date etde heure", "Forma diei et temporis"),
    ("Bonus et dates", "Praemia et tempora"),
    ("Bonuses &amp; timing", "Praemia &amp; tempora"),
    ("Configuratio de compte de service", "Configuratio rationis ministerii"),
    ("compte de service", "ratio ministerii"),
    ("Clé de compte de service", "Clavis rationis ministerii"),
    ("Clavis de compte de service", "Clavis rationis ministerii"),
    ("Configuratio de synchronizatio", "Configuratio synchronizationis"),
    ("Intervalle de synchronizatio", "Intervallum synchronizationis"),
    ("Statut de synchronizatio", "Status synchronizationis"),
    ("Error lors de test de status synchronizationis", "Error in probatione status synchronizationis"),
    ("Error lors de lectio de fichier diarium", "Error in lectione fasciculi diarii"),
    ("Contenu de diarium", "Contentum diarii"),
    ("Fichiers journaux récents", "Fasciculi diarii recentes"),
    ("Mode de débogage", "Modus debug"),
    ("Journaux de débogage", "Diaria debug"),
    ("Arrière-plan animé", "Fons animatus"),
    ("Arrière-plan billetterie", "Fons billetariae"),
    ("Intensité de l'arrière-plan", "Intensitas fondi"),
    ("Appliquer l'intensité", "Applica intensitatem"),
    ("ID de feuille de calcul", "ID schedae computensis"),
    ("feuille de calcul", "scheda computensis"),
    ("Feuille liste des invités", "Scheda indici hospitum"),
    ("Feuille liste invités bénévoles", "Scheda indici hospitum voluntariorum"),
    ("Feuille des bénévoles", "Scheda voluntariorum"),
    ("Feuille guest-list temporaire", "Scheda indici hospitum temporaria"),
    ("Scheda de Shifts", "Scheda munerum"),
    ("Scheda typi de Shifts", "Scheda typorum munerum"),
    ("Guest List Sheet", "Scheda indici hospitum"),
    ("Volunteer Guest List Sheet", "Scheda indici hospitum voluntariorum"),
    ("Guest List", "Index hospitum"),
    ("Guestlist", "Index hospitum"),
    ("guest-list", "index-hospitum"),
    ("date/heure", "dies/hora"),
    ("Dies/heure", "Dies/hora"),
    ("Dies et heure", "Dies et tempus"),
    ("Horodatage (ms) → date/heure", "Timestamp (ms) → dies/hora"),
    ("Dies/heure → horodatage (ms)", "Dies/hora → timestamp (ms)"),
    ("Choisir vel saisir date/heure", "Elige vel inscribe dies/horam"),
    ("Configurer synchronizatio", "Configura synchronizationem"),
    ("Configurer ", "Configura "),
    ("Obtenez ceci depuis", "Accipe hoc ex"),
    ("depuisURL", "ex URL"),
    ("Téléchargez votre fichier de clé JSON", "Deprome fasciculum clavis JSON tuum"),
    ("Remplacer le fichier de clé", "Substitue fasciculum clavis"),
    ("Télécharger le fichier de clé", "Deprome fasciculum clavis"),
    ("Choisissez depuis combien de temps", "Elige ex quo tempore"),
    ("Aperçu des bénévoles à supprimer", "Prospectus voluntariorum delendorum"),
    ("Aucun·e bénévole inactif·ve", "Nullus voluntarius inactivus"),
    ("n'a été trouvé·e", "non inventus est"),
    ("Voir le plus récent", "Vide recentissimum"),
    ("Échec de la vérification des mises à jour", "Defectum in verificatione renovationum"),
    ("Voir instructiones de configuration", "Vide instructiones configurationis"),
    ("Voir les instructiones", "Vide instructiones"),
    ("Proba connexio", "Proba connexionem"),
    ("Ajouter novus Shift", "Adde novum munus"),
    ("Ajouter novum Shift", "Adde novum munus"),
    ("Tester la connexion", "Proba connexionem"),
    ("Synchroniza manu nunc", "Synchroniza manu nunc"),
    ("Omniae modification de cette pagina", "Omnis mutatio huius paginae"),
    ("ne sera pas servatume perapplication", "non servabitur ab applicatione"),
    ("perapplication", "ab applicatione"),
    ("optioes deapplication", "optiones applicationis"),
    ("deapplication", "applicationis"),
    ("bénévoles", "voluntarii"),
    ("bénévole", "voluntarius"),
    ("invités", "hospitum"),
    ("invitées", "hospitae"),
    ("invité", "hospes"),
    ("inactif·ve", "inactivus"),
    ("inactifs", "inactivi"),
    ("Shifts", "Munera"),
    ("Shift", "Munus"),
    ("Debug Mode", "Modus debug"),
    ("lors de ", "in "),
    ("  ", " "),
]
LA_POST_CLEANUP.sort(key=lambda x: len(x[0]), reverse=True)

FRENCH_REMNANT_RE = re.compile(
    r"\b(Configurer|Choisissez|Saisissez|Cliquez|Obtenez|depuis|cette|ces|dans|pour|avec|"
    r"trouvé|trouvée|aperçu|contenu|fichier|erreur|lors|appliquer|voir|aucun|aucune|"
    r"heure|format|depuis|depuis|depuis|depuis|depuis|depuis|depuis)\b",
    re.IGNORECASE,
)

MANUAL_LA: dict[str, str] = {
    "biometric_warning_message": (
        "Quilibet cum codice clausurae huius instrumenti impressionem digitalem addere potest "
        "et ita ad paginam Administratoris accedere.\\n\\n"
        "Hoc vehementer vetitum est in instrumentis publico patentibus vel apud homines qui "
        "privata haec non debent videre.\\n\\n"
        "Haec functio TANTUM pro usoribus qui pericula intellegunt et accipiunt.\\n\\n"
        "Ut procedas, identitas tua administratoris primum per codicem QR vel insignia NFC confirmabitur."
    ),
    "sync_error_device_time_solution": (
        "Quomodo corrigere:\\n\\n"
        "1. Vade ad Optiones\\n"
        "2. Quaere \"Dies &amp; tempus\" vel \"Systema\"\\n"
        "3. Activa \"Dies &amp; tempus automatica\" vel \"Tempus automatice statue\"\\n"
        "4. Si non praesto est, die et tempus recta manu constitue\\n"
        "5. Applicationem restitue et synchronizationem iterum tempta\\n\\n"
        "Frequens in tabulis veteribus — quaeso optiones instrumenti inspice!"
    ),
    "step_3_description": (
        "Crea rationem ministerii in IAM &amp; Admin > Service Accounts. Deprome fasciculum clavis JSON."
    ),
    "future_entry_invites_helper": (
        "Adhibitum ad &quot;+n inv.&quot; in futuris introitibus semel usurpandis ostendendum."
    ),
    "pos_cash_payment_message": (
        "Ratio %1$s tegit. Solve %2$s pecunia vel charta ut venditionem perficias?"
    ),
    "benefits_help_section_profité_body": (
        "Solum munus ex consuetudine, cum \"frui vespera\" activum est.\\n"
        "Non de introitu: utrum horae muneris adhuc permittant homini noctem frui necne.\\n"
        "Alia genera Nova: haec optio latet."
    ),
    "settings_title": "Optiones &amp; configuratio",
    "nav_settings_tablet": "Optiones &amp; configuratio",
    "volunteer_guest_sheet_last_updated": "Ultima renovatio: %1$s",
    "manual_sync_now": "Synchroniza manu nunc",
    "theme_dark": "Obscurum",
    "theme_light": "Clarum",
    "cancel": "Abrogare",
    "close": "Claude",
    "done": "Factum",
    "continue_label": "Perge",
    "account_add_money": "Adde pecuniam",
    "account_remove_money": "Remove pecuniam",
    "adjust_account_title": "Compone stateram rationis",
    "amount_label": "Summa",
    "note_label": "Nota",
    "announcement_cancel_button": "Abrogare",
    "biometric_warning_cancel": "Abrogare",
    "pos_cash_payment_cancel": "Abroga venditionem",
    "admin_label": "Administrator",
    "admin_mode": "Administrator",
    "guest_email": "Epistula",
    "email": "Epistula",
    "actions": "Actiones",
    "active": "Activus",
    "active_status": "Activus",
    "benefits_help_tab_extras": "Praemia &amp; tempora",
    "date_time_format_title": "Forma diei &amp; temporis",
    "date_time_label": "Dies &amp; tempus",
    "guest_list_sheet_label": "Scheda indici hospitum",
    "volunteer_sheet_label": "Scheda voluntariorum",
    "volunteer_guest_list_sheet_label": "Scheda indici hospitum voluntariorum",
    "spreadsheet_id_label": "ID schedae computensis",
    "debug_mode_title": "Modus debug",
    "test_connection": "Proba connexionem",
    "add_new_shift": "Adde novum munus",
    "email_gmail_oauth_step_2_description": (
        "In APIs &amp; Services &gt; Library, quaere \"Gmail API\" et activa."
    ),
    "email_gmail_oauth_step_3_description": (
        "In APIs &amp; Services &gt; OAuth consent screen, reple campum necessaria. "
        "Si applicatio in modo Testing est, adde epistulam Gmail tuam ut usor probationis."
    ),
    "email_gmail_oauth_step_4_description": (
        "In APIs &amp; Services &gt; Credentials, preme Create Credentials &gt; OAuth client ID. "
        "Elige Application type Desktop app, deinde crea."
    ),
    "email_gmail_service_account_help_step_3_description": (
        "In optionibus E-mail, activa toggle rationis ministerii, inscribe adresse Workspace "
        "quae epistulas mittere debet, deinde preme Sign in with Google ad validandum."
    ),
    "volunteer_guest_sheet_banner_title": (
        "NE MUTA — Index hospitum beneficia voluntariorum"
    ),
    "volunteer_guest_sheet_banner_l2": (
        "Haec pagina non mutanda est."
    ),
    "volunteer_guest_sheet_banner_l4": (
        "Haec scheda est eventus impositionis applicationis, post calculum commodorum voluntariorum. "
        "Haec scheda non mutanda est."
    ),
    "service_account_key_found": "Clavis rationis ministerii inventa",
    "upload_key_file_description": (
        "Deprome fasciculum clavis JSON rationis ministerii Google tuum"
    ),
    "upload_key_description": (
        "Deprome fasciculum clavis JSON rationis ministerii Google tuum"
    ),
    "replace_key_file": "Substitue fasciculum clavis",
    "upload_key_file": "Deprome fasciculum clavis",
    "sync_status_not_configured": (
        "Google Sheets non configuratum est. Quaeso optiones tuas inspice."
    ),
    "benefits_help_app_manual": (
        "Tu ipse inscribis dies, potiones, bar \\u0025, hospitae, extras\\n"
        "Adhibe pro casibus singularibus extra consuetam tabulam\\n"
        "Cave ne idem munus bis numeres si iam in modo Stellaire captum est"
    ),
    "cleanup_no_volunteers_found": (
        "Nullus voluntarius inactivus ex %1$d annis vel plus inventus est."
    ),
    "debug_mode_description": (
        "Activa diarium detaillatum ad problemata solvenda"
    ),
    "benefits_help_footer_title": "Notae utiles",
    "benefits_help_footer_bullets": (
        "Profité / non-profité — utrum horae muneris permiserint frui vespera necne "
        "(non de introitu)\\n"
        "50\\u0025 ad bar sequitur pretia Lightspeed\\n"
        "Galaxie cum commodis per munus componitur"
    ),
    "sales_discount_label": "Discountio applicatur",
    "invalid_qr_code_format": "Forma codicis QR invalida: %1$s",
    "announcements_tracked_venues_title": "Loci observati",
    "announcements_tracked_venues_description": (
        "Elige ex quibus locis nuntios accipere vis"
    ),
    "announcements_tracked_venues_all": "Omnes loci",
    "nova_job_type_label": "Typus commodi Nova",
    "nova_type_graphic_designer_event": "Graphista (pro eventu)",
    "nova_type_graphic_designer_association": "Graphista (pro associatione)",
    "admin_setup_type_title": "Typus administratoris",
}

# Android EN uses different placeholders than Compose for some keys
MANUAL_LA_ANDROID: dict[str, str] = {
    "cleanup_no_volunteers_found": (
        "Nullus voluntarius inactivus ex %d annis vel plus inventus est."
    ),
    "invalid_qr_code_format": "Forma codicis QR invalida: %s",
    "benefits_help_footer_bullets": (
        "Profité / non-profité — utrum horae muneris permiserint frui vespera necne "
        "(non de introitu)\\n"
        "50\\u0025 ad bar sequitur pretia Lightspeed\\n"
        "Galaxie cum commodis per munus componitur"
    ),
}

HI_MANUAL: dict[str, str] = {
    "future_entry_invites_helper": (
        "भविष्य की एकल-उपयोग प्रविष्टियों पर दिखाए गए \"+n inv.\" के लिए उपयोग किया जाता है।"
    ),
    "step_3_description": (
        "IAM &amp; Admin > Service Accounts में एक सेवा खाता बनाएँ। JSON कुंजी फ़ाइल डाउनलोड करें।"
    ),
    "guest_email": "ईमेल",
    "volunteer_qr_code": "स्वयंसेवक QR कोड",
    "guest_phone_placeholder": "+91 98765 43210",
}


def has_french_diacritics(s: str) -> bool:
    return bool(re.search(r"[àâäéèêëïîôùûüçœæÀÂÄÉÈÊËÏÎÔÙÛÜÇŒÆ]", s))


def normalize_inclusive_fr(text: str) -> str:
    """Turn French inclusive middle-dot forms into plain words for lexicon matching."""
    text = re.sub(r"·e·s\b", "s", text)
    text = re.sub(r"·e\b", "e", text)
    text = re.sub(r"·", "", text)
    return text


def post_cleanup_la(text: str) -> str:
    result = text
    for fr, la in LA_POST_CLEANUP:
        result = result.replace(fr, la)
    # Remove leftover French articles only as whole words
    result = re.sub(r"\bde\b", "", result, flags=re.IGNORECASE)
    result = re.sub(r"\bdu\b", "ex", result, flags=re.IGNORECASE)
    result = re.sub(r"\bdes\b", "ex", result, flags=re.IGNORECASE)
    result = re.sub(r"\bla\b", "", result, flags=re.IGNORECASE)
    result = re.sub(r"\ble\b", "", result, flags=re.IGNORECASE)
    result = re.sub(r"\bles\b", "", result, flags=re.IGNORECASE)
    result = re.sub(r"  +", " ", result)
    result = re.sub(r" +([,.;:!?])", r"\1", result)
    return result.strip()


def fr_to_la(text: str) -> str:
    text = normalize_inclusive_fr(text)
    result = text
    for fr, la in FR_LA_PHRASES:
        result = result.replace(fr, la)
    result = post_cleanup_la(result)
    # Second lexicon pass catches newly adjacent tokens
    for fr, la in FR_LA_PHRASES:
        result = result.replace(fr, la)
    result = post_cleanup_la(result)
    return result.strip()


def load_cache() -> dict[str, str]:
    if CACHE_PATH.exists():
        return json.loads(CACHE_PATH.read_text(encoding="utf-8"))
    return {}


def save_cache(cache: dict[str, str]) -> None:
    CACHE_PATH.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")


def translate_segments_fr(text: str, cache: dict[str, str]) -> str:
    if not text:
        return text
    parts = PH_RE.split(text)
    translator = GoogleTranslator(source="fr", target="la")
    out: list[str] = []
    for part in parts:
        if not part:
            continue
        if PH_RE.fullmatch(part):
            out.append(part)
            continue
        stripped = part.strip()
        if not stripped:
            out.append(part)
            continue
        key = hashlib.sha1(f"fr-la|{part}".encode()).hexdigest()
        if key in cache:
            translated = cache[key]
        else:
            try:
                translated = translator.translate(part) or part
            except Exception:
                translated = part
            cache[key] = translated
            time.sleep(0.03)
        if part != stripped:
            prefix = part[: len(part) - len(part.lstrip())]
            suffix = part[len(part.rstrip()) :]
            out.append(prefix + translated + suffix)
        else:
            out.append(translated)
    return "".join(out)


def needs_mt_fix(la_body: str) -> bool:
    if FRENCH_REMNANT_RE.search(la_body):
        return True
    if has_french_diacritics(la_body):
        return True
    if re.search(
        r"(umum|n'existe|Choisissez|Choisir|Sélectionnez|Remarques|Réduction|savoir|"
        r"Graphiste|Typede|pourassociation|poureventus|Aucun|Aucune|pour |avec |dans |"
        r"les |des |une |est )",
        la_body,
    ):
        return True
    return False


def parse_strings(path: Path) -> tuple[str, list[re.Match]]:
    text = path.read_text(encoding="utf-8")
    return text, list(STRING_RE.finditer(text))


def write_locale(src_path: Path, dest_path: Path, bodies: dict[str, str]) -> None:
    text, matches = parse_strings(src_path)
    out: list[str] = []
    last = 0
    for m in matches:
        out.append(text[last : m.start()])
        name = m.group("name")
        body = bodies.get(name, m.group("body"))
        out.append(f'{m.group("pre")}<string name="{name}"{m.group("attrs")}>{body}</string>')
        last = m.end()
    out.append(text[last:])
    dest_path.write_text("".join(out), encoding="utf-8")


def build_la_from_fr(en_path: Path, fr_path: Path, la_path: Path, cache: dict[str, str]) -> None:
    _, en_matches = parse_strings(en_path)
    en_bodies = {m.group("name"): m.group("body") for m in en_matches}
    _, fr_matches = parse_strings(fr_path)
    fr_bodies = {m.group("name"): m.group("body") for m in fr_matches}
    la_bodies: dict[str, str] = {}
    french_left = 0
    mt_fixed = 0
    for name, en_body in en_bodies.items():
        if name in KEEP_AS_IS:
            la_bodies[name] = en_body
            continue
        if name in MANUAL_LA:
            la_bodies[name] = MANUAL_LA[name]
            if "app/src/main" in str(en_path) and name in MANUAL_LA_ANDROID:
                la_bodies[name] = MANUAL_LA_ANDROID[name]
            continue
        fr_body = fr_bodies.get(name, en_body)
        la_body = fr_to_la(fr_body)
        if has_french_diacritics(la_body):
            french_left += 1
        if needs_mt_fix(la_body) and fr_body != en_body:
            la_body = translate_segments_fr(fr_body, cache)
            mt_fixed += 1
            if mt_fixed % 50 == 0:
                save_cache(cache)
        la_bodies[name] = la_body
    write_locale(en_path, la_path, la_bodies)
    save_cache(cache)
    print(f"  {la_path.name}: french_chars_remaining={french_left} mt_fixed={mt_fixed}")


def patch_hi(hi_path: Path, en_path: Path) -> None:
    _, en_matches = parse_strings(en_path)
    en_bodies = {m.group("name"): m.group("body") for m in en_matches}
    _, hi_matches = parse_strings(hi_path)
    hi_bodies = {m.group("name"): m.group("body") for m in hi_matches}
    for k, v in HI_MANUAL.items():
        hi_bodies[k] = v
    write_locale(en_path, hi_path, hi_bodies)


def audit(path: Path, en_path: Path) -> None:
    en = {m.group("name"): m.group("body") for m in parse_strings(en_path)[1]}
    tr = {m.group("name"): m.group("body") for m in parse_strings(path)[1]}
    corrupt = sum(1 for v in tr.values() if re.search(r"⟦|⟧|<\d+>|&lt;\d+&gt;", v))
    ph_re = re.compile(r"(%\d+\$[sdif]|%[sdif]|\\n|\\u[0-9a-fA-F]{4}|&amp;|&lt;|&gt;|>)")
    ph_bad = sum(1 for k, v in tr.items() if k in en and ph_re.findall(en[k]) != ph_re.findall(v))
    same = sum(1 for k, v in tr.items() if k in en and v == en[k] and k not in KEEP_AS_IS and len(v) > 3)
    fr_left = sum(1 for v in tr.values() if has_french_diacritics(v))
    mixed = sum(1 for k, v in tr.items() if k not in KEEP_AS_IS and FRENCH_REMNANT_RE.search(v))
    print(
        f"  audit {path.parent.name}: corrupt={corrupt} ph_bad={ph_bad} "
        f"same_as_en={same} french_chars={fr_left} french_remnants={mixed}"
    )


def main() -> None:
    cache = load_cache()
    jobs = [
        (
            ROOT / "shared/src/commonMain/composeResources/values/strings.xml",
            ROOT / "shared/src/commonMain/composeResources/values-fr/strings.xml",
            ROOT / "shared/src/commonMain/composeResources/values-la/strings.xml",
        ),
        (
            ROOT / "app/src/main/res/values/strings.xml",
            ROOT / "app/src/main/res/values-fr/strings.xml",
            ROOT / "app/src/main/res/values-la/strings.xml",
        ),
    ]
    print("=== Rebuild Latin from French ===")
    for en_path, fr_path, la_path in jobs:
        build_la_from_fr(en_path, fr_path, la_path, cache)
        audit(la_path, en_path)

    save_cache(cache)

    print("\n=== Patch Hindi manual fixes ===")
    for hi_path, en_path in [
        (
            ROOT / "shared/src/commonMain/composeResources/values-hi/strings.xml",
            ROOT / "shared/src/commonMain/composeResources/values/strings.xml",
        ),
        (
            ROOT / "app/src/main/res/values-hi/strings.xml",
            ROOT / "app/src/main/res/values/strings.xml",
        ),
    ]:
        patch_hi(hi_path, en_path)
        audit(hi_path, en_path)

    print("\nDone.")


if __name__ == "__main__":
    main()
