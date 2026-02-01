package esvar.ua.workinghoursbot.bot;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

public final class InlineKeyboardFactory {

    private InlineKeyboardFactory() {
    }

    public static InlineKeyboardMarkup singleRow(List<InlineKeyboardButton> buttons) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(new ArrayList<>(buttons)));
        return markup;
    }

    public static InlineKeyboardMarkup rows(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (List<InlineKeyboardButton> row : rows) {
            keyboard.add(new ArrayList<>(row));
        }
        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
