# Bezpieczeństwo i zgodność OPAQUE

## Zachowane dane i parametry

Nie zmieniono wersji działających bibliotek Android/JVM ani Serenity 1.1.0,
identyfikatorów kont, normalizacji e-maila, profilu `memory-constrained`,
kodowania komunikatów ani wyprowadzania `exportKey`. Format AES-GCM,
12-bajtowy nonce oraz parametry HKDF pozostają zgodne. Nie regeneruje się
kluczy E2E ani rekordów kont. Nie używano sekretów produkcyjnych.

`shared/src/jvmTest/resources/opaque-legacy.json` jest trwałym, syntetycznym
rekordem sprzed poprawek. `scripts/opaque-compatibility.cjs` jest wyłącznie
pomocnikiem testowym i korzysta z zależności sąsiedniego repozytorium serwera.
Nie uruchamiać operacji `generate` w celu obejścia błędu zgodności.

## Wdrożone zabezpieczenia

- Sekrety są oddzielone od zwykłych ustawień. Windows korzysta z DPAPI
  użytkownika, Android z szyfrowanego magazynu chronionego przez Keystore.
  Dodano adaptery Keychain i Secret Service; wymagają sprawdzenia na swoich systemach.
- Niedostępność magazynu oznacza sesję w pamięci i komunikat w aplikacji.
  Nie ma trwałego zapisu jawnego po błędzie. Znacznik wymagający logowania
  chroni przed przywróceniem starego zapisu po przerwanym usuwaniu.
- Migracja desktopowa odczytuje poprzedni szyfrogram, sprawdza zgodność par
  kluczy P-256, zapisuje i odczytuje nowy magazyn, dopiero potem usuwa stary.
  Zachowuje klucz DPoP powiązany z sesją.
- Nonce E2E i identyfikatory DPoP pochodzą z systemowego źródła losowości.
  Dostęp do natywnej pary DPoP oraz zapis tokenów przy odświeżeniu są synchronizowane.
- Wylogowanie przygotowuje dowód przed usunięciem danych. Reset odświeżenia
  uniemożliwia zapis tokenów po wylogowaniu. Wyłączono automatyczne ponawianie żądań.
- Browser API ma osobne zakończenie logowania, odświeżanie i wylogowanie.
  Refresh token pozostaje w `__Host-clearmind-refresh` z HttpOnly, Secure,
  SameSite=Strict i Path=/; natywne endpointy pozostają zgodne.
- Browser API sprawdza Origin i nagłówek `X-Clearmind-CSRF: 1`.
  Access token znajduje się tylko w pamięci. DPoP używa nieeksportowalnej
  pary CryptoKey w IndexedDB. Utrata pary wymaga logowania.
- Web Locks koordynują odświeżanie między kartami. Klienci React i Kotlin
  współdzielą klucz DPoP, znacznik zmiany sesji oraz magazyn odblokowania.
  Przeglądarki bez Web Locks zgłaszają brak obsługi bez niebezpiecznego obejścia.
- Zapamiętanie odblokowania wymaga wyboru w aplikacji, domyślnie wyłączonego.
  Zapisany klucz jest nieeksportowalny. Wylogowanie i wyłączenie opcji usuwają go.
  Stare magazyny aplikacji są usuwane bez czyszczenia innych danych domeny.
- Wasm korzysta z Serenity 1.1.0 zamiast własnej konstrukcji kryptograficznej.
  Stany OPAQUE są ograniczone do 32 i 120 sekund, zużywane jednokrotnie
  i usuwane po anulowaniu. Zasoby natywne są zwalniane także po błędzie.
- Logi uwierzytelniania nie zawierają sekretów ani komunikatów protokołu.
  Backend dodaje CSP w trybie raportowania i nagłówki ochronne.

## Środowisko deweloperskie

Frontend i API muszą mieć jeden origin. Proxy Vite oraz
`webApp/webpack.config.d/api-proxy.js` przekazują ścieżki API do backendu.
`PUBLIC_BASE_URL` backendu musi być dokładnym originem przeglądarki,
także przy lokalnych testach; identyfikatorów OPAQUE nie zmieniać dla istniejących kont.
W środowisku docelowym używać HTTPS. Proxy Kotlin przyjmuje
`CLEARMIND_API_URL`, domyślnie `http://localhost:3000`.

Na Windows używać wrappera zgodnie z AGENTS.md. Przykład:

```powershell
$env:CHROME_BIN='C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :shared:jsBrowserTest :shared:wasmJsBrowserTest '-Pkotlin.compiler.execution.strategy=in-process'
```

Testy przeglądarkowe mają dłuższy limit Mocha dla pełnych przebiegów Argon2/Wasm.
Blokady plików wynikowych na Windows wymagają osobnego katalogu wyników;
nie zmieniać uprawnień ani ustawień kryptograficznych w celu obejścia problemu.

## Pozostałe bramki wydania

Weryfikacja na Windows, 17.09.2026:

| Zestaw | Wynik |
| --- | --- |
| JVM, w tym trwałe dane OPAQUE, DPAPI, DPoP i wylogowanie | 128 testów, 0 błędów |
| Android Host, w tym natywny adapter OPAQUE | 28 testów, 0 błędów |
| Kotlin JS w Edge, w tym stare konto i pełne OPAQUE | 27 testów, 0 błędów |
| Kotlin Wasm w Edge, w tym stare konto i pełne OPAQUE | 27 testów, 0 błędów |
| Klient React | 59 testów, 0 błędów; typy i build poprawne |
| Backend | 186 testów, 0 błędów; typy poprawne |

Wyniki Host Test dotyczą bibliotek uruchamianych na Windows, nie urządzenia Android.
Pełne polecenie czterech platform zakończyło się `BUILD SUCCESSFUL`.
Testy negatywne JS/Wasm obejmują błędne hasło, identyfikator serwera,
uszkodzoną odpowiedź i ponowne wykorzystanie stanu. Błędy Serenity i magazynu
są normalizowane na granicy JavaScript/Wasm, bez ujawniania danych protokołu.

Użytkownik odłożył prace wymagające Maca. iOS nie ma jeszcze zgodnego
adaptera Rust ani testów na urządzeniu i symulatorze. Logowanie na tej platformie
zgłasza niedostępność zamiast stosować własną konstrukcję XOR/HMAC.

Pełny odbiór wymaga jeszcze izolowanego testu HTTP z bazą danych:
stare konto, zadania i załączniki, zmiana hasła, restart i równoległe karty,
unieważnienie sesji oraz rzeczywiste ciasteczka i CSRF. Testy jednostkowe
i przebiegi syntetyczne nie zastępują tej bramki.

Nie włączono egzekwowania CSP bez testu wszystkich zasobów aplikacji JS/Wasm
oraz raportów naruszeń. Android Host Test nie zastępuje testu Keystore
na urządzeniu. Keychain i Secret Service wymagają sprawdzenia na macOS/Linux.

Nie wdrażano zmian na produkcję. Nie cofać do wersji zapisującej sekrety jawnie;
awaryjny powrót może wymagać ponownego logowania, nigdy regeneracji kluczy konta.
