package sprint.server.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import sprint.server.controller.datatransferobject.request.*;
import sprint.server.controller.datatransferobject.response.*;
import sprint.server.controller.exception.ApiException;
import sprint.server.controller.exception.ExceptionEnum;
import sprint.server.domain.Groups;
import sprint.server.domain.groupmember.GroupMember;
import sprint.server.domain.groupmember.GroupMemberState;
import sprint.server.domain.member.Member;
import sprint.server.service.GroupService;
import sprint.server.service.MemberService;
import sprint.server.service.StatisticsService;

import javax.validation.Valid;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-management/group")
public class GroupsApiController {
    private final MemberService memberService;
    private final GroupService groupService;
    private final StatisticsService statisticsService;

    @ApiOperation(value="그룹 만들기", notes ="groupName: NotNull\ngroupLeaderId: NotNull\ngroupDescription, groupPicture: Nullable")
    @PostMapping("")
    public CreateGroupsResponse createGroup(@RequestBody @Valid CreateGroupRequest request) {
        Groups groups = new Groups(request.getGroupName(), request.getGroupLeaderId(), request.getGroupDescription(), request.getGroupPicture());
        Integer groupId = groupService.join(groups);
        return new CreateGroupsResponse(groupId);

    }

    @ApiOperation(value="내가 가입한 그룹 목록 검색", notes = "그룹장인 그룹이 더 우선으로 존재")
    @GetMapping("/list/{userId}")
    public GroupListResponse<MyGroupInfoVo> findGroupsByUserId(@PathVariable Long userId) {
        Member member = memberService.findById(userId);
        List<MyGroupInfoVo> result = groupService.findJoinedGroupMemberByMember(member)
                .stream()
                .map(groupMember -> new MyGroupInfoVo(
                        groupService.findGroupByGroupId(groupMember.getGroupId()), groupMember.getGroupMemberState()))
                .sorted(MyGroupInfoVo.COMPARE_BY_ISLEADER)
                .collect(Collectors.toList());
        return new GroupListResponse(result.size(), result);
    }

