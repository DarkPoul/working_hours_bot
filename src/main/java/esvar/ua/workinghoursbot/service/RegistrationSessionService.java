package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.RegistrationSession;
import esvar.ua.workinghoursbot.repository.RegistrationSessionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationSessionService {

    private final RegistrationSessionRepository registrationSessionRepository;

    public Optional<RegistrationSession> findByTelegramUserId(Long telegramUserId) {
        return registrationSessionRepository.findByTelegramUserId(telegramUserId);
    }

    @Transactional
    public RegistrationSession save(RegistrationSession session) {
        return registrationSessionRepository.save(session);
    }

    @Transactional
    public void deleteByTelegramUserId(Long telegramUserId) {
        registrationSessionRepository.deleteByTelegramUserId(telegramUserId);
    }
}
