🇬🇧 Intentium – Project README Draft (EN)

Name: Intentium
Tagline: Natural-language UI element locators for Java / Selenium.

What is Intentium?

Intentium is a Java library that lets you work with web pages using human language intents instead of hand-written XPath or CSS selectors.
You tell Intentium what you mean – for example, “login field”, “password field”, “login button” – and it resolves those intents to real DOM elements on the page and performs actions on them.

The goal is to cut down the time spent on building and maintaining UI tests: fewer brittle locators, fewer page objects, more readable test code.

Why would you use it?

Less XPath / CSS boilerplate
You describe elements by their role and meaning; Intentium handles the DOM inspection and locator logic.

Human-readable tests
Tests read closer to business steps (“fill login field, fill password field, click login button”) instead of a wall of selectors.

Multi-language intents
From day one, Intentium is designed to understand intents in at least English and Russian, with the option to expand to more languages later.

Action layer on top of Selenium
Clicking, typing, clearing fields and other common actions are exposed through a simple, intent-driven API.

Transparent diagnostics
For each intent, Intentium can show which DOM element was chosen and which XPath and CSS correspond to it — useful for debugging and integration with existing frameworks.

What does Intentium actually do?

Conceptually, Intentium:

Receives an intent like “login field” or “password field”.

Maps it to an internal semantic role (e.g. LOGIN_FIELD, PASSWORD_FIELD, LOGIN_BUTTON).

Scans the page DOM via Selenium WebDriver:

inputs, buttons and other interactive elements,

labels, placeholders, aria attributes and nearby text.

Scores candidate elements according to how well they match the role.

Returns the best matching element (or fails explicitly if nothing fits or the match is ambiguous).

Performs requested actions on that element (type text, click, clear, etc.).

For advanced usage, Intentium can also:

expose both XPath and CSS for the chosen element in logs and reports;

optionally run a “double-check” mode, where it verifies that XPath- and CSS-based lookups still point to the same DOM node.

Example use case (in words)

On a typical login page, your test might:

Ask Intentium for the “login field” and type an email into it.

Ask for the “password field” and type a password.

Ask for the “login button” and click it.

Under the hood, Intentium figures out which inputs and button you meant by looking at labels (“Email”, “Логин”, “Password”), placeholders, aria-labels and surrounding text, then executes the appropriate Selenium actions.

🇷🇺 Intentium – Черновик README (RU)

Название: Intentium
Слоган: Локаторы для UI-тестов на человеческом языке (Java / Selenium).

Что такое Intentium?

Intentium — это Java-библиотека, которая позволяет обращаться к элементам страницы через намерения на обычном языке, а не через руками написанные XPath и CSS.

Вместо сложных селекторов вы используете фразы вроде:
«поле логина», «поле пароля», «кнопка входа»,
а Intentium сам находит соответствующие элементы в DOM и выполняет нужные действия.

Цель библиотеки — сократить время на разработку и поддержку UI-тестов: меньше хрупких локаторов и громоздких page object’ов, больше понятных и читаемых сценариев.

Зачем это нужно?

Меньше рутины с XPath и CSS
Вы описываете элемент по роли и смыслу, а разбор DOM и построение локаторов берёт на себя библиотека.

Тесты, которые можно читать как шаги сценария
Вместо набора селекторов — понятные действия: «заполнить поле логина», «заполнить поле пароля», «нажать кнопку входа».

Интенты на нескольких языках
С самого начала библиотека ориентирована на английский и русский, с возможностью расширения на другие языки.

Слой действий поверх Selenium
Клики, ввод текста, очистка поля и другие базовые операции доступны через простой API, привязанный к намерениям.

Прозрачная диагностика
Для каждого намерения Intentium может показать, какой элемент был выбран, а также сгенерированные для него XPath и CSS — это удобно и для отладки, и для интеграции в существующий фреймворк.

Что именно делает Intentium?

В общих чертах Intentium:

Получает намерение: например, «поле логина» или «кнопка входа».

Приводит его к внутренней роли (LOGIN_FIELD, PASSWORD_FIELD, LOGIN_BUTTON и т.п.).

Обходит DOM через Selenium WebDriver:

инпуты, кнопки и другие интерактивные элементы,

связанные label’ы, placeholder’ы, aria-атрибуты и соседний текст.

Оценивает кандидатов по набору признаков и эвристик.

Возвращает лучший подходящий элемент (или явно падает, если ничего подходящего нет или совпадений слишком много).

Выполняет над этим элементом нужные действия (ввод, клик, очистка и т.д.).

Для продвинутых сценариев Intentium также может:

отдавать и XPath, и CSS выбранного элемента в логах и отчётах;

по флагу включать режим «двойной проверки», когда поиск по XPath и по CSS сверяется между собой.

Пример сценария (словами)

На типовой странице авторизации тест:

просит Intentium найти «поле логина» и ввести туда email;

просит найти «поле пароля» и ввести пароль;

просит найти «кнопку входа» и нажать её.

Внутри библиотека анализирует label’ы («Email», «Логин», «Пароль»), placeholder’ы, aria-label и текст вокруг, выбирает подходящие поля и кнопку, а затем вызывает соответствующие действия Selenium.