    @ApiOperation(value="전체 그룹 목록 검색", notes="그룹 이름 기준, LIKE연산\nstate는 \"LEADER\", \"MEMBER\", \"NOT_MEMBER\", \"REQUEST\" 중 하나로 응답.")
    @GetMapping("/list")
    public GroupListResponse<GroupInfoVo> findGroupsByGroupName(@RequestParam Long userId, @RequestParam String target) {
        Member member = memberService.findById(userId);
        List<Groups> groupList = groupService.findGroupByString(target);
        List<GroupMember> groupMemberList = groupService.findJoinedGroupMemberByMember(member);
        List<Integer> leaderGroupId = groupMemberList.stream()
                .filter(groupMember -> groupMember.getGroupMemberState().equals(GroupMemberState.LEADER))
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());
        List<Integer> memberGroupList = groupMemberList.stream()
                .filter(groupMember -> groupMember.getGroupMemberState().equals(GroupMemberState.ACCEPT))
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());
        List<Integer> requestGroupList = groupService.findRequestGroupMemberByMember(member)
                .stream().map(Groups::getId).collect(Collectors.toList());
        List<GroupInfoVo> result = groupList.stream()
                .map(g -> new GroupInfoVo(g, memberGroupList, requestGroupList, leaderGroupId))
                .sorted(GroupInfoVo.COMPARE_BY_GROUPNAME)
                .collect(Collectors.toList());
        return new GroupListResponse(result.size(), result);
    }

    @ApiOperation(value="그룹 가입 요청", notes = "groupId : NotNull\nuserId : NotNull")
    @PostMapping("/group-member")
    public BooleanResponse createGroupMember(@RequestBody @Valid GroupIdMemberIdRequest request){
        Groups group = groupService.findGroupByGroupId(request.getGroupId());
        Member member = memberService.findById(request.getUserId());
        return new BooleanResponse(groupService.requestJoinGroupMember(group, member));
    }

    @ApiOperation(value="그룹 가입 승인/거절/취소", notes="groupMemberState는 \"ACCEPT\", \"REJECT\", \"CANCEL\" 중 하나\n" +
            "* LEAVE: 그룹장은 탈퇴할 수 없음.")
    @PutMapping("/group-member")
    public BooleanResponse modifyGroupMember(@RequestBody @Valid ModifyGroupMemberRequest request) {
        Groups group = groupService.findGroupByGroupId(request.getGroupId());
        Member member = memberService.findById(request.getUserId());

        switch (request.getGroupMemberState()){
            case ACCEPT:
            case REJECT:
            case CANCEL:
                return new BooleanResponse(groupService.answerGroupMember(group, member, request.getGroupMemberState()));
            default:
                throw new ApiException(ExceptionEnum.GROUP_METHOD_NOT_FOUND);
        }
    }

    @ApiOperation(value="그룹 탈퇴")
    @DeleteMapping("/group-member")
    public BooleanResponse deleteGroupMember(@RequestBody @Valid GroupIdMemberIdRequest request) {
        Groups group = groupService.findGroupByGroupId(request.getGroupId());
        Member member = memberService.findById(request.getUserId());

        return new BooleanResponse(groupService.leaveGroupMember(group, member));
    }

    @ApiOperation(value="그룹 정보", notes="요청한 그룹의 정보를 출력합니다.\n" +
            "그룹정보와 그룹원들의 이번주의 기록을 반환.")
    @GetMapping("/{groupId}")
    public GroupInfoResponse getGroupInfo(@PathVariable Integer groupId) {
        Groups group = groupService.findGroupByGroupId(groupId);
        List<Member> memberList = groupService.findAllMemberByGroup(group);
        List<GroupUserDataVo> groupWeeklyUserDataVoList = memberList.stream()
                .map(m -> new GroupUserDataVo(m, statisticsService.findWeeklyStatistics(m.getId(), Calendar.getInstance())))
                .collect(Collectors.toList());
        GroupWeeklyUserDataDto groupWeeklyUserDataDto = new GroupWeeklyUserDataDto(groupWeeklyUserDataVoList.size(), groupWeeklyUserDataVoList);
        double totalTime = groupWeeklyUserDataVoList.stream().mapToDouble(GroupUserDataVo::getTotalSeconds).sum();
        double totalDistance = groupWeeklyUserDataVoList.stream().mapToDouble(GroupUserDataVo::getDistance).sum();
        GroupWeeklyStatVo groupWeeklyStatVo = new GroupWeeklyStatVo(totalTime, totalDistance);
        return new GroupInfoResponse(group, groupWeeklyStatVo, groupWeeklyUserDataDto);
    }

    @ApiOperation(value = "모든 그룹원 요청", notes="요청한 그룹의 모든 그룹원들의 정보를 출력합니다.\n" +
            "아이디/이름/티어/사진\n" +
            "+ 당일 통계 량(거리, 시간, 칼로리)")
    @GetMapping("/group-member/{groupId}")
    public FindMembersResponseDto getGroupMember(@PathVariable Integer groupId) {
        Groups group = groupService.findGroupByGroupId(groupId);
        List<Member> memberList = groupService.findAllMemberByGroup(group);
        List<GroupUserDataVo> groupUserDataVoList = memberList.stream()
                .map(m -> new GroupUserDataVo(m, statisticsService.findDailyStatistics(m.getId(), Calendar.getInstance())))
                .collect(Collectors.toList());
        return new FindMembersResponseDto(groupUserDataVoList.size(), groupUserDataVoList);
    }

    @ApiOperation(value = "그룹장 위임", notes = "그룹장을 다른 그룹원에게 위임합니다.")
    @PutMapping("group-member/leader")
    public BooleanResponse modifyGroupLeader(@RequestBody @Valid ModifyGroupLeaderRequest request) {
        Groups group = groupService.findGroupByGroupId(request.getGroupId());
        Member member = memberService.findById(request.getNewGroupLeaderUserId());
        return new BooleanResponse(groupService.changeGroupLeaderByGroupAndMember(group, member));
    }

    @ApiOperation(value = "그룹 삭제", notes = "그룹을 삭제합니다. 그룹 삭제는 그룹장만이 할 수 있다.")
    @DeleteMapping("{groupId}")
    public BooleanResponse deleteGroup(@PathVariable Integer groupId, @RequestParam Long userId) {
        Member member =memberService.findById(userId);
        Groups group = groupService.findGroupByGroupId(groupId);
        Member leader = groupService.findGroupLeader(group);
        if (!member.equals(leader)){
            throw new ApiException(ExceptionEnum.GROUP_NOT_LEADER);
        }
        return new BooleanResponse(groupService.deleteGroup(group));
    }

    @ApiOperation(value = "그룹 정보 변경", notes = "그룹의 정보를 변경합니다.")
    @PutMapping("{groupId}")
    public BooleanResponse modifyGroupInfo(@PathVariable Integer groupId, @RequestBody @Valid ModifyGroupInfoRequest request) {
        Groups groups = groupService.findGroupByGroupId(groupId);
        return new BooleanResponse(groupService.modifyGroupInfo(groups, request));
    }
}
