package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
@RequiredArgsConstructor
public class LocationInfoService {

    private final UserAccountRepository userAccountRepository;
    private final MainMenuService mainMenuService;
    private static final String COMMAND_MY_LOCATION = "📍 Моя локація";

    public BotResponse showMyLocation(UserAccount account, Long chatId) {
        if (account == null || account.getStatus() != RegistrationStatus.APPROVED) {
            return BotResponse.empty();
        }
        Location location = account.getLocation();
        if (location == null) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Локацію не знайдено.")
                    .replyMarkup(mainMenuService.mainMenuKeyboard(account))
                    .build();
            return BotResponse.of(message);
        }

        List<UserAccount> sellers = userAccountRepository.findByStatusAndRoleAndLocation_Id(
                RegistrationStatus.APPROVED,
                Role.SELLER,
                location.getId()
        ).stream()
                .sorted(Comparator.comparing(UserAccount::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserAccount::getCreatedAt))
                .toList();

        UserAccount firstSeller = sellers.size() > 0 ? sellers.get(0) : null;
        UserAccount secondSeller = sellers.size() > 1 ? sellers.get(1) : null;

        Optional<UserAccount> senior = userAccountRepository.findFirstByStatusAndRoleAndLocation_IdOrderByCreatedAtAsc(
                RegistrationStatus.APPROVED,
                Role.SENIOR_SELLER,
                location.getId()
        );

        String tmName = null;
        if (location.getTmUserId() != null) {
            tmName = userAccountRepository.findByTelegramUserId(location.getTmUserId())
                    .map(UserAccount::getLastName)
                    .orElse(null);
        }

        String text = """
                Моя локація
                ________________
                Перший продавець: %s
                Другий продавець: %s
                Старший продавець: %s
                ТМ: %s
                ________________
                Робочі контакти: %s
                """.formatted(
                formatName(firstSeller),
                formatName(secondSeller),
                senior.map(UserAccount::getLastName).orElse("-"),
                tmName == null ? "-" : tmName,
                "-"
        );

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.trim())
                .replyMarkup(mainMenuService.mainMenuKeyboard(account))
                .build();
        return BotResponse.of(message);
    }

    public BotResponse showMyLocation(UserAccount account, Long chatId, String text) {
        if (text == null || !COMMAND_MY_LOCATION.equalsIgnoreCase(text)) {
            return BotResponse.empty();
        }
        return showMyLocation(account, chatId);
    }

    private String formatName(UserAccount account) {
        return account == null ? "-" : account.getLastName();
    }
}
