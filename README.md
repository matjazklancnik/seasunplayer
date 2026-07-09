# ShazamYtdlAndroid

Android aplikacija za uvoz seznamov skladb iz Shazam oziroma YouTube CSV
izvozov, prenos zvoka prek `yt-dlp` in lokalno predvajanje zvočnih datotek.

Projekt je trenutno MVP. Podatki in prenosi ostanejo v zasebnem internem
pomnilniku aplikacije.

## Funkcionalnosti

- uvoz Shazam, splošnih playlist in Google Takeout CSV datotek,
- avtorizacija Google računa in neposreden uvoz zasebnih YouTube seznamov,
- izbira enega, več ali vseh YouTube seznamov pred uvozom,
- dodajanje novih uvozov na obstoječi seznam brez brisanja prejšnjih skladb,
- odstranjevanje podvojenih skladb po normaliziranem izvajalcu in naslovu,
- ročno dodajanje izvajalca in naslova,
- lokalna SQLite zbirka skladb in stanj prenosa,
- rezervno iskanje javnih YouTube zadetkov, kadar lokalno iskanje nima zadetkov,
- slušno prepoznavanje skladbe prek uporabniško nastavljenega HTTPS endpointa
  in prikaz YouTube rezultatov za prenos,
- zaporedna čakalna vrsta za prenose,
- premik poljubne čakajoče skladbe na naslednje mesto z gumbom **Naslednja**,
- en samodejni ponovni poskus neuspelega prenosa na koncu vrste,
- prekinitev prenosa po treh minutah brez napredka,
- odstranitev posamezne skladbe in njene lokalne datoteke,
- iskanje skladbe na YouTubu, kadar uvoz ne vsebuje YouTube URL-ja,
- eksplicitna izbira najboljšega razpoložljivega zvočnega toka,
- ohranitev izvornega Opus/AAC zvoka brez dodatnega izgubnega pretvarjanja,
- prikaz in ročna zamenjava dejansko uporabljenega YouTube URL-ja,
- samodejna lokalna naslovnica iz MusicBrainz/Cover Art Archive z YouTube
  thumbnailom kot rezervnim virom,
- lokalna čakalna vrsta iz trenutno prikazanega seznama skladb,
- naslednja/prejšnja skladba, naključno predvajanje in tri možnosti ponavljanja,
- obnovitev čakalne vrste in položaja po ponovnem zagonu aplikacije,
- lokalno predvajanje, pavza, ustavitev in premikanje po posnetku,
- izbirni videzi **Sončni zahod**, **Ocean** in **Svetla**,
- SunSea ikona ter kompaktna nadzorna plošča trenutno predvajane skladbe,
- prikaz napredka ter podrobnosti napake,
- čiščenje celotnega seznama in vseh prenesenih datotek po potrditvi uporabnika.

## Uporaba

### Uvoz CSV

Izberi **Uvozi CSV** in nato eno ali več CSV datotek. Vsak naslednji uvoz se
združi z obstoječim seznamom; obstoječi prenosi in stanje skladb se ohranijo.
Po uvozu aplikacija prikaže število novih in že obstoječih skladb.

Za Shazam oziroma splošni CSV sta potrebna naslov in izvajalec. Prepoznani so
naslednji nazivi stolpcev:

- naslov: `Title`, `Track Title`, `Video Title`, `Song Title`, `Track`,
  `Song` ali `Name`,
- izvajalec: `Artist`, `Subtitle`, `Performer`, `Author`,
  `Channel Title`, `Channel` ali `Uploader`,
- neobvezni URL: `URL`, `Link`, `Shazam URL`, `Track URL`,
  `Source URL` ali `YouTube URL`.

Primer:

```csv
Artist,Title,URL
Example Artist,Example Track,https://www.youtube.com/watch?v=VIDEO_ID
```

Datoteka [`samples/shazam_sample.csv`](samples/shazam_sample.csv) vsebuje
osnovni vzorec.

