package sprint.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sprint.server.controller.exception.ApiException;
import sprint.server.domain.Groups;
import sprint.server.domain.groupmember.GroupMember;
import sprint.server.domain.groupmember.GroupMemberId;
import sprint.server.domain.groupmember.GroupMemberState;
import sprint.server.repository.GroupMemberRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class GroupServiceTest {
    @Autowired GroupService groupService;
    @Autowired MemberService memberService;
    @Autowired GroupMemberRepository groupMemberRepository;

    /**
     * 그룹 만들기 테스트
     */
    @Test
    public void makeGroupTest(){
        /* 정상적인 요청 */
        Groups groups = new Groups("groups1", 2L, "Description", "picture");
        groupService.join(groups);

        /* 해당 이름의 그룹이 이미 있을때 */
        Groups groups2 = new Groups("groups1", 1L, "Description", "picture");
        ApiException thrown = assertThrows(ApiException.class, () -> groupService.join(groups2));
        assertEquals("G0001", thrown.getErrorCode());

        /* 해당 멤버가 없을 때 */
        Groups groups3 = new Groups("groups2", -1L, "Description", "picture");
        ApiException thrown2 = assertThrows(ApiException.class, () -> groupService.join(groups3));
        assertEquals("M0001", thrown2.getErrorCode());
    }

    /**
     * 그룹 가입 요청 테스트
     */
    @Test
    public void requestGroupJoinTest(){
        /* 정상적인 요청 */
        Groups groups = new Groups("groups1", 1L, "Description", "picture");
        groupService.join(groups);
        GroupMember groupMember = new GroupMember(new GroupMemberId(groups.getId(), 2L));
        Boolean result = groupService.requestJoinGroupMember(groupMember);

        assertEquals(true, result);
        assertEquals(true, groupMemberRepository.existsByGroupMemberIdAndMemberState(groupMember.getGroupMemberId(), GroupMemberState.REQUEST));

        /* 이미 해당 그룹에 가입해 있을 때 (그룹장일때) */
        GroupMember groupMember2 = new GroupMember(new GroupMemberId(groups.getId(),1L));
        ApiException thrown = assertThrows(ApiException.class, () -> groupService.requestJoinGroupMember(groupMember2));
        assertEquals("G0004", thrown.getErrorCode());

        /* 이미 해당 그룹에 가입해 있을 때 (일반 그룹원) */
        Boolean result2 = groupService.answerGroupMember(groupMember.getGroupMemberId(), true);

        assertEquals(true, result2);
        ApiException thrown2 = assertThrows(ApiException.class, () -> groupService.requestJoinGroupMember(groupMember2));
        assertEquals("G0004", thrown2.getErrorCode());
    }

    /**
     * 그룹 가입 요청 승인 테스트
     */
    @Test
    public void answerGroupMemberTest() {
        Groups groups = new Groups("groups1", 1L, "Description", "picture");
        groupService.join(groups);
        GroupMember groupMember = new GroupMember(new GroupMemberId(groups.getId(), 2L));
        GroupMember groupMember2 = new GroupMember(new GroupMemberId(groups.getId(), 3L));
        groupService.requestJoinGroupMember(groupMember);
        groupService.requestJoinGroupMember(groupMember2);

        /* ACCEPT TEST */
        /* 정상적인 요청 */
        Boolean result = groupService.answerGroupMember(groupMember.getGroupMemberId(), true);
        assertEquals(true, result);
        assertEquals(true, groupMemberRepository.existsByGroupMemberIdAndMemberState(groupMember.getGroupMemberId(), GroupMemberState.ACCEPT));

        /* REJECT TEST */
        /*정상적인 요청 */
        Boolean result2 = groupService.answerGroupMember(groupMember2.getGroupMemberId(), false);
        assertEquals(true, result2);
        assertEquals(true, groupMemberRepository.existsByGroupMemberIdAndMemberState(groupMember2.getGroupMemberId(), GroupMemberState.REJECT));

        /* 해당 그룹이 없을 때 */
        ApiException thrown = assertThrows(ApiException.class,() -> groupService.answerGroupMember(new GroupMemberId(-1, 2L), true));
        assertEquals("G0002", thrown.getErrorCode());

        /* 해당 멤버가 없을 때*/
        ApiException thrown2 = assertThrows(ApiException.class, () -> groupService.answerGroupMember(new GroupMemberId(groups.getId(), -1L), true));
        assertEquals("M0001", thrown2.getErrorCode());

        /* 수락/거절할 요청이 존재하지 않을 때 */
        ApiException thrown3 = assertThrows(ApiException.class, () -> groupService.answerGroupMember(new GroupMemberId(groups.getId(),  3L), true));
        assertEquals("G0003", thrown3.getErrorCode());
    }

    /**
     * 그룹 탈퇴 테스트
     */
    @Test
    public void leaveGroupMemberTest(){
        Groups groups = new Groups("groups1", 1L, "Description", "picture");
        groupService.join(groups);
        GroupMemberId groupMemberId = new GroupMemberId(groups.getId(), 2L);
        GroupMember groupMember = new GroupMember(groupMemberId);
        groupService.requestJoinGroupMember(groupMember);


        /* 해당 그룹 멤버가 아닐 때 */
        ApiException thrown1 = assertThrows(ApiException.class, () -> groupService.leaveGroupMember(groupMemberId));
        assertEquals("G0006", thrown1.getErrorCode());

        groupService.answerGroupMember(groupMemberId, true);
        /* 정상적인 요청 */
        Boolean result = groupService.leaveGroupMember(groupMemberId);
        assertEquals(true, result);
        assertEquals(true, groupMemberRepository.existsByGroupMemberIdAndMemberState(groupMemberId, GroupMemberState.LEAVE));

        /* 해당 그룹 리더가 요청할 때*/
        ApiException thrown3 = assertThrows(ApiException.class, () -> groupService.leaveGroupMember(new GroupMemberId(groups.getId(),  1L)));
        assertEquals("G0005", thrown3.getErrorCode());
    }
}