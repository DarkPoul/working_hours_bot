package esvar.ua.workinghoursbot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

@Component
@RequiredArgsConstructor
public class ScheduleCalendarRenderer {

    private final ScheduleCalendarKeyboardBuilder keyboardBuilder;

    public SendMessage buildEditMessage(Long chatId, String locationName, YearMonth month, Set<LocalDate> workDays) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(buildEditText(locationName, month))
                .build();
        message.setReplyMarkup(keyboardBuilder.buildEditKeyboard(month, workDays));
        return message;
    }

    public EditMessageText buildEditMessage(Long chatId,
                                            Integer messageId,
                                            String locationName,
                                            YearMonth month,
                                            Set<LocalDate> workDays) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(buildEditText(locationName, month));
        edit.setReplyMarkup(keyboardBuilder.buildEditKeyboard(month, workDays));
        return edit;
    }

    private String buildEditText(String locationName, YearMonth month) {
        String monthLabel = month.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("uk"))
                + " " + month.getYear();
        return "Локація: " + locationName
                + "\nМісяць: " + monthLabel
                + "\nОберіть робочі дні (✅ — робочий, ❌ — вихідний).";
    }
}
