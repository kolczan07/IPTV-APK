# Wydania i automatyczne aktualizacje

Aplikacja sprawdza `releases/latest` w repozytorium GitHub, z którego została zbudowana
(nazwa repo jest wstrzykiwana przez workflow jako `-PupdateRepo`). Lokalny build bez tego
parametru ma aktualizacje wyłączone.

## Jednorazowo: stały klucz podpisu
Aktualizacja zainstaluje się tylko wtedy, gdy APK jest podpisane tym samym kluczem co zainstalowane.

    keytool -genkeypair -v -keystore release.jks -alias iptv -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 release.jks     # wynik wklej do sekretu KEYSTORE_BASE64

Sekrety repozytorium (Settings → Secrets and variables → Actions):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Plik `release.jks` zachowaj w bezpiecznym miejscu (utrata klucza = brak możliwości aktualizacji).

## Nowe wydanie
    git tag v0.2.0
    git push origin v0.2.0

Workflow zbuduje podpisane APK i opublikuje je jako GitHub Release.
Pierwsza instalacja na TV musi być wersją release (odinstaluj wcześniejszy build debug).
