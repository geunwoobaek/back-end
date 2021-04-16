package com.project.devidea.modules.content.study.service;

import com.project.devidea.modules.account.Account;
import com.project.devidea.modules.account.repository.AccountRepository;
import com.project.devidea.modules.content.study.Study;
import com.project.devidea.modules.content.study.StudyMember;
import com.project.devidea.modules.content.study.StudyRole;
import com.project.devidea.modules.content.study.aop.AlreadyExistError;
import com.project.devidea.modules.content.study.apply.StudyApply;
import com.project.devidea.modules.content.study.apply.StudyApplyForm;
import com.project.devidea.modules.content.study.apply.StudyApplyListForm;
import com.project.devidea.modules.content.study.apply.StudyApplyRepository;
import com.project.devidea.modules.content.study.exception.AlreadyApplyException;
import com.project.devidea.modules.content.study.exception.StudyNullException;
import com.project.devidea.modules.content.study.form.*;
import com.project.devidea.modules.content.study.repository.StudyMemberRepository;
import com.project.devidea.modules.content.study.repository.StudyRepository;
import com.project.devidea.modules.notification.Notification;
import com.project.devidea.modules.notification.NotificationRepository;
import com.project.devidea.modules.tagzone.tag.Tag;
import com.project.devidea.modules.tagzone.tag.TagRepository;
import com.project.devidea.modules.tagzone.zone.Zone;
import com.project.devidea.modules.tagzone.zone.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {
    private final ModelMapper studyMapper;
    private final StudyRepository studyRepository;
    private final ZoneRepository zoneRepository;
    private final TagRepository tagRepository;
    private final AccountRepository accountRepository;
    private final StudyApplyRepository studyApplyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final NotificationRepository notificationRepository;

    public List<StudyListForm> searchByCondition(@Valid StudySearchForm studySearchForm) {
        List<Study> studyList = studyRepository.findByCondition(studySearchForm);
        return studyList.stream().map(study -> {
            return studyMapper.map(study, StudyListForm.class);
        }).collect(Collectors.toList());
    }


    public StudyDetailForm getDetailStudy(Long id) {
        Study study = studyRepository.findById(id).orElseThrow();
        return studyMapper.map(study, StudyDetailForm.class);
    }

    public StudyDetailForm makingStudy(Account admin, @Valid StudyMakingForm studyMakingForm) { //study만들기
        Study study = makingStudyEntity(admin, studyMakingForm);
        return ConvertStudyDetailForm(study, admin);
    }

    public Study makingStudyEntity(Account admin, @Valid StudyMakingForm studyMakingForm) { //study만들기
        Study study = convertToStudy(studyMakingForm);
        studyRepository.save(study);
        studyMemberRepository.save(generateStudyMember(study, admin, StudyRole.팀장));
        return study;
    }

    public StudyDetailForm ConvertStudyDetailForm(Study study, Account admin) { //study만들기
        StudyDetailForm studyDetailForm = studyMapper.map(study, StudyDetailForm.class);
        studyDetailForm.setMembers(new HashSet<String>(Arrays.asList(admin.getNickname())));
        return studyDetailForm;
    }
    @AlreadyExistError(message="이미 존재하는 스터디입니다.")
    public String applyStudy(Account applicant, @Valid StudyApplyForm studyApplyForm) throws AlreadyApplyException{
        Study study = studyRepository.findById(studyApplyForm.getStudyId()).orElseThrow(StudyNullException::new);
        StudyApply studypply=MakingStudyApplyEntity(study, applicant, studyApplyForm); //받는 인자가 없으면 그냥 스킵해버린다A..
        return "Yes";//완료 메시지 미완성
    }
    @AlreadyExistError(message="이미 존재하는 스터디입니다.")
    public StudyApply MakingStudyApplyEntity(Study study, Account applicant, @Valid StudyApplyForm studyApplyForm) throws AlreadyApplyException{
        StudyApply studyApply = StudyApply.builder()
                .study(study)
                .applicant(applicant)
                .answer(studyApplyForm.getAnswer())
                .etc(studyApplyForm.getEtc())
                .build();

        studyApplyRepository.saveAndFlush(studyApply);
        return studyApply;
    }

    public String decideJoin(Long id, Boolean accept) {
        StudyApply studyApply = studyApplyRepository.findById(id).orElseThrow();
        Account applicant = studyApply.getApplicant();
        Study study = studyApply.getStudy();
        if (study.getCounts() == study.getMaxCount())
            return "인원이 꽉찼습니다.";
        studyApply.setAccpted(accept);
        if (accept) {
            return addMember(applicant, study, StudyRole.회원);
        } else return rejected(study,applicant);
    }

    public String addMember(Account applicant, Study study, StudyRole role) {
        studyMemberRepository.save(
                StudyMember.
                        builder()
                        .study(study)
                        .member(applicant)
                        .role(role)
                        .build()
        );
        return "성공적으로 저장하였습니다.";
    }
    public String rejected(Study study,Account applicant){
        return "스터디 요청을 거절하였습니다.";
    }

    @Override
    public List<StudyApplyForm> getApplyForm(Long id) { //해당 스터디 가입신청 리스트 보기
        return studyApplyRepository.findById(id).stream()
                .map(studyApply -> {
                    return studyMapper.map(studyApply, StudyApplyForm.class);
                }).collect(Collectors.toList());
    }

    @Override
    public List<StudyListForm> getMyStudy(Account account) {
        return null;
    }

    @Override
    public List<StudyApplyForm> getMyApplyList(Account account) {
        return null;
    }

    public String deleteStudy(Account account,Long id) { //해당 스터디 가입신청 리스트 보기
        Study study = studyRepository.findById(id).orElseThrow();
        studyRepository.delete(study);
        return "성공적으로 삭제하였습니다.";
    }

    public String leaveStudy(Account account, Long study_id) {
        studyMemberRepository.deleteByStudy_IdAndMember_Id(study_id, account.getId());
        studyRepository.LeaveStudy(study_id);
        return "스터디를 떠났습니다.";
    }

    public List<StudyListForm> myStudy(Account account) {
        List<StudyMember> studyList = studyMemberRepository.findByMember_Id(account.getId());
        return studyList.stream().map(study -> {
            return studyMapper.map(study.getStudy(), StudyListForm.class);
        }).collect(Collectors.toList());
    }

    public List<StudyApplyListForm> getApplyList(Long id) {
        return studyApplyRepository.findByStudy_Id(id).stream().map(
                studyApply -> {
                    return studyMapper.map(studyApply, StudyApplyListForm.class);
                }
        ).collect(Collectors.toList());
    }

    public StudyApplyForm getApplyDetail(Long id) {
        StudyApply studyApply = studyApplyRepository.findById(id).get();
        return studyMapper.map(studyApply, StudyApplyForm.class);
    }

    public OpenRecruitForm getOpenRecruitForm(Long id) {
        Study study = studyRepository.findById(id).orElseThrow();
        return new OpenRecruitForm(study.isOpen(), study.isRecruiting());
    }

    public TagZoneForm getTagandZone(Long id) {
        Study study = studyRepository.findById(id).orElseThrow();
        return new TagZoneForm(study.getTags(), study.getLocation());
    }

    public String UpdateOpenRecruiting(Long id, OpenRecruitForm openRecruitForm) {
        Study study = studyRepository.findById(id).orElseThrow();
        study.setOpenAndRecruiting(openRecruitForm.isOpen(), openRecruitForm.isRecruiting());
        return "success";
    }

    public String UpdateTagAndZone(Long id, TagZoneForm tagZoneForm) {
        Study study = studyRepository.findById(id).orElseThrow();
        return "success";
    }

    public StudyApplyForm makeStudyForm(Long id) {
        Study study = studyRepository.findById(id).orElseThrow();
        StudyApplyForm studyApplyForm = new StudyApplyForm()
                .builder()
                .studyId(study.getId())
                .study(study.getTitle())
                .answer(study.getQuestion())
                .applicant("")
                .build();
        return studyApplyForm;
    }


    public Study convertToStudy(StudyMakingForm studyMakingForm) {
        Study study = studyMakingForm.toStudy();
        String[] locations = studyMakingForm.getLocation().split("/");
        Zone zone = zoneRepository.findByCityAndProvince(locations[0], locations[1]);
        Set<Tag> tagsSet = studyMakingForm.getTags().stream().map(tag -> {
            return tagRepository.findByFirstName(tag);
        }).collect(Collectors.toSet());
        study.setLocation(zone);
        study.setTags(tagsSet);
        study.setCounts(study.getCounts() + 1);
        return study;
    }

    public StudyMember generateStudyMember(Study study, Account account, StudyRole role) {
        return StudyMember.builder()
                .study(study)
                .member(account)
                .JoinDate(LocalDateTime.now())
                .role(role)
                .build();
    }

    public StudyApply generateStudyApply(Study study, Account account) {
        return new StudyApply().builder()
                .study(study)
                .applicant(account)
                .build();

    }

    public String setEmpower(Long study_id, EmpowerForm empowerForm) {
        studyMemberRepository.updateRole(study_id, accountRepository.findByNickname(empowerForm.getNickName()).getId(), empowerForm.getRole());
        return "성공적으로 권한을 부여했습니다.";
    }

    public List<StudyApplyForm> myApplyList(Account account) {
        List<StudyApply> studyApplies = studyApplyRepository.findByApplicant(account);
        return studyApplies.stream().map(studyApply -> {
                    return studyMapper.map(studyApply, StudyApplyForm.class);
                }
        ).collect(Collectors.toList());
    }
}