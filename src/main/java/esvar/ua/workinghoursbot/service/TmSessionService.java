package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.TmSession;
import esvar.ua.workinghoursbot.repository.TmSessionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TmSessionService {

    private final TmSessionRepository tmSessionRepository;

    public Optional<TmSession> findByTelegramUserId(Long telegramUserId) {
        return tmSessionRepository.findByTelegramUserId(telegramUserId);
    }

    @Transactional
    public TmSession save(TmSession session) {
        return tmSessionRepository.save(session);
    }

    @Transactional
    public void deleteByTelegramUserId(Long telegramUserId) {
        tmSessionRepository.deleteByTelegramUserId(telegramUserId);
    }
}
