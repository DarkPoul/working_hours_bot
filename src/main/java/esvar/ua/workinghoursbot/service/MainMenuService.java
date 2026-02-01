package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

@Service
@RequiredArgsConstructor
public class MainMenuService {

    private final UserAccountService userAccountService;
    private final TmScheduleEditGateService scheduleEditGateService;

    public ReplyKeyboardMarkup mainMenuKeyboard(Long telegramUserId) {
        UserAccount account = userAccountService.findByTelegramUserId(telegramUserId).orElse(null);
        if (account == null) {
            return KeyboardFactory.mainMenuKeyboard(false, false);
        }
        return mainMenuKeyboard(account);
    }

    public ReplyKeyboardMarkup mainMenuKeyboard(UserAccount account) {
        if (account == null) {
            return KeyboardFactory.mainMenuKeyboard(false, false);
        }
        if (account.getRole() == Role.TM) {
            return KeyboardFactory.tmMainMenuKeyboard();
        }
        boolean editEnabled = scheduleEditGateService.isScheduleEditEnabled(account);
        boolean showActiveRequests = account.getRole() == Role.SENIOR_SELLER;
        return KeyboardFactory.mainMenuKeyboard(editEnabled, showActiveRequests);
    }
}
