package esvar.ua.workinghoursbot.bot;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

public final class KeyboardFactory {

    private KeyboardFactory() {
    }

    public static ReplyKeyboardMarkup cancelKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Скасувати"));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    public static InlineKeyboardMarkup roleKeyboard() {
        InlineKeyboardButton seller = InlineKeyboardButton.builder()
                .text("Продавець")
                .callbackData(CallbackData.ROLE_SELLER)
                .build();
        InlineKeyboardButton seniorSeller = InlineKeyboardButton.builder()
                .text("Старший продавець")
                .callbackData(CallbackData.ROLE_SENIOR_SELLER)
                .build();
        InlineKeyboardButton tm = InlineKeyboardButton.builder()
                .text("ТМ")
                .callbackData(CallbackData.ROLE_TM)
                .build();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(seller));
        rows.add(List.of(seniorSeller));
        rows.add(List.of(tm));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup locationKeyboard(List<String> locations) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            String location = locations.get(i);
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(location)
                    .callbackData(CallbackData.LOCATION_PREFIX + i)
                    .build();
            rows.add(List.of(button));
        }

        InlineKeyboardButton back = InlineKeyboardButton.builder()
                .text("Назад")
                .callbackData(CallbackData.BACK_TO_ROLE)
                .build();
        InlineKeyboardButton cancel = InlineKeyboardButton.builder()
                .text("Скасувати")
                .callbackData(CallbackData.CANCEL)
                .build();
        rows.add(List.of(back, cancel));
        return new InlineKeyboardMarkup(rows);
    }
}