### Odstranjevanje dvojnikov

Identiteta skladbe temelji na izvajalcu in naslovu. Pred primerjavo se oba
zapisa:

- Unicode normalizirata,
- pretvorita v male črke,
- obrežeta,
- zaporedni presledki pa se združijo.

Če skladba že obstaja, uvoz ne ustvari nove vrstice. Nov veljaven URL se lahko
doda obstoječemu zapisu, lokalna pot in stanje že prenesene skladbe pa se ne
izgubita.

### Iskanje lokalno in na YouTubu

Iskalno polje najprej filtrira lokalno knjižnico. Če po najmanj dveh znakih ni
lokalnega zadetka, aplikacija po kratkem zamiku prek obstoječega yt-dlp prikaže
do pet javnih YouTube zadetkov. To iskanje ne potrebuje prijave ali API ključa.
Gumb **Prenesi** izbrani zadetek doda v lokalno knjižnico z neposrednim YouTube
URL-jem in ga uvrsti v čakalno vrsto.

### Slušno prepoznavanje

Ikona ušesa v iskalnem polju posname približno 10 sekund zvoka, pošlje vzorec
na nastavljen HTTPS prepoznavni endpoint in iz rezultata nastavi iskanje po
izvajalcu ter naslovu. YouTube rezultati se prikažejo takoj pod iskalnim poljem
in jih je mogoče dodati v čakalno vrsto z gumbom **Prenesi**.

Aplikacija ne vgrajuje Shazam ali ACRCloud skrivnosti v APK. Endpoint naj bo
lasten proxy oziroma backend, ki hrani ključe ponudnika prepoznavanja. Aplikacija
pošlje `multipart/form-data` POST z deli:

- `sample`: M4A/AAC zvočni vzorec,
- `sample_bytes`: velikost vzorca v bajtih,
- `data_type`: `audio`.

Sprejet je preprost JSON:

```json
{ "artist": "Adele", "title": "Hello", "youtubeVideoId": "YQHsXMglC9A" }
```

Sprejet je tudi ACRCloud-compatible odgovor z `metadata.music[0].artists`,
`metadata.music[0].title` in neobveznim `external_metadata.youtube.vid`.

### Zasebni YouTube seznami

Pred uporabo mora razvijalec enkrat nastaviti Google Cloud projekt po navodilih
v [`YOUTUBE_API_SETUP.md`](YOUTUBE_API_SETUP.md).

V aplikaciji izberi **Poveži YouTube in izberi sezname**. Google prikaže svoj
avtorizacijski zaslon, aplikacija pa zahteva samo read-only dovoljenje
`youtube.readonly`. Po potrditvi:

1. aplikacija prek YouTube Data API prebere vse strani uporabnikovih seznamov,
2. prikaže naslov, število videov in vidnost vsakega seznama,
3. uporabnik izbere enega, več ali vse sezname,
4. aplikacija prebere vse strani izbranih seznamov in skladbe združi z lokalno
   knjižnico brez podvajanja.

Gesla, prijavni piškotki in OAuth žeton se ne zapisujejo v SQLite ali datoteke
aplikacije. Aplikacijska koda kratkotrajni access token drži samo v pomnilniku
med izbiro in uvozom ter ga nato odstrani; sistemski predpomnilnik žetonov
varno upravlja Google Play Services.

#### CSV rezervna možnost

Če API ni nastavljen, je še vedno podprt CSV izvoz prek Google Takeout:

1. izvozi podatke **YouTube and YouTube Music**,
2. v izvozu poišči CSV izbranega seznama,
3. v aplikaciji izberi **Uvozi CSV**.

Če CSV vsebuje stolpec `Video ID`, aplikacija iz njega sestavi neposredni URL
`https://www.youtube.com/watch?v=VIDEO_ID`. Vzorec je v
[`samples/youtube_takeout_sample.csv`](samples/youtube_takeout_sample.csv).

