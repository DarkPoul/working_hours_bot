package esvar.ua.workinghoursbot.bot;

import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

public final class KeyboardFactory {

    private KeyboardFactory() {
    }

    public static ReplyKeyboardMarkup enterNameKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("🔁 Почати спочатку"));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    public static ReplyKeyboardMarkup roleMenuKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Продавець"));
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Старший продавець"));
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("ТМ"));
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
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🗓 Створити графік"));
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📅 Мій графік"));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row1, row2));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }
}
