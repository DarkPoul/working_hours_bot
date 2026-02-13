package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.config.DomainProperties;
import esvar.ua.workinghoursbot.domain.*;
import esvar.ua.workinghoursbot.repository.JoinRequestRepository;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerOnboardingService {

    private final JoinRequestRepository joinRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final LocationRepository locationRepository;
    private final DomainProperties domainProperties;

    @Transactional
    public JoinRequest createJoinRequest(UserAccount seller, UUID locationId) {
        if (joinRequestRepository.existsBySeller_IdAndStatus(seller.getId(), JoinRequestStatus.PENDING)) {
            throw new IllegalStateException("Seller already has pending request");
        }
        Location location = locationRepository.findById(locationId).orElseThrow();
        if (location.getTmUserId() == null) {
            throw new IllegalStateException("Location has no TM assigned");
        }
        long approved = userAccountRepository.countByRoleAndSellerStatusAndLocation_Id(
                Role.SELLER,
                SellerStatus.APPROVED,
                locationId
        );
        if (approved >= domainProperties.schedule().maxSellersPerLocation()) {
            throw new IllegalStateException("Location capacity reached");
        }
        UserAccount tm = userAccountRepository.findByTelegramUserId(location.getTmUserId()).orElseThrow();

        seller.setSellerStatus(SellerStatus.PENDING);
        seller.setPendingLocation(location);
        seller.setState(UserState.SELLER_PENDING_MENU);
        userAccountRepository.save(seller);

        JoinRequest request = new JoinRequest();
        request.setSeller(seller);
        request.setLocation(location);
        request.setTm(tm);
        request.setStatus(JoinRequestStatus.PENDING);
        return joinRequestRepository.save(request);
    }

    @Transactional
    public JoinRequest approve(UUID requestId, UUID tmId) {
        JoinRequest request = joinRequestRepository.findById(requestId).orElseThrow();
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            return request;
        }
        if (!request.getTm().getId().equals(tmId)) {
            throw new IllegalStateException("TM mismatch");
        }
        long approved = userAccountRepository.countByRoleAndSellerStatusAndLocation_Id(
                Role.SELLER,
                SellerStatus.APPROVED,
                request.getLocation().getId()
        );
        if (approved >= domainProperties.schedule().maxSellersPerLocation()) {
            throw new IllegalStateException("Location capacity reached");
        }
        UserAccount seller = request.getSeller();
        seller.setSellerStatus(SellerStatus.APPROVED);
        seller.setLocation(request.getLocation());
        seller.setPendingLocation(null);
        seller.setActive(true);
        seller.setState(UserState.SELLER_MAIN_MENU);
        userAccountRepository.save(seller);

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setResolvedAt(Instant.now());
        return joinRequestRepository.save(request);
    }

    @Transactional
    public JoinRequest reject(UUID requestId, UUID tmId) {
        JoinRequest request = joinRequestRepository.findById(requestId).orElseThrow();
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            return request;
        }
        if (!request.getTm().getId().equals(tmId)) {
            throw new IllegalStateException("TM mismatch");
        }
        UserAccount seller = request.getSeller();
        seller.setSellerStatus(SellerStatus.REJECTED);
        seller.setPendingLocation(null);
        seller.setState(UserState.REGISTRATION_SELLER_LOCATION);
        userAccountRepository.save(seller);

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setResolvedAt(Instant.now());
        return joinRequestRepository.save(request);
    }
}
