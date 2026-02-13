package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.SellerStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import org.springframework.stereotype.Service;

@Service
public class ScheduleAccessService {

    public boolean canSellerEdit(UserAccount account) {
        return account.getRole() == Role.SELLER
                && account.getSellerStatus() == SellerStatus.APPROVED
                && account.getLocation() != null
                && !account.isBlocked();
    }

    public boolean canTmEdit(UserAccount account) {
        return false;
    }
}