Google Takeout lahko vsebuje samo ID videa in čas dodajanja. V tem primeru je
skladba do pridobitve datoteke v seznamu prikazana kot izvajalec `YouTube` in
naslov enak ID-ju. Zasebnost seznama ni ovira, posamezen zaseben ali drugače
nedostopen video pa brez prijave ni prenosljiv.

### Prenos

Pri skladbi izberi **Prenesi**:

- če ima skladba YouTube URL, se uporabi neposredno;
- sicer aplikacija uporabi YouTube iskanje
  `ytsearch1:<izvajalec> - <naslov>`;
- rezultat iskanja razreši v neposredni YouTube URL in ga shrani pri skladbi;
- z gumbom **Vir** je mogoče URL pregledati, zamenjati ali s praznim poljem
  ponovno vključiti samodejno iskanje;
- možnost `--no-playlist` zagotovi prenos ene skladbe;
- `bestaudio/best` izbere najboljši razpoložljivi zvočni tok;
- Opus oziroma AAC se ohrani brez ponovnega izgubnega kodiranja. Končna datoteka
  je običajno `.opus` ali `.m4a`; obstoječe `.mp3` datoteke ostanejo podprte.

Shazam in drugi ne-YouTube URL-ji se lahko shranijo kot izvorni podatek, vendar
se za prenos trenutno ne uporabijo neposredno; v tem primeru se izvede YouTube
iskanje po izvajalcu in naslovu.

Prenosi se izvajajo zaporedno. Nedokončani elementi čakalne vrste se po ponovnem
zagonu aplikacije ponovno dodajo v vrsto. Pri čakajoči skladbi gumb
**Naslednja** skladbo premakne pred običajno vrsto; trenutno aktivnega prenosa
ne prekine. Prva napaka skladbo samodejno doda na konec običajne vrste. Šele če
odpove tudi drugi poskus, se skladba označi kot napaka.

Če `yt-dlp` tri minute ne javi nobenega napredka, aplikacija proces prekine.
Tak timeout šteje kot neuspešen poskus in zanj velja enaka politika ponovitve.

Zvočne datoteke se shranijo v interno mapo:

```text
<app files>/music/<track-id>/
```

Zato dovoljenje za skupni pomnilnik ni potrebno in datoteke niso neposredno
vidne drugim aplikacijam.

### Predvajanje

Ko je prenos končan, dotik gumba za predvajanje sestavi čakalno vrsto iz vseh
lokalnih skladb, ki so trenutno prikazane. Nadzorna plošča predvajalnika ponuja:

- predvajanje in pavza,
- ustavitev,
- naslednjo in prejšnjo skladbo,
- naključni vrstni red,
- ponavljanje izklopljeno, celotne vrste ali ene skladbe,
- drsnik za premik po posnetku,
- trenutni in skupni čas posnetka,
- prikaz položaja skladbe v čakalni vrsti.

Predvajanje uporablja AndroidX Media3/ExoPlayer.

Po uspešnem novem prenosu aplikacija pri zelo dobrem ujemanju izvajalca in
naslova poišče albumsko naslovnico v MusicBrainz/Cover Art Archive. Če varnega
ujemanja ali slike ni, shrani thumbnail dejanskega YouTube videa. Naslovnica je
prikazana na kartici skladbe in posredovana Media3 sistemskemu predvajalniku.

Čakalna vrsta, aktivna skladba, položaj, naključno predvajanje in ponavljanje
se shranijo. Po ponovnem zagonu aplikacija vrsto pripravi na zadnjem položaju,
vendar predvajanja ne zažene brez uporabnikovega dejanja. Gumb **Ustavi**
izprazni shranjeno čakalno vrsto.

### Videz

Gumb s paleto v glavi odpre izbiro ozadja in barvne sheme. Na voljo so
**Sončni zahod**, **Ocean** in **Svetla**. Izbira se shrani in uporabi ob
naslednjem zagonu.

### Čiščenje knjižnice

