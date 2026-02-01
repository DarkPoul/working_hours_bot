package esvar.ua.workinghoursbot.bot;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

public final class KeyboardFactory {

    private KeyboardFactory() {
    }

    private static KeyboardRow singleButtonRow(String label) {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(label));
        return row;
    }

    public static ReplyKeyboardMarkup enterNameKeyboard() {
        KeyboardRow row = singleButtonRow("🔁 Почати спочатку");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    public static ReplyKeyboardMarkup roleMenuKeyboard() {
        KeyboardRow row1 = singleButtonRow("Продавець");
        KeyboardRow row2 = singleButtonRow("Старший продавець");
        KeyboardRow row3 = singleButtonRow("ТМ");
        KeyboardRow navRow = new KeyboardRow();
        navRow.add(new KeyboardButton("⬅️ Назад"));
        navRow.add(new KeyboardButton("🔁 Почати спочатку"));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row1, row2, row3, navRow));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup locationMenuKeyboard(List<String> locationButtons,
                                                           boolean hasPrev,
                                                           boolean hasNext) {
        List<KeyboardRow> rows = new java.util.ArrayList<>();
        for (String location : locationButtons) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(location));
            rows.add(row);
        }

        KeyboardRow paginationRow = new KeyboardRow();
        if (hasPrev) {
            paginationRow.add(new KeyboardButton("⬅️ Попередні"));
        }
        if (hasNext) {
            paginationRow.add(new KeyboardButton("➡️ Наступні"));
        }
        if (!paginationRow.isEmpty()) {
            rows.add(paginationRow);
        }

        KeyboardRow navRow = new KeyboardRow();
        navRow.add(new KeyboardButton("⬅️ Назад"));
        navRow.add(new KeyboardButton("🔁 Почати спочатку"));
        rows.add(navRow);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup pendingMenuKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("🔄 Оновити"));
        row.add(new KeyboardButton("🔁 Почати спочатку"));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup mainMenuKeyboard() {
        KeyboardRow row1 = singleButtonRow("✍️ Внести графік");
        KeyboardRow row2 = singleButtonRow("🗓 Мій графік");
        KeyboardRow row3 = singleButtonRow("🏢 Локація");
        KeyboardRow row4 = singleButtonRow("🔁 Підміна");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row1, row2, row3, row4));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup tmMainMenuKeyboard() {
        KeyboardRow row1 = singleButtonRow("Заявки");
        KeyboardRow row2 = singleButtonRow("Локації");
        KeyboardRow row3 = singleButtonRow("Графік локацій");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row1, row2, row3));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup backKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(singleButtonRow("Назад")));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup yesBackKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Так"));
        row.add(new KeyboardButton("Назад"));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup yesRejectBackKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Так"));
        row.add(new KeyboardButton("Заборонити"));
        row.add(new KeyboardButton("Назад"));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup listWithBackKeyboard(List<String> buttons) {
        List<KeyboardRow> rows = new java.util.ArrayList<>();
        for (String label : buttons) {
            rows.add(singleButtonRow(label));
        }
        rows.add(singleButtonRow("Назад"));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    public static ReplyKeyboardMarkup tmLocationsMenuKeyboard() {
        KeyboardRow row1 = singleButtonRow("Додати локацію");
        KeyboardRow row2 = singleButtonRow("Видалити локацію");
        KeyboardRow row3 = singleButtonRow("Назад");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row1, row2, row3));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }
    /**
     * Меню для редагування графіка:
     * 🗑 Очистити, ◀️, ▶️, 💾 Зберегти, ⬅️ Назад
     */
    public static ReplyKeyboardMarkup scheduleEditMenuKeyboard() {
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🗑 Очистити"));
        row1.add(new KeyboardButton("💾 Зберегти"));
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("◀️ Попередній місяць"));
        row2.add(new KeyboardButton("▶️ Наступний місяць"));
        rows.add(row2);

        KeyboardRow row3 = singleButtonRow("⬅️ Назад");
        rows.add(row3);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

}
