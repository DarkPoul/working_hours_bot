package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;

    public Optional<UserAccount> findByTelegramUserId(Long telegramUserId) {
        return userAccountRepository.findByTelegramUserId(telegramUserId);
    }

    @Transactional
    public UserAccount save(UserAccount userAccount) {
        return userAccountRepository.save(userAccount);
    }

    @Transactional
    public void deleteByTelegramUserId(Long telegramUserId) {
        userAccountRepository.deleteByTelegramUserId(telegramUserId);
    }
}