Gumb za čiščenje seznama in prenesenih datotek je omogočen, ko seznam ni prazen.
Pred brisanjem aplikacija zahteva izrecno potrditev. Operacija:

1. ustavi predvajanje,
2. razveljavi čakajoče prenose,
3. izbriše vse vrstice iz lokalne baze,
4. izbriše interno mapo `music`.

Operacije ni mogoče razveljaviti. Prenos, ki je bil ob potrditvi že v izvajanju,
se ob zaključku zavrže in njegova izhodna mapa izbriše.

## Zahteve za razvoj

- Android Studio z Android Gradle Plugin podporo,
- JDK 17,
- Android SDK 36,
- internetna povezava za prvi Gradle sync in posodobitev `yt-dlp`.

Konfigurirane različice:

| Komponenta | Različica |
| --- | --- |
| Android Gradle Plugin | 9.2.1 |
| Gradle wrapper | 9.4.1 |
| Kotlin/Compose plugin | 2.2.21 |
| Compose BOM | 2026.04.01 |
| Media3 | 1.10.1 |
| youtubedl-android | 0.18.1 |
| Google Play Services Auth | 21.6.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 (Android 8.0) |

## Gradnja

Odpri korensko mapo projekta v Android Studiu, počakaj na Gradle Sync in zaženi
modul `app`.

Preverjanje prevajanja iz ukazne vrstice:

```bash
./gradlew :app:compileDebugKotlin
```

Gradnja APK-ja:

```bash
./gradlew :app:assembleDebug
```

Debug APK se ustvari v:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Struktura kode

| Pot | Namen |
| --- | --- |
| `MainActivity.kt` | Compose uporabniški vmesnik in uporabniški tokovi |
| `importer/ShazamCsvImporter.kt` | zaznava formata, razčlenjevanje in deduplikacija CSV |
| `data/TrackDbHelper.kt` | SQLite baza in združevalni uvoz |
| `download/DownloadQueueManager.kt` | zaporedna čakalna vrsta in zaščita pri čiščenju |
| `download/YoutubeDlBridge.kt` | povezava z yt-dlp in FFmpeg |
| `player/PlayerHolder.kt` | Media3 lokalni predvajalnik |
| `util/Ids.kt` | normalizacija in stabilni ID skladbe |
| `youtube/YouTubeApiClient.kt` | paginiran read-only dostop do YouTube Data API |

Osnovni paket aplikacije je `com.example.shazamytdl`.

## Omejitve

- slušno prepoznavanje potrebuje nastavljen zunanji HTTPS endpoint oziroma
  proxy za ponudnika prepoznavanja zvoka,
- YouTube API zahteva pravilno nastavljen Google Cloud projekt, Android OAuth
  klient ter ustrezen SHA-1 podpis aplikacije,
- aplikacije z zunanjimi uporabniki lahko pred objavo zahtevajo Googlov postopek
  OAuth preverjanja,
- posamezni zasebni, odstranjeni ali geografsko omejeni videi lahko odpovejo,
- aktivnega procesa yt-dlp trenutno ne prekine takoj; rezultat se po čiščenju
  zavrže,
- prenosi ne uporabljajo foreground service in jih lahko Android pri daljšem
  delovanju v ozadju prekine,
- avtomatski testi za CSV in podatkovno plast še niso dodani.

## Pravna opomba

Prenašaj samo vsebine, za katere imaš dovoljenje, in upoštevaj pogoje ponudnika
vsebine. Projekt uporablja nespremenjene zunanje knjižnice
`youtubedl-android` in FFmpeg kot Gradle odvisnosti.

## Predlagani naslednji koraki

- pridobitev naslovov in izvajalcev za Takeout izvoze, ki vsebujejo samo ID-je,
- pravi preklic aktivnega yt-dlp procesa,
- foreground service za zanesljive dolge prenose,
- ponovni poskus posameznega neuspešnega prenosa,
- enotski in instrumentirani testi.
