# IptvTv

Odtwarzacz IPTV na Android TV (Kotlin, Compose for TV, Media3). Własne źródła: link do M3U + link do EPG (XMLTV).

## Aktualizacje z GitHub Releases

Aplikacja sprawdza `releases/latest` w repozytorium, z którego została zbudowana (`-PupdateRepo`),
i instaluje APK bezpośrednio na TV. Lokalne buildy (bez `updateRepo`) mają aktualizacje wyłączone.

1. Wygeneruj klucz podpisu (raz, i zachowaj plik — kolejne wersje MUSZĄ być podpisane tym samym kluczem):

       keytool -genkeypair -v -keystore release.jks -alias iptv -keyalg RSA -keysize 2048 -validity 10000

2. W repozytorium: Settings → Secrets and variables → Actions → dodaj:
   `KEYSTORE_BASE64` (wynik `base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (np. `iptv`), `KEY_PASSWORD`.
3. Wydanie: `git tag v0.2.0 && git push origin v0.2.0` — workflow „Release" zbuduje podpisany APK
   i utworzy Release (opis zmian generowany automatycznie, pokazuje go ekran Ustawienia).
4. Repozytorium musi być publiczne (API GitHuba bez tokena).
5. Na TV przy pierwszej instalacji zezwól aplikacji na „instalowanie z nieznanych źródeł".
