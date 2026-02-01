package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
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

    @Transactional(readOnly = true)
    public String findApproverName(Location location) {
        if (location == null) {
            return null;
        }
        Long tmUserId = location.getTmUserId();
        if (tmUserId != null) {
            Optional<UserAccount> tm = userAccountRepository.findByTelegramUserId(tmUserId);
            if (tm.isPresent()) {
                return "ТМ: " + tm.get().getLastName();
            }
        }
        return userAccountRepository.findFirstByStatusAndRoleAndLocation_IdOrderByCreatedAtAsc(
                        RegistrationStatus.APPROVED,
                        Role.SENIOR_SELLER,
                        location.getId()
                )
                .map(user -> "Старший продавець: " + user.getLastName())
                .orElse(null);
    }
}
