package job.search.kg.service.user;

import job.search.kg.dto.request.user.UpsertWorkerOfferRequest;
import job.search.kg.dto.response.WorkerOfferResponse;
import job.search.kg.entity.City;
import job.search.kg.entity.User;
import job.search.kg.entity.WorkerOffer;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.CityRepository;
import job.search.kg.repo.UserRepository;
import job.search.kg.repo.WorkerOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BotWorkerOfferService {

    private final WorkerOfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final ContactMasking contactMasking;

    /** Создать или обновить «Мои услуги» (одна карточка на пользователя) */
    @Transactional
    public WorkerOfferResponse upsert(Long telegramId, UpsertWorkerOfferRequest req) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        City city = cityRepository.findById(req.getCityId() != null ? req.getCityId() : 1)
                .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));

        WorkerOffer offer = offerRepository.findByUser(user).orElseGet(WorkerOffer::new);
        offer.setUser(user);
        offer.setCity(city);
        offer.setSkills(String.join(WorkerOffer.SKILL_SEPARATOR, req.getSkills().stream()
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList()));
        offer.setPrice(req.getPrice());
        offer.setPriceUnit(req.getPriceUnit() != null ? req.getPriceUnit() : WorkerOffer.PriceUnit.HOUR);
        offer.setDays(String.join(",", req.getDays()));
        offer.setDistrict(req.getDistrict());
        offer.setAbout(req.getAbout());
        offer.setPhone(user.getPhone());
        offer.setIsActive(req.getIsActive() == null || req.getIsActive());
        return toResponse(offerRepository.save(offer), telegramId);
    }

    @Transactional(readOnly = true)
    public Optional<WorkerOfferResponse> mine(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        return offerRepository.findByUser(user).map(o -> toResponse(o, telegramId));
    }

    @Transactional(readOnly = true)
    public WorkerOfferResponse get(Long id, Long viewerTelegramId) {
        return offerRepository.findByIdWithUser(id).map(o -> toResponse(o, viewerTelegramId))
                .orElseThrow(() -> new ResourceNotFoundException("Карточка не найдена"));
    }

    @Transactional(readOnly = true)
    public List<WorkerOfferResponse> list(Long viewerTelegramId, Integer cityId) {
        return offerRepository.findActive(cityId).stream().map(o -> toResponse(o, viewerTelegramId)).toList();
    }

    @Transactional
    public WorkerOfferResponse setActive(Long telegramId, boolean active) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        WorkerOffer offer = offerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Карточка не найдена"));
        offer.setIsActive(active);
        return toResponse(offerRepository.save(offer), telegramId);
    }

    public static List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) return List.of();
        return Arrays.stream(skills.split(Pattern.quote(WorkerOffer.SKILL_SEPARATOR)))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private WorkerOfferResponse toResponse(WorkerOffer o, Long viewerTelegramId) {
        WorkerOfferResponse r = new WorkerOfferResponse();
        r.setId(o.getId());
        r.setSkills(splitSkills(o.getSkills()));
        r.setPrice(o.getPrice());
        r.setPriceUnit(o.getPriceUnit());
        r.setDays(o.getDays() == null || o.getDays().isBlank() ? List.of() : List.of(o.getDays().split(",")));
        r.setDistrict(o.getDistrict());
        r.setAbout(o.getAbout());
        r.setCityId(o.getCity().getId());
        r.setCityName(o.getCity().getNameRu());
        User u = o.getUser();
        r.setName(ContactMasking.shortName(u.getFirstName(), u.getLastName()));
        boolean open = contactMasking.canSeeContacts(viewerTelegramId, u.getTelegramId());
        r.setPhone(open ? o.getPhone() : ContactMasking.mask(o.getPhone()));
        r.setTelegramUsername(open ? u.getUsername() : null);
        r.setIsActive(o.getIsActive());
        r.setMine(u.getTelegramId().equals(viewerTelegramId));
        r.setCreatedAt(o.getCreatedAt());
        return r;
    }
}
