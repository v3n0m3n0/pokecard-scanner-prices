# 🎴 PokéCard Scanner & Prices

Aplikacja mobilna na system Android służąca do skanowania, identyfikacji oraz wyceny kart Pokémon TCG w oparciu o rynek polski (Allegro, OLX, Pokekarty.pl) oraz globalny (TCGplayer, Cardmarket, PriceCharting).

---

## 🌟 Główne Funkcje

1. **Skanowanie AI (Google Gemini API)**:
   - Skanowanie kart za pomocą aparatu lub wybranego zdjęcia z galerii.
   - Zaawansowane rozpoznawanie nazwy karty, numeru kolekcjonerskiego oraz serii za pomocą sztucznej inteligencji (Gemini 1.5/2.0 Vision).

2. **Wyszukiwanie i Filtrowanie**:
   - Wyszukiwarka tekstowa z podpowiedziami gatunków Pokémonów (autocomplete).
   - Filtrowanie według rzadkości (*Rarity*: Common, Uncommon, Rare Holo, Illustration Rare, Secret Rare, Promo, itp.).

3. **Konsolidacja Cen i Ofert (Rynek PL & Świat)**:
   - Ceny rynkowe z **TCGplayer** (USD) oraz **Cardmarket** (EUR).
   - Bezpośrednie odnośniki do ofert na rynek polski: **Allegro**, **OLX** (z filtrowaniem miejscowości) oraz **Pokekarty.pl**.
   - Integracja z **PriceCharting** (wycena kart nieocenionych oraz ocenionych PSA/BGS/CGC).

4. **Ulubione i Historia Wyszukiwań**:
   - Zapisywanie kart do Ulubionych wraz z migawką cen rynkowych w lokalnej bazie danych SQLite (**Room**).
   - Przechowywanie historii wyszukiwań z możliwością szybkiego ponownego wyszukania.

---

## 🛠️ Stos Technologiczny

- **Język**: Kotlin
- **UI**: Jetpack Compose (Material 3 Design)
- **Architektura**: MVVM (Model-View-ViewModel), Coroutines & Flow
- **Baza Danych**: Room Database
- **Integracje API**:
  - Google Gemini API (Multimodal AI Vision)
  - Pokémon TCG API (`api.pokemontcg.io`)
- **Ładowanie Obrazów**: Coil Compose

---

## 🚀 Jak Uruchomić Projekt

1. Otwórz projekt w **Android Studio**.
2. Utwórz plik `.env` w głównym katalogu projektu i ustaw klucz API Gemini:
   ```env
   GEMINI_API_KEY=twój_klucz_gemini_api
   ```
3. Zbuduj i uruchom aplikację na emulatorze lub urządzeniu fizycznym (`Run 'app'`).
