package esvar.ua.workinghoursbot.service;

import static org.junit.jupiter.api.Assertions.*;

import esvar.ua.workinghoursbot.domain.*;
import org.junit.jupiter.api.Test;

class ScheduleAccessServiceTest {
    @Test
    void sellerCannotEditWithoutApprovalOrLocationAndTmNeverEdits() {
        ScheduleAccessService service = new ScheduleAccessService();
        UserAccount seller = new UserAccount();
        seller.setRole(Role.SELLER);
        seller.setSellerStatus(SellerStatus.PENDING);
        assertFalse(service.canSellerEdit(seller));

        seller.setSellerStatus(SellerStatus.APPROVED);
        assertFalse(service.canSellerEdit(seller));

        seller.setLocation(new Location());
        assertTrue(service.canSellerEdit(seller));

        UserAccount tm = new UserAccount();
        tm.setRole(Role.TM);
        assertFalse(service.canTmEdit(tm));
    }
}
