package sprint.server.controller.datatransferobject.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import sprint.server.domain.Groups;

import java.util.Comparator;

@Data
@AllArgsConstructor
public class LoadGroupsResponseVo {
    private int groupId;
    private String groupName;
    private String groupDescription;
    private String groupPicture;
    private int groupPersonnel;
    private int groupMaxPersonnel;

    public LoadGroupsResponseVo(Groups groups) {
        this.groupId = groups.getId();
        this.groupName = groups.getGroupName();
        this.groupDescription = groups.getGroupDescription();
        this.groupPicture = groups.getGroupPicture();
        this.groupPersonnel = groups.getGroupPersonnel();
        this.groupMaxPersonnel = groups.getGroupMaxPersonnel();
    }

    public static Comparator<LoadGroupsResponseVo> COMPARE_BY_GROUPNAME = Comparator.comparing(o -> o.getGroupName());
}