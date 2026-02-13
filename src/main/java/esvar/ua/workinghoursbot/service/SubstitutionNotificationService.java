package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.InlineKeyboardFactory;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.service.CallbackIdEncoder;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubstitutionNotificationService {

    private static final String CB_SENIOR_TAKE = "SENIOR_SUB_TAKE:";
    private static final String CB_SENIOR_FIND = "SENIOR_SUB_FIND:";
    private static final String CB_SENIOR_REJECT = "SENIOR_SUB_REJECT:";
    private static final String CB_SENIOR_ACTIVE_LIST = "SENIOR_SUB_ACTIVE_LIST";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final SubstitutionService substitutionService;
    private final TelegramNotificationService notificationService;

    public List<BotApiMethod<?>> notifySeniorAboutRequest(SubstitutionRequest request) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        UserAccount tm = substitutionService.findTmForRequest(request.getLocation()).orElse(null);
        if (tm != null) {
            SendMessage message = notificationService.sendMessage(
                    tm.getTelegramChatId(),
                    buildSeniorRequestText(request),
                    buildSeniorInlineKeyboard(request)
            );
            actions.add(message);
        } else {
            log.warn("TM not found for substitution request. requestId={}, locationId={}",
                    request.getId(),
                    request.getLocation() != null ? request.getLocation().getId() : null);
            actions.add(notificationService.sendMessage(
                    request.getRequester().getTelegramChatId(),
                    "⚠️ Не знайдено ТМ для вашої локації. Зверніться до адміністратора.",
                    null
            ));
        }
        return actions;
    }

    public String buildSeniorRequestText(SubstitutionRequest request) {
        return """
                🔁 Запит на підміну
                👤 Продавець: %s
                📍 Локація: %s
                📅 Дата: %s
                🕒 Створено: %s
                """.formatted(
                request.getRequester().getLastName(),
                request.getLocation().getName(),
                DATE_FORMAT.format(request.getRequestDate()),
                DATE_TIME_FORMAT.format(request.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
        );
    }

    public InlineKeyboardMarkup buildSeniorInlineKeyboard(SubstitutionRequest request) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (request.getStatus() == SubstitutionRequestStatus.NEW
                || request.getStatus() == SubstitutionRequestStatus.IN_PROGRESS) {
            rows.add(List.of(
                    InlineKeyboardFactory.button("✅ Я візьму зміну", CB_SENIOR_TAKE + CallbackIdEncoder.encode(request.getId())),
                    InlineKeyboardFactory.button("👥 Знайти заміну", CB_SENIOR_FIND + CallbackIdEncoder.encode(request.getId()))
            ));
            rows.add(List.of(InlineKeyboardFactory.button("❌ Відхилити", CB_SENIOR_REJECT + CallbackIdEncoder.encode(request.getId()))));
        }
        rows.add(List.of(InlineKeyboardFactory.button("📌 Активні запити на підміну", CB_SENIOR_ACTIVE_LIST)));
        return InlineKeyboardFactory.rows(rows);
    }
}
