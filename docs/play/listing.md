# Google Play store listing — Recipe Fork (serverless build)

Drafted for v0.25 (T-184) from the code on `develop` and `docs/increments/v0.1–v0.24`. Every sentence describes what the serverless build does today: no account, no server, no sync, no publishing. If a later build changes that, change these texts with it.

Each text sits in a block so it can be copied as is into Play Console (Store presence → Main store listing; the Russian texts go into the ru-RU translation of the listing). Counts are Unicode characters as Play Console counts them, line breaks included, checked with a script on the block's content.

Limits: name ≤ 30, short description ≤ 80, full description ≤ 4000.

Rules followed (Play's metadata policy): no ranking or "best" claims, no testimonials, no emoji, no keyword lists, no mention of other apps' trademarks beyond saying a file can be opened from a messenger (Telegram is named once because the app was tested with it; drop it if you prefer).

---

## English (en-US, the default language)

### App name

```text
Recipe Fork
```

Characters: 11

### Short description

```text
Your recipe book: scale servings, run step timers, share recipes as files.
```

Characters: 74

### Full description

```text
Recipe Fork is a recipe book that lives on your phone. Write your recipes once, then cook from a clear card that scales, times and keeps your place. No account, no sign-in: everything stays on this device.

WRITE A RECIPE
• Pick ingredients from a built-in dictionary of more than 2,500 ingredients in English and Russian, or type your own.
• Put ingredients into groups such as "Dough" or "Glaze", and add a short note to any line ("finely chopped", "room temperature").
• Insert an ingredient into a step's text as a reference: its amount follows the servings, so "add 200 g flour" becomes "400 g" when you double the recipe.
• Give a step up to five timers, each with its own duration and name.
• Add a cover photo and a photo or a short clip to any step. Photos are cropped and made smaller, and their location and other data are removed. Clips (up to 15 seconds) are made small and silent; an animated GIF or WebP becomes a clip too.
• Tags from a ready list (cuisines, meals, diets, cooking methods), the recipe's language, servings and a description.
• Drag steps and ingredients into order, and undo a removal.

COOK FROM THE CARD
• Change the servings and every amount is recalculated, in the list and inside the steps. Rounded amounts say which way they were rounded; eggs and other whole items stay whole.
• Have only 300 g of something? Fix that amount and the whole recipe follows it.
• Set how many people you usually cook for, and recipes open at that number.
• Tap an ingredient or a step to mark it done; the marks stay until you clear them.
• Start timers from the steps. Running timers stay in a bar at the bottom, keep running when the app is closed, and ring with a notification when they end.
• Large buttons and larger step text for busy hands, and the screen stays on while the card is open.

SHARE A RECIPE AS A FILE
• Export a recipe as a .recipefork file and send it with any app on your phone: a messenger, e-mail, cloud storage.
• Open a .recipefork file straight from Telegram or a file manager, and the recipe, with its photos and clips, is added to your book.
• Recipes you receive have their own tab. Cook from them as they are, or fork one: make your own version to edit, which remembers the recipe it came from.

FIND IT AGAIN
• Search titles, ingredients and descriptions.
• Filter by ingredient (recipes with the most of it per serving come first), by tag or by the recipe's language; sort by date or by title.

AND ALSO
• English and Russian interface, chosen in the settings or by the phone.
• Light and dark theme, or as on the device.
• No ads, no tracking, no internet needed.

Your recipes are kept only on this phone and are not backed up anywhere by the app. Export the ones you want to keep a copy of.
```

Characters: 2747

---

## Русский (ru-RU, перевод листинга)

### Название

```text
Recipe Fork
```

Символов: 11

### Краткое описание

```text
Книга рецептов: пересчёт порций, таймеры в шагах, обмен рецептами файлом.
```

Символов: 73

### Полное описание

```text
Recipe Fork — книга рецептов в вашем телефоне. Запишите рецепт один раз и готовьте по понятной карточке, которая пересчитывает порции, засекает время и помнит, где вы остановились. Без аккаунта и входа: всё хранится на этом устройстве.

ЗАПИСЫВАЙТЕ РЕЦЕПТЫ
• Ингредиенты выбираются из встроенного словаря — больше 2 500 продуктов на русском и английском, — или вписываются своими словами.
• Ингредиенты можно разбить на группы («Тесто», «Глазурь») и к любой строке добавить короткую заметку («мелко нарезать», «комнатной температуры»).
• Ингредиент вставляется в текст шага ссылкой: его количество следует за порциями, и «всыпьте 200 г муки» превращается в «400 г», когда вы удваиваете рецепт.
• В шаге — до пяти таймеров, у каждого своё время и название.
• Обложка рецепта и фото или короткий ролик к любому шагу. Фото обрезаются и уменьшаются, а геолокация и прочие данные из них удаляются. Ролики (до 15 секунд) сжимаются и становятся беззвучными; анимированный GIF или WebP тоже становится роликом.
• Теги из готового списка (кухни, приёмы пищи, диеты, способы приготовления), язык рецепта, число порций и описание.
• Шаги и ингредиенты перетаскиваются в нужном порядке, удаление можно отменить.

ГОТОВЬТЕ ПО КАРТОЧКЕ
• Меняете число порций — пересчитываются все количества, и в списке, и в тексте шагов. Округлённые помечены, в какую сторону; яйца и другие штучные продукты остаются целыми.
• Есть только 300 г чего-то? Зафиксируйте это количество, и весь рецепт подстроится под него.
• Укажите, на сколько человек вы обычно готовите, — рецепты будут открываться на это число порций.
• Нажмите на ингредиент или шаг, чтобы отметить его сделанным; отметки остаются, пока вы их не снимете.
• Таймеры запускаются прямо из шагов. Запущенные видны на панели внизу, идут при закрытом приложении и звонят с уведомлением, когда время вышло.
• Крупные кнопки и крупный текст шагов для занятых рук; пока карточка открыта, экран не гаснет.

ДЕЛИТЕСЬ РЕЦЕПТОМ ФАЙЛОМ
• Экспортируйте рецепт в файл .recipefork и отправьте его через любое приложение на телефоне: мессенджер, почту, облако.
• Откройте файл .recipefork прямо из Telegram или файлового менеджера — рецепт вместе с фото и роликами появится в вашей книге.
• Полученные рецепты лежат на отдельной вкладке. Готовьте по ним как есть или сделайте свою версию: её можно править, и она помнит, из какого рецепта сделана.

НАХОДИТЕ НУЖНОЕ
• Поиск по названиям, ингредиентам и описаниям.
• Фильтры по ингредиенту (первыми — рецепты, где его больше всего на порцию), по тегам и по языку рецепта; сортировка по дате или по названию.

А ЕЩЁ
• Интерфейс на русском и английском — по выбору в настройках или как в телефоне.
• Светлая и тёмная тема или как в системе.
• Без рекламы, без слежки, без интернета.

Рецепты хранятся только на этом телефоне, и приложение нигде не делает их резервную копию. Экспортируйте те, копию которых хотите сохранить.
```

Символов: 2890

---

## Other listing fields (suggestions)

- **App category:** Food & Drink.
- **Tags (Play Console picks from its own list):** recipes, cooking — choose the closest Play offers.
- **Contact e-mail (shown publicly):** `<CONTACT_EMAIL>` — a dedicated address is better than a personal one, it is public.
- **Website:** optional; can be the page that hosts the privacy policy.
- **Privacy policy URL:** the public address of `privacy-policy.md` once hosted (see `owner-checklist.md`).
- **Graphics:** icon `build/play/icon-512.png`; feature graphic and phone screenshots from `build/play/screenshots/` (English screenshots for en-US, Russian ones for ru-RU).

## Words checked against the app

- "Fork" in English and "своя версия" in Russian are the words the card's button uses (`app/src/main/res/values/strings_card.xml` `card_fork`, `values-ru/strings_card.xml`).
- The two tabs of the book are "Written here" / "Marked" ("Написанные здесь" / "Отмеченные"); the listing says "their own tab" rather than "Marked" because in the serverless build the second tab holds the recipes imported from files (`library_marked_empty_sentence_serverless`).
- "More than 2,500 ingredients": `app/src/main/assets/dictionaries.json` holds 2,536 ingredient entries and 293 tags (counted 2026-10-09).
- "No internet needed": true of the serverless build — it builds no HTTP client (`AppContainer.kt`, `CompositionRootTest`). The manifest still declares the INTERNET permission, so "full network access" appears among the app's permissions in its Play details; see `data-safety.md`, finding 1. If the permission stays, the sentence is still true but readers may notice the mismatch.
