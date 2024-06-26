package server.api.kiwes.domain.club.service;

import com.amazonaws.services.kms.model.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import server.api.kiwes.domain.category.entity.Category;
import server.api.kiwes.domain.category.repository.CategoryRepository;
import server.api.kiwes.domain.category.type.CategoryType;
import server.api.kiwes.domain.club.constant.ClubResponseType;
import server.api.kiwes.domain.club.dto.ClubArticleRequestDto;
import server.api.kiwes.domain.club.dto.ClubCreatedResponseDto;
import server.api.kiwes.domain.club.dto.ClubJoinedResponseDto;
import server.api.kiwes.domain.club.dto.ClubPopularEachResponseDto;
import server.api.kiwes.domain.club.entity.Club;
import server.api.kiwes.domain.club.repository.ClubRepository;
import server.api.kiwes.domain.club_category.entity.ClubCategory;
import server.api.kiwes.domain.club_category.repository.ClubCategoryRepository;
import server.api.kiwes.domain.club_language.entity.ClubLanguage;
import server.api.kiwes.domain.club_language.repository.ClubLanguageRepository;
import server.api.kiwes.domain.club_member.entity.ClubMember;
import server.api.kiwes.domain.club_member.repository.ClubMemberRepository;
import server.api.kiwes.domain.heart.constant.HeartStatus;
import server.api.kiwes.domain.heart.entity.Heart;
import server.api.kiwes.domain.heart.repository.HeartRepository;
import server.api.kiwes.domain.language.entity.Language;
import server.api.kiwes.domain.language.language.LanguageRepository;
import server.api.kiwes.domain.language.type.LanguageType;
import server.api.kiwes.domain.member.entity.Member;
import server.api.kiwes.domain.member.repository.MemberRepository;
import server.api.kiwes.domain.member_language.repository.MemberLanguageRepository;
import server.api.kiwes.global.entity.Gender;
import server.api.kiwes.response.BizException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ClubService {
    private final ClubRepository clubRepository;
    private final LanguageRepository languageRepository;
    private final CategoryRepository categoryRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubLanguageRepository clubLanguageRepository;
    private final ClubCategoryRepository clubCategoryRepository;
    private final HeartRepository heartRepository;
    private final MemberLanguageRepository memberLanguageRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;


    /**
     * club id를 통해 club 정보 불러오기
     */
    public Club findById(Long id){
        return clubRepository.findById(id)
                .orElseThrow(() -> new BizException(ClubResponseType.CLUB_NOT_EXIST));
    }

    /**
     * club 모집 글 등록
     */
    public ClubCreatedResponseDto saveNewClub(ClubArticleRequestDto requestDto, Member member) {
        Gender gender = Gender.valueOf(requestDto.getGender());

        Club club = Club.builder()
                .date(requestDto.getDate())
                .dueTo(requestDto.getDueTo())
                .cost(requestDto.getCost())
                .maxPeople(requestDto.getMaxPeople())
                .gender(gender)
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .locationKeyword(requestDto.getLocationKeyword())
                .location(requestDto.getLocation())
                .latitude(requestDto.getLatitude())
                .longitude(requestDto.getLongitude())
                .thumbnailUrl("club_2")
                .build();
        clubRepository.save(club);
        club.setLanguages(getClubLanguageEntities(requestDto.getLanguages(), club));
        club.setMembers(getClubMemberEntities(member, club));
        club.setCategory(getClubCategoryEntities(requestDto.getCategory(), club));

        return ClubCreatedResponseDto.builder()
                .clubId(club.getId())
                .clubTitle(club.getTitle())
                .hostId(member.getId())
                .hostNickname(member.getNickname())
                .clubMaxPeople(club.getMaxPeople())
                .build();
    }
    public ClubCreatedResponseDto updateClub(ClubArticleRequestDto requestDto, long clubId, Member member) {
        Gender gender = Gender.valueOf(requestDto.getGender());

        Club club = clubRepository.findById(clubId).orElseThrow(() -> new NotFoundException("Club not found"));
        club.setDate(requestDto.getDate());
        club.setDueTo(requestDto.getDueTo());
        club.setCost(requestDto.getCost());
        club.setMaxPeople(requestDto.getMaxPeople());
        club.setGender(gender);
        club.setTitle(requestDto.getTitle());
        club.setContent(requestDto.getContent());
        club.setLocationKeyword(requestDto.getLocationKeyword());
        club.setLocation(requestDto.getLocation());
        club.setLatitude(requestDto.getLatitude());
        club.setLongitude(requestDto.getLongitude());
        clubLanguageRepository.deleteAllByClubId(club.getId());
        club.getLanguages().clear();
        club.getLanguages().addAll(getClubLanguageEntities(requestDto.getLanguages(), club)); // 새 엔티티를 추가합니다.
//        club.setLanguages(getClubLanguageEntities(requestDto.getLanguages(), club));
        updateClubCategoryEntities(requestDto.getCategory(), club);

        return ClubCreatedResponseDto.builder()
                .clubId(club.getId())
                .clubTitle(club.getTitle())
                .hostId(member.getId())
                .hostNickname(member.getNickname())
                .clubMaxPeople(club.getMaxPeople())
                .build();
    }
    /**
     * 모임 글 삭제
     */
    public void deleteClub(Club club) {
        clubRepository.delete(club);
    }
    /**
     * 요청으로부터 넘어온 언어코드를 토대로 ClubLanguage 리스트를 만들어 반환
     */
    private List<ClubLanguage> getClubLanguageEntities(List<String> languageStrings, Club club){
        List<ClubLanguage> clubLanguages = new ArrayList<>();
        for(String languageString : languageStrings){
            LanguageType type = LanguageType.valueOf(languageString);
            Language language = languageRepository.findByName(type);
            ClubLanguage clubLanguage = ClubLanguage.builder()
                    .club(club)
                    .language(language)
                    .build();
            clubLanguageRepository.save(clubLanguage);
            clubLanguages.add(clubLanguage);
        }
        return clubLanguages;
    }

    /**
     * 요청으로부터 넘어온 카테고리코드를 토대로 ClubCategory 리스트를 만들어 반환
     */
    private ClubCategory getClubCategoryEntities(String categoryString, Club club){
            CategoryType type = CategoryType.valueOf(categoryString);
            Category category = categoryRepository.findByName(type);
            ClubCategory clubCategory = ClubCategory.builder()
                    .club(club)
                    .category(category)
                    .build();
            clubCategoryRepository.save(clubCategory);
        return clubCategory;
    }

    private ClubCategory updateClubCategoryEntities(String categoryString, Club club){
        CategoryType type = CategoryType.valueOf(categoryString);
        Category category = categoryRepository.findByName(type);
        ClubCategory clubCategory = clubCategoryRepository.findByClubId(club.getId());
        clubCategory.setCategory(category);
        clubCategoryRepository.save(clubCategory);
        return clubCategory;
    }

    /**
     * Club을 처음 생성할 때, 현재 멤버는 호스트 한명 뿐이므로, 호스트 한명만 담는 ClubMember 리스트를 반환
     */
    private List<ClubMember> getClubMemberEntities(Member member, Club club){
        ClubMember clubMember = ClubMember.builder()
                .club(club)
                .member(member)
                .isHost(true)
                .isApproved(true)
                .build();

        clubMemberRepository.save(clubMember);

        return List.of(clubMember);
    }

    /**
     * 모임 참여 신청
     * ClubMember 객체는 무조건 없음. 컨트롤러에서 존재하는지 여부 체크하고 넘어옴
     */
    public void applyClub(Member member, Club club) {
        clubMemberRepository.save( ClubMember.builder()
                .member(member)
                .club(club)
                .build());
    }

    /**
     * 모임 신청자 승인
     */
    public ClubJoinedResponseDto approveMember(ClubMember clubMember, Club club) {
        if(club.getCurrentPeople() >= club.getMaxPeople()){
            throw new BizException(ClubResponseType.OVER_THE_LIMIT);
        }

        clubMember.setIsApproved(true);
        club.addCurrentPeople();

        return ClubJoinedResponseDto.builder()
                .clubId(club.getId())
                .clubTitle(club.getTitle())
                .participantId(clubMember.getMember().getId())
                .participantNickname(clubMember.getMember().getNickname())
                .build();
    }

    /**
     * 신청자 거절(삭제)
     */
    public void denyMember(ClubMember clubMember) {
        if(clubMember.getIsApproved()){
            throw new BizException(ClubResponseType.ALREADY_APPROVED);
        }
        clubMemberRepository.delete(clubMember);
    }

    /**
     * 승인된 모임 멤버 강퇴
     */
    public void kickMember(ClubMember clubApplicant, Club club) {
        clubMemberRepository.delete(clubApplicant);
        club.subCurrentPeople();
    }

    /**
     * 인기 모임 조회 (5개)
     */
    public List<ClubPopularEachResponseDto> getPopularClubs(Member member) {
        List<ClubPopularEachResponseDto> response = new ArrayList<>();
        List<Club> clubs = clubRepository.findAllOrderByHeartCnt();

        if(clubs.isEmpty()){
            clubs = clubRepository.findAllOrderByHeartCntOutDate();
        }
        for(Club club : clubs){
            response.add(eachPopularClub(club,member));
        }
        return response;
    }

    public List<ClubPopularEachResponseDto> getRecommandClubs(Member member) {
        List<ClubPopularEachResponseDto> response = new ArrayList<>();

        List<Long> languageIds = memberLanguageRepository.findLanguageIdsByMemberId(member.getId());
        List<Club> clubs = clubRepository.findOrderByLanguages(languageIds);
        if(clubs.isEmpty()){
            clubs = clubRepository.findOrderByLanguagesOutDate(languageIds);
        }
        for(Club club : clubs){
            response.add(eachPopularClub(club,member));
        }
        return response;
    }

    /**
     * 인기 모임 무작위 조회 (3개)
     */
    public List<ClubPopularEachResponseDto> getPopularRandomClubs(Member member) {
        List<ClubPopularEachResponseDto> response = new ArrayList<>();
        List<Club> clubs = clubRepository.findOrderByHeartCntRandomOutDate();
            // clubRepository.findOrderByHeartCntRandom();
        // if(clubs.isEmpty()){
        //     clubs = clubRepository.findOrderByHeartCntRandomOutDate();
        // }
        for(Club club : clubs){
            response.add(eachPopularClub(club,member));
        }
        return response;
    }


    /**
     * 썸네일이미지 수정
     */
    public void setClubThumbnailImageUrl(Club club){
        club.setThumbnailUrl(String.valueOf(UUID.randomUUID()));
    }
    private ClubPopularEachResponseDto eachPopularClub(Club club,Member member) {
        ClubPopularEachResponseDto each = ClubPopularEachResponseDto.of(club);
        each.setHostProfileImg("https://kiwes2-bucket.s3.ap-northeast-2.amazonaws.com/profileimg/"+
                clubMemberRepository.findByClubHost(club).get().getMember().getProfileImg()+".jpg");

        Optional<Heart> heart = heartRepository.findByClubAndMember(club, member);
        each.setIsHeart(heart.isPresent() ? heart.get().getStatus() : HeartStatus.NO);
        return each;
    }

}
